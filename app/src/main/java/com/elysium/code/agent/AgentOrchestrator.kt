package com.elysium.code.agent

import android.util.Log
import com.elysium.code.ai.InferenceConfig
import com.elysium.code.ai.LlamaEngine
import com.elysium.code.memory.MemoryEngine
import com.elysium.code.memory.TaskOutcome
import com.elysium.code.memory.KnowledgeCategory
import com.elysium.code.plugins.PersonalityEngine
import com.elysium.code.plugins.SkillsParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — AgentOrchestrator
 * ═══════════════════════════════════════════════════════════════
 *
 * Implements a ReAct (Reason + Act) loop that turns Gemma 4 E4B
 * into an autonomous coding agent. The agent:
 *
 * 1. Receives a user request
 * 2. Recalls relevant memories from past interactions
 * 3. Reasons about what tools/steps are needed
 * 4. Executes tools (shell commands, file ops, etc.)
 * 5. Observes the results
 * 6. Iterates until the task is complete
 * 7. Records what it learned for future recall
 *
 * This is the brain that makes Elysium a true AI companion.
 */
class AgentOrchestrator(
    private val engine: LlamaEngine,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val memoryEngine: MemoryEngine,
    private val personalityEngine: PersonalityEngine,
    private val skillsParser: SkillsParser,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
        private const val MAX_ITERATIONS = 30
        private const val MAX_HISTORY_TOKENS = 3000
    }

    // ═══ State ═══
    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val streamingText: SharedFlow<String> = _streamingText.asSharedFlow()

    private val _currentAction = MutableStateFlow<String?>(null)
    val currentAction: StateFlow<String?> = _currentAction.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _pendingToolCall = MutableStateFlow<ToolCall?>(null)
    val pendingToolCall: StateFlow<ToolCall?> = _pendingToolCall.asStateFlow()

    private val approvalChannel = Channel<Boolean>()
    
    private var currentJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ═══ Conversation history for context ═══
    private val conversationHistory = mutableListOf<ChatMessage>()

    // ═══════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════

    /**
     * Process a user message through the agentic pipeline
     */
    fun processUserMessage(
        text: String,
        imageData: ByteArray? = null,
        audioData: ByteArray? = null,
        inferenceConfig: InferenceConfig = InferenceConfig()
    ) {
        currentJob = scope.launch(Dispatchers.IO) {
            try {
                _agentState.value = AgentState.THINKING

                // Add user message
                val userMsg = ChatMessage(
                    role = MessageRole.USER,
                    content = text,
                    hasImage = imageData != null,
                    hasAudio = audioData != null,
                    timestamp = System.currentTimeMillis()
                )
                addMessage(userMsg)

                // === STEP 1: Recall relevant memories ===
                _currentAction.value = "Searching memories..."
                val memoryContext = memoryEngine.buildMemoryContext(text)
                val prefsContext = memoryEngine.getPreferencesContext()

                // === STEP 2: Build system prompt ===
                val systemPrompt = buildSystemPrompt(memoryContext, prefsContext)

                // === STEP 3: ReAct Loop ===
                var iteration = 0
                var isComplete = false
                val stepsPerformed = mutableListOf<String>()
                val toolsUsed = mutableListOf<String>()
                val commandsRun = mutableListOf<String>()
                val filesModified = mutableListOf<String>()

                while (iteration < MAX_ITERATIONS && !isComplete && isActive) {
                    iteration++
                    _currentAction.value = "Thinking... (step $iteration)"

                    // Build the full prompt with conversation history
                    val fullPrompt = buildPrompt(systemPrompt, text, iteration)

                    // Generate response
                    _agentState.value = AgentState.GENERATING
                    val response = StringBuilder()

                    engine.tokenStream.let { flow ->
                        val collectJob = launch {
                            flow.collect { token ->
                                response.append(token)
                                _currentResponse.value = response.toString()
                                _streamingText.emit(token)
                            }
                        }

                        engine.generate(fullPrompt, inferenceConfig)
                        collectJob.cancel()
                    }

                    val responseText = response.toString().trim()
                    _currentResponse.value = ""

                    // === STEP 4: Parse response for tool calls ===
                    val toolCall = parseToolCall(responseText)

                    if (toolCall != null) {
                        // Zero Trust Validation
                        val isDestructive = toolCall.name in listOf("execute_command", "delete_file", "write_file")
                        
                        if (isDestructive) {
                            _agentState.value = AgentState.WAITING_FOR_APPROVAL
                            _currentAction.value = "Waiting for approval to run: ${toolCall.name}"
                            _pendingToolCall.value = toolCall
                            
                            val approved = approvalChannel.receive()
                            _pendingToolCall.value = null
                            
                            if (!approved) {
                                val rejectMsg = ChatMessage(
                                    role = MessageRole.TOOL,
                                    content = "User denied permission to execute ${toolCall.name}",
                                    toolName = toolCall.name,
                                    timestamp = System.currentTimeMillis()
                                )
                                addMessage(rejectMsg)
                                continue
                            }
                        }

                        // Execute tool
                        _agentState.value = AgentState.EXECUTING
                        _currentAction.value = "Executing: ${toolCall.name}..."

                        stepsPerformed.add("${toolCall.name}(${toolCall.args})")
                        toolsUsed.add(toolCall.name)

                        if (toolCall.name == "execute_command") {
                            commandsRun.add(toolCall.args.toString())
                        }
                        if (toolCall.name in listOf("write_file", "edit_file")) {
                            filesModified.add(toolCall.args.toString())
                        }

                        val result = toolExecutor.execute(toolCall)

                        // Add tool result to conversation
                        val toolMsg = ChatMessage(
                            role = MessageRole.TOOL,
                            content = "Tool: ${toolCall.name}\nResult: ${result.output}",
                            toolName = toolCall.name,
                            timestamp = System.currentTimeMillis()
                        )
                        addMessage(toolMsg)

                        // Record in memory
                        if (result.success) {
                            memoryEngine.recordKnowledge(
                                title = "Tool usage: ${toolCall.name}",
                                content = "Args: ${toolCall.args}\nResult: ${result.output.take(200)}",
                                category = KnowledgeCategory.TOOL_USAGE,
                                tags = listOf(toolCall.name),
                                confidence = 0.7f
                            )
                        }

                    } else {
                        // No tool call — this is a final response
                        val assistantMsg = ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = responseText,
                            timestamp = System.currentTimeMillis()
                        )
                        addMessage(assistantMsg)
                        isComplete = true
                    }
                }

                // === STEP 5: Record task pattern for future recall ===
                if (stepsPerformed.isNotEmpty()) {
                    memoryEngine.recordTaskPattern(
                        taskDescription = text.take(200),
                        stepsPerformed = stepsPerformed,
                        toolsUsed = toolsUsed.distinct(),
                        commandsRun = commandsRun,
                        filesModified = filesModified,
                        outcome = if (isComplete) TaskOutcome.SUCCESS else TaskOutcome.PARTIAL
                    )
                }

                // Learn user preferences from the interaction
                learnFromInteraction(text)

                _agentState.value = AgentState.IDLE
                _currentAction.value = null

            } catch (e: CancellationException) {
                _agentState.value = AgentState.IDLE
                _currentAction.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Agent error", e)
                addMessage(ChatMessage(
                    role = MessageRole.SYSTEM,
                    content = "Error: ${e.message}",
                    timestamp = System.currentTimeMillis()
                ))
                _agentState.value = AgentState.ERROR
                _currentAction.value = null

                // Record error for future reference
                memoryEngine.recordErrorSolution(
                    errorMessage = e.message ?: "Unknown error",
                    errorContext = "Agent processing: ${e.stackTraceToString().take(200)}",
                    solution = "Pending resolution",
                    explanation = "Error occurred during agentic processing"
                )
            }
        }
    }

    fun cancelProcessing() {
        currentJob?.cancel()
        engine.cancelGeneration()
        _agentState.value = AgentState.IDLE
        _currentAction.value = null
    }

    fun clearConversation() {
        conversationHistory.clear()
        _messages.value = emptyList()
    }

    /**
     * Approve or reject a pending tool execution
     */
    fun resolvePendingToolCall(approved: Boolean) {
        scope.launch {
            approvalChannel.send(approved)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PROMPT BUILDING
    // ═══════════════════════════════════════════════════════════

    private fun buildSystemPrompt(memoryContext: String, prefsContext: String): String {
        val personality = personalityEngine.getActivePersonalityPrompt()
        val skills = skillsParser.getActiveSkillsPrompt()
        val tools = toolRegistry.getToolDescriptions()

        return buildString {
            appendLine("You are Elysium, a world-class AI coding assistant running locally on Android.")
            appendLine("You are a top 1% programmer and systems engineer.")
            appendLine("You have access to a real terminal, file system, and code editor on this device.")
            appendLine()

            // Personality
            if (personality.isNotBlank()) {
                appendLine(personality)
                appendLine()
            }

            // Skills
            if (skills.isNotBlank()) {
                appendLine(skills)
                appendLine()
            }

            // Available tools
            appendLine("## Available Tools")
            appendLine("You can call tools by responding with a tool_call block:")
            appendLine("```tool_call")
            appendLine("{\"name\": \"tool_name\", \"args\": {\"arg1\": \"value1\"}}")
            appendLine("```")
            appendLine()
            appendLine("Available tools:")
            appendLine(tools)
            appendLine()

            // Memory context
            if (memoryContext.isNotBlank()) {
                appendLine(memoryContext)
            }

            // User preferences
            if (prefsContext.isNotBlank()) {
                appendLine(prefsContext)
            }

            appendLine()
            appendLine("## Rules")
            appendLine("- Execute tasks autonomously using available tools")
            appendLine("- Always verify your work by reading files or checking results") 
            appendLine("- If a tool call fails, try a different approach")
            appendLine("- When done, provide a clear summary of what you accomplished")
            appendLine("- Do NOT make up file contents — always read them first")
            appendLine("- For destructive operations, explain what you're about to do")
            appendLine("- Write production-quality code, never placeholders")
        }
    }

    private fun buildPrompt(systemPrompt: String, currentQuery: String, iteration: Int): String {
        return buildString {
            appendLine("<start_of_turn>system")
            appendLine(systemPrompt)
            appendLine("<end_of_turn>")

            // Conversation history (last N messages)
            val recentHistory = conversationHistory.takeLast(20)
            for (msg in recentHistory) {
                when (msg.role) {
                    MessageRole.USER -> {
                        appendLine("<start_of_turn>user")
                        appendLine(msg.content)
                        appendLine("<end_of_turn>")
                    }
                    MessageRole.ASSISTANT -> {
                        appendLine("<start_of_turn>model")
                        appendLine(msg.content)
                        appendLine("<end_of_turn>")
                    }
                    MessageRole.TOOL -> {
                        appendLine("<start_of_turn>user")
                        appendLine("[Tool Result] ${msg.content}")
                        appendLine("<end_of_turn>")
                    }
                    MessageRole.SYSTEM -> { /* skip */ }
                }
            }

            // Current turn
            if (iteration == 1) {
                appendLine("<start_of_turn>user")
                appendLine(currentQuery)
                appendLine("<end_of_turn>")
            }

            appendLine("<start_of_turn>model")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TOOL CALL PARSING
    // ═══════════════════════════════════════════════════════════

    private fun parseToolCall(response: String): ToolCall? {
        // Look for ```tool_call blocks
        val toolCallRegex = Regex("```tool_call\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = toolCallRegex.find(response) ?: return null

        return try {
            val jsonStr = match.groupValues[1].trim()
            val jsonObj = json.decodeFromString<JsonObject>(jsonStr)
            val name = jsonObj["name"]?.jsonPrimitive?.content ?: return null
            val args = jsonObj["args"] ?: JsonObject(emptyMap())

            ToolCall(name = name, args = args)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LEARNING
    // ═══════════════════════════════════════════════════════════

    private suspend fun learnFromInteraction(userMessage: String) {
        // Detect language preference
        val spanishWords = listOf("quiero", "necesito", "crear", "hacer", "cómo", "ayuda", "por favor")
        val isSpanish = spanishWords.any { userMessage.lowercase().contains(it) }
        if (isSpanish) {
            memoryEngine.updatePreferences {
                copy(communicationLanguage = "Spanish")
            }
        }

        // Detect programming language mentions
        val langMentions = mapOf(
            "kotlin" to "Kotlin", "python" to "Python", "javascript" to "JavaScript",
            "typescript" to "TypeScript", "java" to "Java", "rust" to "Rust",
            "go " to "Go", "swift" to "Swift", "c++" to "C++", "react" to "React"
        )
        val detectedLangs = langMentions.filter { userMessage.lowercase().contains(it.key) }.values
        if (detectedLangs.isNotEmpty()) {
            memoryEngine.updatePreferences {
                copy(preferredLanguages = (preferredLanguages + detectedLangs).distinct())
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        conversationHistory.add(message)
        _messages.value = conversationHistory.toList()
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════

enum class AgentState {
    IDLE,
    THINKING,
    REFLECTING,
    GENERATING,
    WAITING_FOR_APPROVAL,
    EXECUTING,
    ERROR
}

@Serializable
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val hasImage: Boolean = false,
    val hasAudio: Boolean = false,
    val toolName: String? = null,
    val timestamp: Long = 0
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM
}

data class ToolCall(
    val name: String,
    val args: Any
)

data class ToolResult(
    val output: String,
    val success: Boolean,
    val error: String? = null
)
