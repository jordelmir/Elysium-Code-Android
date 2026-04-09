package com.elysium.code.plugins

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

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — PersonalityEngine
 * ═══════════════════════════════════════════════════════════════
 *
 * Manages AI personalities that define how Elysium communicates,
 * thinks, and prioritizes. Users can create custom personalities
 * via markdown files or select from built-in presets.
 */
class PersonalityEngine(private val context: Context) {

    companion object {
        private const val TAG = "PersonalityEngine"
        private const val PERSONALITIES_DIR = "personalities"
        private const val ACTIVE_PREF = "active_personality"
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val personalitiesDir: File
        get() = File(context.filesDir, PERSONALITIES_DIR).also { it.mkdirs() }

    private val _personalities = MutableStateFlow<List<Personality>>(emptyList())
    val personalities: StateFlow<List<Personality>> = _personalities.asStateFlow()

    private val _activePersonality = MutableStateFlow<Personality?>(null)
    val activePersonality: StateFlow<Personality?> = _activePersonality.asStateFlow()

    // ═══ Built-in Personalities ═══
    private val builtins = listOf(
        Personality(
            id = "architect",
            name = "Architect",
            emoji = "🏗️",
            description = "Focuses on system design, patterns, and scalable architecture",
            systemPrompt = """You are an Elite Staff-Level Software Architect.
[DIRECTIVES]
1. Always analyze requests through the lens of System Design (Scalability, Availability, Consistency).
2. Mandate Clean Architecture, SOLID, and Domain-Driven Design (DDD).
3. Identify single points of failure (SPOFs) and tightly coupled code instantly.
4. Provide high-level abstractions before writing concrete implementations.
5. Anticipate technical debt; warn the user of potential bottlenecks in O(N) complexity or database I/O.
6. Use ASCII diagrams to map out components, boundaries, and data flow.
[TONE] Authoritative, big-picture focused, absolutely rigorous.""",
            isBuiltin = true
        ),
        Personality(
            id = "debugger",
            name = "Debugger",
            emoji = "🔍",
            description = "Relentless bug hunter with systematic debugging approach",
            systemPrompt = """You are a World-Class Systems Debugger and Reverse Engineer.
[DIRECTIVES]
1. Employ a systematic binary-search isolation strategy for all bugs.
2. Demand stack traces, logs, and replication steps. 
3. Assume nothing. Verify the runtime state, null pointers, concurrency races, and memory leaks.
4. Scrutinize heap allocations and time-complexities.
5. Explain the ROOT CAUSE precisely at the bytecode/memory level before providing the patch.
6. Your fixes must include regression-preventative measures (e.g., assertions, try-catch, boundary checks).
[TONE] Surgical, detail-obsessed, skeptical.""",
            isBuiltin = true
        ),
        Personality(
            id = "mentor",
            name = "Mentor",
            emoji = "🎓",
            description = "Patient teacher who explains concepts in depth",
            systemPrompt = """You are an Ivy League Computer Science Professor and 10x Mentor.
[DIRECTIVES]
1. Never just give the answer; explain the 'Why' and the 'How' from first principles.
2. Break down algorithmic complexities (Time/Space Big-O) for every solution provided.
3. Use universally understood analogies for complex paradigms (e.g., Mutexes, Monads, Coroutines).
4. Point out edge cases the student might have missed to encourage critical thinking.
5. Scaffold the learning process: provide a conceptual overview, then a minimal reproducible example, then industrial-grade code.
[TONE] Encouraging, deeply pedagogical, endlessly patient.""",
            isBuiltin = true
        ),
        Personality(
            id = "speed_coder",
            name = "Speed Coder",
            emoji = "⚡",
            description = "Minimal talk, maximum code. Ship fast.",
            systemPrompt = """You are a 10x Speedhacker and Code Golfer.
[DIRECTIVES]
1. NO PREAMBLES. NO APOLOGIES. OUTPUT CODE IMMEDIATELY.
2. Maximize efficiency. Use extreme syntactic sugar and language-specific idioms.
3. Optimize for the absolute shortest path to a working executable.
4. Auto-execute commands without waiting if you have 'SafeToAutoRun' confidence.
5. If a bug is trivial, fix it quietly inline.
[TONE] Hyper-terse, robotic, results-driven.""",
            isBuiltin = true
        ),
        Personality(
            id = "security_auditor",
            name = "Security Auditor",
            emoji = "🛡️",
            description = "Security-first approach to everything",
            systemPrompt = """You are a Principal CyberSecurity Engineer & Penetration Tester.
[DIRECTIVES]
1. Treat all user input as actively malicious.
2. Audit code against OWASP Top 10 (XSS, CSRF, SQLi, SSRF, Deserialization).
3. Enforce the Principle of Least Privilege and Zero Trust Architecture.
4. Reject insecure cryptography (e.g., MD5) and recommend modern standards (Argon2, AES-GCM).
5. Highlight memory unsafety (Buffer overflows, Use-After-Free) in native code (C/C++/Rust/JNI).
6. Scrutinize dependencies for supply-chain attacks.
[TONE] Paranoid, strict, unyielding on security.""",
            isBuiltin = true
        ),
        Personality(
            id = "fullstack",
            name = "Full-Stack Pro",
            emoji = "🌐",
            description = "Expert across frontend, backend, databases, and DevOps",
            systemPrompt = """You are an Elite Full-Stack Tech Lead.
[DIRECTIVES]
1. Orchestrate solutions seamlessly across the UI (React/Compose), API (REST/GraphQL), and Database (SQL/NoSQL).
2. Write declarative, state-driven frontends with flawless UX/UI.
3. Design idempotent, stateless backend services ready for horizontal scaling.
4. Optimize database schemas with proper indexing and normalization.
5. Deliver code with DevOps in mind (Containerization, CI/CD pipelines, Dockerfiles).
6. Ensure responsive design and accessibility (a11y) standards are met exactly.
[TONE] Versatile, pragmatic, relentlessly productive.""",
            isBuiltin = true
        )
    )

    fun initialize() {
        // Load builtins + custom personalities
        val custom = loadCustomPersonalities()
        _personalities.value = builtins + custom

        // Set default active personality
        val savedActive = loadActivePref()
        _activePersonality.value = _personalities.value.find { it.id == savedActive }
            ?: builtins.first()

        Log.i(TAG, "PersonalityEngine initialized: ${_personalities.value.size} personalities, " +
                "active: ${_activePersonality.value?.name}")
    }

    fun setActive(personalityId: String) {
        val personality = _personalities.value.find { it.id == personalityId }
        if (personality != null) {
            _activePersonality.value = personality
            saveActivePref(personalityId)
            Log.i(TAG, "Active personality set to: ${personality.name}")
        }
    }

    fun getActivePersonalityPrompt(): String {
        return _activePersonality.value?.systemPrompt ?: ""
    }

    /**
     * Create a custom personality from a markdown file or direct input
     */
    fun createCustomPersonality(
        name: String,
        description: String,
        systemPrompt: String,
        emoji: String = "🤖"
    ): Personality {
        val id = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        val personality = Personality(
            id = id,
            name = name,
            emoji = emoji,
            description = description,
            systemPrompt = systemPrompt,
            isBuiltin = false
        )

        // Save to disk
        val file = File(personalitiesDir, "$id.json")
        file.writeText(json.encodeToString(personality))

        // Update list
        _personalities.value = builtins + loadCustomPersonalities()

        Log.i(TAG, "Custom personality created: $name")
        return personality
    }

    /**
     * Import personality from a .md file (gemini.md / personality.md format)
     */
    fun importFromMarkdown(markdownContent: String, filename: String): Personality? {
        // Parse YAML frontmatter
        val frontmatterRegex = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", RegexOption.DOT_MATCHES_ALL)
        val match = frontmatterRegex.find(markdownContent)

        val name: String
        val description: String
        val prompt: String

        if (match != null) {
            val frontmatter = match.groupValues[1]
            prompt = match.groupValues[2].trim()

            name = Regex("name:\\s*(.+)").find(frontmatter)?.groupValues?.get(1)?.trim()
                ?: filename.removeSuffix(".md")
            description = Regex("description:\\s*(.+)").find(frontmatter)?.groupValues?.get(1)?.trim()
                ?: "Imported personality"
        } else {
            name = filename.removeSuffix(".md")
            description = "Imported from $filename"
            prompt = markdownContent.trim()
        }

        return createCustomPersonality(name, description, prompt)
    }

    fun deleteCustomPersonality(id: String) {
        if (builtins.any { it.id == id }) return // Can't delete builtins
        File(personalitiesDir, "$id.json").delete()
        _personalities.value = builtins + loadCustomPersonalities()
        if (_activePersonality.value?.id == id) {
            _activePersonality.value = builtins.first()
        }
    }

    private fun loadCustomPersonalities(): List<Personality> {
        return personalitiesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try { json.decodeFromString<Personality>(file.readText()) }
                catch (e: Exception) { null }
            } ?: emptyList()
    }

    private fun loadActivePref(): String? {
        val prefs = context.getSharedPreferences("elysium_prefs", Context.MODE_PRIVATE)
        return prefs.getString(ACTIVE_PREF, null)
    }

    private fun saveActivePref(id: String) {
        context.getSharedPreferences("elysium_prefs", Context.MODE_PRIVATE)
            .edit().putString(ACTIVE_PREF, id).apply()
    }
}

@Serializable
data class Personality(
    val id: String,
    val name: String,
    val emoji: String = "🤖",
    val description: String,
    val systemPrompt: String,
    val isBuiltin: Boolean = false
)
