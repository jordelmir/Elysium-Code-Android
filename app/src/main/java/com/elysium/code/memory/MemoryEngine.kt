package com.elysium.code.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — MemoryEngine
 * ═══════════════════════════════════════════════════════════════
 *
 * Persistent knowledge and memory system that allows the AI
 * agent to learn from past interactions, remember solutions,
 * record task patterns, and evolve into a true companion.
 *
 * The agent records:
 * - Task patterns: How specific tasks were solved
 * - Code solutions: Successful code snippets and approaches
 * - User preferences: Communication style, tools, workflows
 * - Error resolutions: How errors were diagnosed and fixed
 * - Project knowledge: Architecture, dependencies, patterns
 *
 * Memory is stored as a local JSON knowledge graph and
 * retrieved via keyword matching and recency scoring.
 */
class MemoryEngine(private val context: Context) {

    companion object {
        private const val TAG = "MemoryEngine"
        private const val MEMORY_DIR = "memory"
        private const val KNOWLEDGE_FILE = "knowledge.json"
        private const val TASKS_FILE = "task_patterns.json"
        private const val PREFS_FILE = "user_preferences.json"
        private const val ERRORS_FILE = "error_solutions.json"
        private const val PROJECTS_FILE = "project_knowledge.json"
        private const val MAX_MEMORIES_PER_CATEGORY = 500
        private const val MAX_CONTEXT_MEMORIES = 10
    }

    private val memoryDir: File
        get() = File(context.filesDir, MEMORY_DIR).also { it.mkdirs() }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ═══ In-memory caches ═══
    private val _knowledgeItems = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val knowledgeItems: StateFlow<List<KnowledgeItem>> = _knowledgeItems.asStateFlow()

    private val _taskPatterns = MutableStateFlow<List<TaskPattern>>(emptyList())
    val taskPatterns: StateFlow<List<TaskPattern>> = _taskPatterns.asStateFlow()

    private val _userPreferences = MutableStateFlow(UserPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private val _errorSolutions = MutableStateFlow<List<ErrorSolution>>(emptyList())
    val errorSolutions: StateFlow<List<ErrorSolution>> = _errorSolutions.asStateFlow()

    private val _projectKnowledge = MutableStateFlow<List<ProjectKnowledge>>(emptyList())
    val projectKnowledge: StateFlow<List<ProjectKnowledge>> = _projectKnowledge.asStateFlow()

    private val _stats = MutableStateFlow(MemoryStats())
    val stats: StateFlow<MemoryStats> = _stats.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initializing MemoryEngine...")

        val loadedKnowledge = loadList<KnowledgeItem>(KNOWLEDGE_FILE)
        if (loadedKnowledge.isEmpty()) {
            val defaults = InitialKnowledge.getDefaultKnowledge()
            _knowledgeItems.value = defaults
            saveList(KNOWLEDGE_FILE, defaults)
        } else {
            _knowledgeItems.value = loadedKnowledge
        }

        _taskPatterns.value = loadList(TASKS_FILE)
        _userPreferences.value = loadObject(PREFS_FILE) ?: UserPreferences()
        _errorSolutions.value = loadList(ERRORS_FILE)
        _projectKnowledge.value = loadList(PROJECTS_FILE)

        updateStats()
        Log.i(TAG, "MemoryEngine initialized: ${_stats.value}")
    }

