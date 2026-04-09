package com.elysium.code.agent

import android.util.Log
import com.elysium.code.ai.InferenceConfig
import com.elysium.code.ai.LlamaEngine
import com.elysium.code.terminal.EnhancedShellManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ═══════════════════════════════════════════════════════════════
 * ENHANCED Agent Orchestrator — Lightweight ReAct Agent
 * ═══════════════════════════════════════════════════════════════
 *
 * A streamlined orchestrator that connects directly to
 * LlamaEngine for real inference. Supports:
 * - Direct AI chat via Gemma 4 E4B
 * - Shell command execution (/shell prefix)
 * - Slash commands for terminal features
 * - Streaming token output
 *
 * This works alongside the full AgentOrchestrator — providing
 * a simpler code path for when the full ReAct loop isn't needed.
 */
class EnhancedAgentOrchestrator(
    private val scope: CoroutineScope,
    private val shellManager: EnhancedShellManager? = null,
    private val llamaEngine: LlamaEngine? = null
) {
    companion object {
        private const val TAG = "EnhancedAgent"
    }

    // ═══ State ═══
    private val _conversationMessages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationMessages: StateFlow<List<ConversationMessage>> = _conversationMessages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentResponse = MutableStateFlow("")
    val currentResponse: StateFlow<String> = _currentResponse.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _commandOutput = MutableStateFlow("")
    val commandOutput: StateFlow<String> = _commandOutput.asStateFlow()

    /**
     * Process user message — routes to AI or command handler
     */
    fun processMessage(userMessage: String): Job = scope.launch(Dispatchers.IO) {
        try {
            _isProcessing.value = true
            _lastError.value = null
            _currentResponse.value = ""

            Log.d(TAG, "Processing message: $userMessage")

            // Add user message to conversation
            addMessage(ConversationMessage(
                role = "user",
                content = userMessage,
                timestamp = System.currentTimeMillis()
            ))

            // Route: slash commands vs AI inference
            val response = if (userMessage.startsWith("/")) {
                handleCommand(userMessage)
            } else {
                generateAIResponse(userMessage)
            }

            // Add response to conversation
            addMessage(ConversationMessage(
                role = "assistant",
                content = response,
                timestamp = System.currentTimeMillis()
            ))

            _currentResponse.value = response

        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
            _lastError.value = e.message ?: "Unknown error"
            _currentResponse.value = "❌ Error: ${e.message}"
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Generate a real AI response using LlamaEngine
     */
    private suspend fun generateAIResponse(userMessage: String): String {
        val engine = llamaEngine

        // If engine is not available or not ready, explain clearly
        if (engine == null || !engine.isReady()) {
            return buildString {
                appendLine("⚠️ Gemma 4 E4B model is not loaded yet.")
                appendLine()
                appendLine("The model needs to be placed at:")
                appendLine("`/sdcard/Android/data/com.elysium.code/files/models/`")
                appendLine()
                appendLine("Push it via ADB:")
                appendLine("```")
                appendLine("adb push gemma-4-e4b-it-q4_k_m.gguf \\")
                appendLine("  /sdcard/Android/data/com.elysium.code/files/models/")
                appendLine("```")
                appendLine()
                appendLine("Once the model is loaded, I'll respond with real AI inference.")
                appendLine("In the meantime, you can use /help for available commands.")
            }
        }

        // Build conversation context for the model
        val prompt = buildPrompt(userMessage)

        // Stream inference from Gemma
        val responseBuilder = StringBuilder()
        val collectJob = scope.launch {
            engine.tokenStream.collect { token ->
                responseBuilder.append(token)
                _currentResponse.value = responseBuilder.toString()
            }
        }

        try {
            engine.generate(prompt, InferenceConfig(
                maxTokens = 2048,
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40,
                repeatPenalty = 1.1f
            ))
        } finally {
            collectJob.cancel()
        }

        return responseBuilder.toString().trim().ifEmpty {
            "⚠️ Model returned an empty response. Try rephrasing your question."
        }
    }

    /**
     * Build a Gemma-format prompt with conversation history
     */
    private fun buildPrompt(currentMessage: String): String {
        return buildString {
            appendLine("<start_of_turn>system")
            appendLine("You are Elysium, a world-class AI coding assistant running locally on Android.")
            appendLine("You are powered by Gemma 4 E4B, running 100% on-device with no internet needed.")
            appendLine("You are a top 1% programmer. Write production-quality code, never placeholders.")
            appendLine("Respond concisely and precisely. Use code blocks when appropriate.")
            appendLine("<end_of_turn>")

            // Include last N messages for context
            val recentMessages = _conversationMessages.value.takeLast(10)
            for (msg in recentMessages) {
                when (msg.role) {
                    "user" -> {
                        appendLine("<start_of_turn>user")
                        appendLine(msg.content)
                        appendLine("<end_of_turn>")
                    }
                    "assistant" -> {
                        appendLine("<start_of_turn>model")
                        appendLine(msg.content)
                        appendLine("<end_of_turn>")
                    }
                }
            }

            // Current query
            appendLine("<start_of_turn>user")
            appendLine(currentMessage)
            appendLine("<end_of_turn>")
            appendLine("<start_of_turn>model")
        }
    }

    /**
     * Handle slash commands
     */
    private suspend fun handleCommand(command: String): String {
        val trimmedCmd = command.trim()

        return when {
            trimmedCmd == "/help" -> {
                """
                📚 Available Commands:
                /help          — Show this help
                /clear         — Clear conversation
                /history       — Show message history
                /shell <cmd>   — Execute shell command
                /about         — About Elysium
                /status        — System status
                """.trimIndent()
            }

            trimmedCmd == "/clear" -> {
                _conversationMessages.value = emptyList()
                "✅ Conversation cleared"
            }

            trimmedCmd == "/history" -> {
                val history = _conversationMessages.value.joinToString("\n---\n") { msg ->
                    "${msg.role.uppercase()}: ${msg.content}"
                }
                history.ifEmpty { "No messages yet" }
            }

            trimmedCmd == "/about" -> {
                """
                ⚡ ELYSIUM CODE v1.0
                
                A local AI assistant powered by Gemma 4 E4B
                Running 100% on your device — No internet needed
                
                Features:
                • 🤖 Advanced agentic reasoning (ReAct loop)
                • 💻 Code generation & debugging
                • 🖥️ Terminal command execution
                • 📝 File operations (read, write, edit)
                • 💾 Persistent memory & learning
                • 🔌 MCP & Plugin support
                
                All data stays on your device.
                """.trimIndent()
            }

            trimmedCmd == "/status" -> {
                val modelReady = llamaEngine?.isReady() == true
                val modelState = llamaEngine?.state?.value?.name ?: "N/A"
                """
                📊 System Status:
                - Elysium: Active ✅
                - Model: Gemma 4 E4B ${if (modelReady) "✅ Loaded" else "⏳ $modelState"}
                - Inference: ${if (llamaEngine?.isGenerating() == true) "Running" else "Idle"}
                - Messages: ${_conversationMessages.value.size}
                - Shell: ${if (shellManager != null) "Available ✅" else "N/A"}
                """.trimIndent()
            }

            trimmedCmd.startsWith("/shell ") -> {
                val shellCmd = trimmedCmd.substring(7)
                executeShellCommand(shellCmd)
            }

            else -> "❓ Unknown command: $trimmedCmd\nType /help for available commands"
        }
    }

    /**
     * Execute shell command via EnhancedShellManager
     */
    private suspend fun executeShellCommand(command: String): String {
        return if (shellManager != null) {
            Log.d(TAG, "Executing shell command: $command")
            val output = shellManager.executeAndCapture(command)
            _commandOutput.value = output
            output.ifEmpty { "[Command executed with no output]" }
        } else {
            "❌ Shell manager not available"
        }
    }

    /**
     * Generate response (used by MainViewModel)
     */
    suspend fun generateResponse(text: String): String {
        return if (text.startsWith("/")) {
            handleCommand(text)
        } else {
            generateAIResponse(text)
        }
    }

    private fun addMessage(message: ConversationMessage) {
        _conversationMessages.value = _conversationMessages.value + message
        Log.d(TAG, "Message added: ${message.role} — ${message.content.take(50)}...")
    }

    fun clearConversation() {
        _conversationMessages.value = emptyList()
        _currentResponse.value = ""
        _commandOutput.value = ""
        _lastError.value = null
    }

    fun getMessages(): List<ConversationMessage> = _conversationMessages.value
    fun getLastMessage(): ConversationMessage? = _conversationMessages.value.lastOrNull()
}

/**
 * Data class for conversation messages
 */
data class ConversationMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
