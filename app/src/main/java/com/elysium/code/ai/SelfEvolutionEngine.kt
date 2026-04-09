package com.elysium.code.ai

import android.content.Context
import android.util.Log
import com.elysium.code.memory.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — SelfEvolutionEngine
 * ═══════════════════════════════════════════════════════════════
 *
 * The agent's autonomous self-improvement system. Analyzes every
 * interaction to extract patterns, learn from mistakes, optimize
 * strategies, and evolve its behavior over time.
 *
 * Evolution happens through:
 * 1. Interaction Analysis — What worked, what failed
 * 2. Strategy Optimization — Which tools/approaches are most effective
 * 3. Knowledge Consolidation — Merging related memories
 * 4. Preference Learning — Adapting to user's style
 * 5. Error Pattern Detection — Learning from repeated mistakes
 */
class SelfEvolutionEngine(
    private val context: Context,
    private val memoryEngine: MemoryEngine
) {
    companion object {
        private const val TAG = "SelfEvolution"
        private const val EVOLUTION_DIR = "evolution"
        private const val METRICS_FILE = "evolution_metrics.json"
        private const val STRATEGIES_FILE = "strategies.json"
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val evolutionDir: File get() = File(context.filesDir, EVOLUTION_DIR).also { it.mkdirs() }

    private var metrics = EvolutionMetrics()
    private var strategies = mutableListOf<Strategy>()

    // ═══════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val metricsFile = File(evolutionDir, METRICS_FILE)
            if (metricsFile.exists()) {
                metrics = json.decodeFromString(metricsFile.readText())
            }
            val strategiesFile = File(evolutionDir, STRATEGIES_FILE)
            if (strategiesFile.exists()) {
                strategies = json.decodeFromString<MutableList<Strategy>>(strategiesFile.readText())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading evolution data", e)
        }
        Log.i(TAG, "SelfEvolution initialized: ${metrics.totalInteractions} interactions tracked")
    }

    // ═══════════════════════════════════════════════════════════
    // INTERACTION TRACKING
    // ═══════════════════════════════════════════════════════════

    /**
     * Record the outcome of a complete agent interaction.
     * Called after every user↔agent cycle.
     */
    suspend fun recordInteraction(interaction: InteractionRecord) = withContext(Dispatchers.IO) {
        metrics.totalInteractions++

        // Track success/failure
        when (interaction.outcome) {
            InteractionOutcome.SUCCESS -> {
                metrics.successCount++
                metrics.consecutiveSuccesses++
                metrics.consecutiveFailures = 0
            }
            InteractionOutcome.PARTIAL -> {
                metrics.partialCount++
                metrics.consecutiveSuccesses = 0
                metrics.consecutiveFailures = 0
            }
            InteractionOutcome.FAILURE -> {
                metrics.failureCount++
                metrics.consecutiveFailures++
                metrics.consecutiveSuccesses = 0
            }
        }

        // Track tool usage effectiveness
        interaction.toolsUsed.forEach { tool ->
            val existing = metrics.toolEffectiveness.getOrPut(tool) { ToolMetrics() }
            existing.useCount++
            if (interaction.outcome == InteractionOutcome.SUCCESS) existing.successCount++
            existing.avgDurationMs = (existing.avgDurationMs * (existing.useCount - 1) + interaction.durationMs) / existing.useCount
        }

        // Track response time
        metrics.avgResponseTimeMs = (metrics.avgResponseTimeMs * (metrics.totalInteractions - 1) + interaction.durationMs) / metrics.totalInteractions

        // Track tokens
        metrics.totalTokensGenerated += interaction.tokensGenerated

        // Auto-learn from success patterns
        if (interaction.outcome == InteractionOutcome.SUCCESS && interaction.toolsUsed.isNotEmpty()) {
            learnStrategy(interaction)
        }

        // Auto-learn from failures
        if (interaction.outcome == InteractionOutcome.FAILURE) {
            recordFailurePattern(interaction)
        }

        // Detect user language preference
        detectLanguagePreference(interaction.userQuery)

        // Persist
        saveMetrics()
        Log.i(TAG, "Interaction recorded: ${interaction.outcome} (${interaction.toolsUsed.size} tools, ${interaction.tokensGenerated} tokens)")
    }

    // ═══════════════════════════════════════════════════════════
    // STRATEGY LEARNING
    // ═══════════════════════════════════════════════════════════

    /**
     * Learn a successful strategy for future use
     */
    private suspend fun learnStrategy(interaction: InteractionRecord) {
        val category = categorizeQuery(interaction.userQuery)
        val existing = strategies.find { it.category == category }

        if (existing != null) {
            existing.successCount++
            existing.toolSequence = (existing.toolSequence + interaction.toolsUsed).distinct()
            existing.lastUsed = System.currentTimeMillis()
        } else {
            strategies.add(Strategy(
                category = category,
                description = "Learned from: ${interaction.userQuery.take(100)}",
                toolSequence = interaction.toolsUsed,
                successCount = 1,
                lastUsed = System.currentTimeMillis()
            ))
        }

        saveStrategies()

        // Also record in memory engine
        memoryEngine.recordTaskPattern(
            taskDescription = interaction.userQuery.take(200),
            stepsPerformed = interaction.steps,
            toolsUsed = interaction.toolsUsed,
            commandsRun = interaction.commandsRun,
            filesModified = interaction.filesModified,
            outcome = TaskOutcome.SUCCESS,
            notes = "Auto-learned by SelfEvolution (${metrics.totalInteractions} interactions)"
        )
    }

    /**
     * Record a failure pattern to avoid in the future
     */
    private suspend fun recordFailurePattern(interaction: InteractionRecord) {
        if (interaction.errorMessage.isNotBlank()) {
            memoryEngine.recordErrorSolution(
                errorMessage = interaction.errorMessage,
                errorContext = interaction.userQuery.take(200),
                solution = "NOT YET RESOLVED — Agent failed on this task",
                explanation = "Tools used: ${interaction.toolsUsed.joinToString(", ")}"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // STRATEGY RECOMMENDATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Get recommended strategy for a given query.
     * Returns the most effective approach based on past interactions.
     */
    fun recommendStrategy(query: String): StrategyRecommendation? {
        val category = categorizeQuery(query)
        val strategy = strategies
            .filter { it.category == category && it.successCount >= 2 }
            .maxByOrNull { it.successCount }
            ?: return null

        return StrategyRecommendation(
            suggestedTools = strategy.toolSequence,
            confidence = (strategy.successCount.toFloat() / (strategy.successCount + 1)).coerceAtMost(0.95f),
            basedOnInteractions = strategy.successCount
        )
    }

    /**
     * Get the evolution summary for the system prompt
     */
    fun getEvolutionContext(): String {
        if (metrics.totalInteractions < 3) return ""

        return buildString {
            appendLine("\n<evolution_context>")
            appendLine("Agent Evolution Status:")
            appendLine("- Total interactions: ${metrics.totalInteractions}")
            appendLine("- Success rate: ${"%.1f".format(metrics.successRate * 100)}%")
            appendLine("- Most effective tools: ${getTopTools(3).joinToString(", ")}")
            if (metrics.consecutiveSuccesses > 5) {
                appendLine("- Streak: ${metrics.consecutiveSuccesses} consecutive successes 🔥")
            }
            val topStrategy = strategies.maxByOrNull { it.successCount }
            if (topStrategy != null) {
                appendLine("- Best strategy category: ${topStrategy.category} (${topStrategy.successCount} successes)")
            }
            appendLine("</evolution_context>")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ANALYSIS HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun categorizeQuery(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("bug") || q.contains("fix") || q.contains("error") || q.contains("crash") -> "debugging"
            q.contains("create") || q.contains("build") || q.contains("new") || q.contains("scaffold") -> "creation"
            q.contains("refactor") || q.contains("clean") || q.contains("improve") -> "refactoring"
            q.contains("test") || q.contains("unittest") -> "testing"
            q.contains("explain") || q.contains("what") || q.contains("how") || q.contains("why") -> "explanation"
            q.contains("deploy") || q.contains("docker") || q.contains("ci") -> "devops"
            q.contains("review") || q.contains("audit") -> "review"
            q.contains("performance") || q.contains("optimize") || q.contains("slow") -> "optimization"
            q.contains("search") || q.contains("find") || q.contains("where") -> "search"
            q.contains("git") || q.contains("commit") || q.contains("branch") -> "version_control"
            else -> "general"
        }
    }

    private fun detectLanguagePreference(query: String) {
        val isSpanish = query.contains("crea", ignoreCase = true) ||
                query.contains("hazlo", ignoreCase = true) ||
                query.contains("arregla", ignoreCase = true) ||
                query.contains("proyecto", ignoreCase = true)
        if (isSpanish) {
            metrics.detectedLanguage = "es"
        }
    }

    private fun getTopTools(n: Int): List<String> {
        return metrics.toolEffectiveness
            .entries
            .sortedByDescending { it.value.successRate }
            .take(n)
            .map { "${it.key} (${(it.value.successRate * 100).toInt()}%)" }
    }

    // ═══════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════

    private fun saveMetrics() {
        try { File(evolutionDir, METRICS_FILE).writeText(json.encodeToString(metrics)) }
        catch (e: Exception) { Log.e(TAG, "Save metrics failed", e) }
    }

    private fun saveStrategies() {
        try { File(evolutionDir, STRATEGIES_FILE).writeText(json.encodeToString(strategies.toList())) }
        catch (e: Exception) { Log.e(TAG, "Save strategies failed", e) }
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════

data class InteractionRecord(
    val userQuery: String,
    val outcome: InteractionOutcome,
    val toolsUsed: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val commandsRun: List<String> = emptyList(),
    val filesModified: List<String> = emptyList(),
    val tokensGenerated: Int = 0,
    val durationMs: Long = 0,
    val errorMessage: String = ""
)

enum class InteractionOutcome { SUCCESS, PARTIAL, FAILURE }

@Serializable
data class EvolutionMetrics(
    var totalInteractions: Int = 0,
    var successCount: Int = 0,
    var partialCount: Int = 0,
    var failureCount: Int = 0,
    var consecutiveSuccesses: Int = 0,
    var consecutiveFailures: Int = 0,
    var totalTokensGenerated: Long = 0,
    var avgResponseTimeMs: Long = 0,
    var toolEffectiveness: MutableMap<String, ToolMetrics> = mutableMapOf(),
    var detectedLanguage: String = ""
) {
    val successRate: Float get() = if (totalInteractions > 0) successCount.toFloat() / totalInteractions else 0f
}

@Serializable
data class ToolMetrics(
    var useCount: Int = 0,
    var successCount: Int = 0,
    var avgDurationMs: Long = 0
) {
    val successRate: Float get() = if (useCount > 0) successCount.toFloat() / useCount else 0f
}

@Serializable
data class Strategy(
    val category: String,
    val description: String,
    var toolSequence: List<String>,
    var successCount: Int = 0,
    var lastUsed: Long = 0
)

data class StrategyRecommendation(
    val suggestedTools: List<String>,
    val confidence: Float,
    val basedOnInteractions: Int
)