    /**
     * Staff Level Maintenance: Clears all historical memory and resets to default knowledge.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        _knowledgeItems.value = InitialKnowledge.getDefaultKnowledge()
        _taskPatterns.value = emptyList()
        _userPreferences.value = UserPreferences()
        _errorSolutions.value = emptyList()
        _projectKnowledge.value = emptyList()

        saveList(KNOWLEDGE_FILE, _knowledgeItems.value)
        saveList(TASKS_FILE, _taskPatterns.value)
        saveObject(PREFS_FILE, _userPreferences.value)
        saveList(ERRORS_FILE, _errorSolutions.value)
        saveList(PROJECTS_FILE, _projectKnowledge.value)

        updateStats()
        Log.i(TAG, "Memory successfully cleared.")
    }

    // ═══════════════════════════════════════════════════════════
    // KNOWLEDGE RECORDING
    // ═══════════════════════════════════════════════════════════

    /**
     * Record a general knowledge item the agent has learned
     */
    suspend fun recordKnowledge(
        title: String,
        content: String,
        category: KnowledgeCategory,
        tags: List<String> = emptyList(),
        source: String = "",
        confidence: Float = 0.8f
    ) = withContext(Dispatchers.IO) {
        val item = KnowledgeItem(
            id = generateId(title + content),
            title = title,
            content = content,
            category = category,
            tags = tags,
            source = source,
            confidence = confidence,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = 0
        )

        val current = _knowledgeItems.value.toMutableList()
        // Update if exists, add if new
        val existingIndex = current.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            current[existingIndex] = item.copy(
                accessCount = current[existingIndex].accessCount + 1,
                confidence = maxOf(current[existingIndex].confidence, confidence)
            )
        } else {
            current.add(item)
        }

        // Trim old entries
        if (current.size > MAX_MEMORIES_PER_CATEGORY) {
            current.sortByDescending { it.lastAccessedAt }
            while (current.size > MAX_MEMORIES_PER_CATEGORY) {
                current.removeAt(current.size - 1)
            }
        }

        _knowledgeItems.value = current
        saveList(KNOWLEDGE_FILE, current)
        updateStats()

        Log.i(TAG, "Knowledge recorded: '$title' [${category.name}]")
    }

    /**
     * Record a task pattern — how a specific type of task was accomplished
     */
    suspend fun recordTaskPattern(
        taskDescription: String,
        stepsPerformed: List<String>,
        toolsUsed: List<String>,
        commandsRun: List<String> = emptyList(),
        filesModified: List<String> = emptyList(),
        outcome: TaskOutcome,
        notes: String = ""
    ) = withContext(Dispatchers.IO) {
        val pattern = TaskPattern(
            id = generateId(taskDescription),
            description = taskDescription,
            steps = stepsPerformed,
            tools = toolsUsed,
            commands = commandsRun,
            files = filesModified,
            outcome = outcome,
            notes = notes,
            createdAt = System.currentTimeMillis(),
            useCount = 1
        )

        val current = _taskPatterns.value.toMutableList()
        val existing = current.indexOfFirst {
            calculateSimilarity(it.description, taskDescription) > 0.7f
        }

        if (existing >= 0) {
            // Merge with existing pattern
            val old = current[existing]
            current[existing] = old.copy(
                steps = (old.steps + stepsPerformed).distinct(),
                tools = (old.tools + toolsUsed).distinct(),
                commands = (old.commands + commandsRun).distinct(),
                files = (old.files + filesModified).distinct(),
                useCount = old.useCount + 1,
                outcome = if (outcome == TaskOutcome.SUCCESS) outcome else old.outcome,
                notes = if (notes.isNotBlank()) notes else old.notes
            )
        } else {
            current.add(pattern)
        }

        _taskPatterns.value = current
        saveList(TASKS_FILE, current)
        updateStats()

        Log.i(TAG, "Task pattern recorded: '$taskDescription' [${outcome.name}]")
    }

    /**
     * Record how an error was resolved
     */
    suspend fun recordErrorSolution(
        errorMessage: String,
        errorContext: String,
        language: String = "",
        solution: String,
        explanation: String = "",
        preventionTip: String = ""
    ) = withContext(Dispatchers.IO) {
        val entry = ErrorSolution(
            id = generateId(errorMessage),
            errorMessage = errorMessage,
            errorContext = errorContext,
            language = language,
            solution = solution,
            explanation = explanation,
            preventionTip = preventionTip,
            createdAt = System.currentTimeMillis(),
            resolvedCount = 1
        )

        val current = _errorSolutions.value.toMutableList()
        val existing = current.indexOfFirst {
            calculateSimilarity(it.errorMessage, errorMessage) > 0.8f
        }

        if (existing >= 0) {
            current[existing] = current[existing].copy(
                resolvedCount = current[existing].resolvedCount + 1,
                solution = solution // Update with latest solution
            )
        } else {
            current.add(entry)
        }

        _errorSolutions.value = current
        saveList(ERRORS_FILE, current)
        updateStats()

        Log.i(TAG, "Error solution recorded: '${errorMessage.take(50)}...'")
    }

    /**
     * Record project-specific knowledge (architecture, patterns, etc.)
     */
    suspend fun recordProjectKnowledge(
        projectPath: String,
        aspect: String,
        knowledge: String,
        filePatterns: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val entry = ProjectKnowledge(
            id = generateId(projectPath + aspect),
            projectPath = projectPath,
            aspect = aspect,
            knowledge = knowledge,
            filePatterns = filePatterns,
            updatedAt = System.currentTimeMillis()
        )

        val current = _projectKnowledge.value.toMutableList()
        val existing = current.indexOfFirst { it.id == entry.id }

        if (existing >= 0) {
            current[existing] = entry
        } else {
            current.add(entry)
        }

        _projectKnowledge.value = current
        saveList(PROJECTS_FILE, current)

        Log.i(TAG, "Project knowledge recorded: '$aspect' for $projectPath")
    }

    /**
     * Update user preferences based on observed behavior
     */
    suspend fun updatePreferences(update: UserPreferences.() -> UserPreferences) =
        withContext(Dispatchers.IO) {
            val updated = _userPreferences.value.update()
            _userPreferences.value = updated
            saveObject(PREFS_FILE, updated)
            Log.i(TAG, "User preferences updated")
        }

    // ═══════════════════════════════════════════════════════════
    // KNOWLEDGE RETRIEVAL
    // ═══════════════════════════════════════════════════════════

    /**
     * Search memories relevant to a query.
     * Returns the most relevant memories ranked by relevance score.
     */
    fun recall(
        query: String,
        maxResults: Int = MAX_CONTEXT_MEMORIES,
        categories: Set<KnowledgeCategory>? = null
    ): List<MemoryResult> {
        val results = mutableListOf<MemoryResult>()
        val queryTerms = tokenize(query)

        // Search knowledge items
        _knowledgeItems.value
            .filter { categories == null || it.category in categories }
            .forEach { item ->
                val score = calculateRelevance(queryTerms, item)
                if (score > 0.1f) {
                    results.add(MemoryResult(
                        type = MemoryType.KNOWLEDGE,
                        title = item.title,
                        content = item.content,
                        relevance = score,
                        timestamp = item.lastAccessedAt,
                        tags = item.tags
                    ))
                }
            }

        // Search task patterns
        _taskPatterns.value.forEach { pattern ->
            val score = calculateSimilarity(
                tokenize(pattern.description),
                queryTerms
            )
            if (score > 0.15f) {
                results.add(MemoryResult(
                    type = MemoryType.TASK_PATTERN,
                    title = pattern.description,
                    content = buildString {
                        appendLine("Steps: ${pattern.steps.joinToString(" → ")}")
                        appendLine("Tools: ${pattern.tools.joinToString(", ")}")
                        if (pattern.commands.isNotEmpty()) {
                            appendLine("Commands: ${pattern.commands.joinToString("; ")}")
                        }
                        appendLine("Outcome: ${pattern.outcome}")
                        if (pattern.notes.isNotBlank()) appendLine("Notes: ${pattern.notes}")
                    },
                    relevance = score * 1.2f, // Boost task patterns
                    timestamp = pattern.createdAt,
                    tags = pattern.tools
                ))
            }
        }

        // Search error solutions
        _errorSolutions.value.forEach { error ->
            val score = calculateSimilarity(
                tokenize(error.errorMessage + " " + error.errorContext),
                queryTerms
            )
            if (score > 0.2f) {
                results.add(MemoryResult(
                    type = MemoryType.ERROR_SOLUTION,
                    title = error.errorMessage.take(100),
                    content = buildString {
                        appendLine("Solution: ${error.solution}")
                        if (error.explanation.isNotBlank()) appendLine("Why: ${error.explanation}")
                        if (error.preventionTip.isNotBlank()) appendLine("Prevention: ${error.preventionTip}")
                    },
                    relevance = score * 1.3f, // Boost error solutions
                    timestamp = error.createdAt,
                    tags = listOfNotNull(error.language.takeIf { it.isNotBlank() })
                ))
            }
        }

        return results
            .sortedByDescending { it.relevance }
            .take(maxResults)
    }

    /**
     * Build a context string from relevant memories to inject into the system prompt
     */
    fun buildMemoryContext(query: String): String {
        val memories = recall(query)
        if (memories.isEmpty()) return ""

        return buildString {
            appendLine("\n<memory_context>")
            appendLine("The following are relevant memories from past interactions:")
            appendLine()
            memories.forEachIndexed { i, mem ->
                appendLine("[${ i + 1 }] [${mem.type.label}] ${mem.title}")
                appendLine(mem.content.trim().prependIndent("    "))
                appendLine()
            }
            appendLine("</memory_context>")
        }
    }

    /**
     * Get the user's known preferences as a prompt string
     */
    fun getPreferencesContext(): String {
        val prefs = _userPreferences.value
        if (prefs.isEmpty()) return ""

        return buildString {
            appendLine("\n<user_preferences>")
            if (prefs.communicationLanguage.isNotBlank())
                appendLine("- Language: ${prefs.communicationLanguage}")
            if (prefs.codeStyle.isNotBlank())
                appendLine("- Code style: ${prefs.codeStyle}")
            if (prefs.preferredLanguages.isNotEmpty())
                appendLine("- Programming languages: ${prefs.preferredLanguages.joinToString(", ")}")
            if (prefs.preferredTools.isNotEmpty())
                appendLine("- Preferred tools: ${prefs.preferredTools.joinToString(", ")}")
            if (prefs.verbosity.isNotBlank())
                appendLine("- Response style: ${prefs.verbosity}")
            prefs.customNotes.forEach { appendLine("- $it") }
            appendLine("</user_preferences>")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SIMILARITY & RELEVANCE
    // ═══════════════════════════════════════════════════════════

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü_.-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()
    }

    private fun calculateSimilarity(text1: String, text2: String): Float {
        return calculateSimilarity(tokenize(text1), tokenize(text2))
    }

    private fun calculateSimilarity(tokens1: Set<String>, tokens2: Set<String>): Float {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0f
        val intersection = tokens1.intersect(tokens2)
        val union = tokens1.union(tokens2)
        return intersection.size.toFloat() / union.size.toFloat()
    }

    private fun calculateRelevance(queryTerms: Set<String>, item: KnowledgeItem): Float {
        val titleTokens = tokenize(item.title)
        val contentTokens = tokenize(item.content)
        val tagTokens = item.tags.flatMap { tokenize(it) }.toSet()

        val titleScore = calculateSimilarity(queryTerms, titleTokens) * 2.0f
        val contentScore = calculateSimilarity(queryTerms, contentTokens)
        val tagScore = calculateSimilarity(queryTerms, tagTokens) * 1.5f

        // Recency boost (memories accessed recently are more relevant)
        val ageHours = (System.currentTimeMillis() - item.lastAccessedAt) / 3_600_000f
        val recencyBoost = 1f / (1f + ageHours / 168f) // Decay over a week

        // Confidence boost
        val confidenceBoost = item.confidence

        return (titleScore + contentScore + tagScore) * recencyBoost * confidenceBoost
    }

    // ═══════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════

    private inline fun <reified T> loadList(filename: String): List<T> {
        val file = File(memoryDir, filename)
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<T>>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $filename", e)
            emptyList()
        }
    }

    private inline fun <reified T> saveList(filename: String, data: List<T>) {
        try {
            val file = File(memoryDir, filename)
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $filename", e)
        }
    }

    private inline fun <reified T> loadObject(filename: String): T? {
        val file = File(memoryDir, filename)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<T>(file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $filename", e)
            null
        }
    }

    private inline fun <reified T> saveObject(filename: String, data: T) {
        try {
            val file = File(memoryDir, filename)
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $filename", e)
        }
    }

    private fun generateId(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    private fun updateStats() {
        _stats.value = MemoryStats(
            totalKnowledge = _knowledgeItems.value.size,
            totalTaskPatterns = _taskPatterns.value.size,
            totalErrorSolutions = _errorSolutions.value.size,
            totalProjectEntries = _projectKnowledge.value.size
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════

@Serializable
data class KnowledgeItem(
    val id: String,
    val title: String,
    val content: String,
    val category: KnowledgeCategory,
    val tags: List<String> = emptyList(),
    val source: String = "",
    val confidence: Float = 0.8f,
    val createdAt: Long = 0,
    val lastAccessedAt: Long = 0,
    val accessCount: Int = 0
)

@Serializable
enum class KnowledgeCategory {
    CODING_PATTERN,      // How to implement specific patterns
    TOOL_USAGE,          // How to use tools and commands
    ARCHITECTURE,        // System design knowledge
    DEBUGGING,           // Debugging techniques
    BEST_PRACTICE,       // Best practices learned
    LANGUAGE_SPECIFIC,   // Language-specific knowledge
    FRAMEWORK,           // Framework-specific knowledge
    DEVOPS,              // Build, deploy, CI/CD knowledge
    GENERAL              // General knowledge
}

@Serializable
data class TaskPattern(
    val id: String,
    val description: String,
    val steps: List<String>,
    val tools: List<String>,
    val commands: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val outcome: TaskOutcome,
    val notes: String = "",
    val createdAt: Long = 0,
    val useCount: Int = 0
)

@Serializable
enum class TaskOutcome {
    SUCCESS,
    PARTIAL,
    FAILED,
    UNKNOWN
}

@Serializable
data class ErrorSolution(
    val id: String,
    val errorMessage: String,
    val errorContext: String,
    val language: String = "",
    val solution: String,
    val explanation: String = "",
    val preventionTip: String = "",
    val createdAt: Long = 0,
    val resolvedCount: Int = 0
)

@Serializable
data class ProjectKnowledge(
    val id: String,
    val projectPath: String,
    val aspect: String,
    val knowledge: String,
    val filePatterns: List<String> = emptyList(),
    val updatedAt: Long = 0
)

@Serializable
data class UserPreferences(
    val communicationLanguage: String = "",
    val codeStyle: String = "",
    val preferredLanguages: List<String> = emptyList(),
    val preferredTools: List<String> = emptyList(),
    val verbosity: String = "",
    val customNotes: List<String> = emptyList()
) {
    fun isEmpty(): Boolean = communicationLanguage.isBlank() && codeStyle.isBlank() &&
            preferredLanguages.isEmpty() && preferredTools.isEmpty() &&
            verbosity.isBlank() && customNotes.isEmpty()
}

data class MemoryResult(
    val type: MemoryType,
    val title: String,
    val content: String,
    val relevance: Float,
    val timestamp: Long,
    val tags: List<String> = emptyList()
)

enum class MemoryType(val label: String) {
    KNOWLEDGE("Knowledge"),
    TASK_PATTERN("Task Pattern"),
    ERROR_SOLUTION("Error Solution"),
    PROJECT("Project")
}

@Serializable
data class MemoryStats(
    val totalKnowledge: Int = 0,
    val totalTaskPatterns: Int = 0,
    val totalErrorSolutions: Int = 0,
    val totalProjectEntries: Int = 0
) {
    val total: Int get() = totalKnowledge + totalTaskPatterns + totalErrorSolutions + totalProjectEntries
    override fun toString() = "Memory: $total items (K=$totalKnowledge, T=$totalTaskPatterns, E=$totalErrorSolutions, P=$totalProjectEntries)"
}
