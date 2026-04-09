package com.elysium.code.plugins

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — SkillsParser
 * ═══════════════════════════════════════════════════════════════
 *
 * Parses skills.md files that extend the agent's capabilities.
 * Skills are markdown documents with YAML frontmatter that define
 * specialized instructions for specific tasks (code review,
 * debugging, testing, etc.)
 */
class SkillsParser(private val context: Context) {

    companion object {
        private const val TAG = "SkillsParser"
        private const val SKILLS_DIR = "skills"
    }

    private val skillsDir: File
        get() = File(context.filesDir, SKILLS_DIR).also { it.mkdirs() }

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    private val _activeSkillIds = MutableStateFlow<Set<String>>(emptySet())
    val activeSkillIds: StateFlow<Set<String>> = _activeSkillIds.asStateFlow()

    private val builtinSkills = listOf(
        Skill(
            id = "code_review",
            name = "Expert Code Review",
            description = "Thorough code review with actionable feedback",
            instructions = """[CODE REVIEW PROTOCOL]
1. Assess Cyclomatic Complexity: Highlight functions exceeding 10 branches.
2. Verify SOLID compliance: Point out violations explicitly.
3. Check Error Handling: Are exceptions swallowed? Use Result/Either monads where applicable.
4. Security Audit: Scan for SQLi, XSS, insecure dependencies, and hardcoded secrets.
5. Provide actionable refactoring snippets with `[SUGGESTION]` blocks.
6. Rate severity strictly: 🔴 BLOCKER, 🟡 MINOR, 🟢 NITPICK.""",
            isBuiltin = true
        ),
        Skill(
            id = "refactor",
            name = "Advanced Refactoring",
            description = "Intelligent code refactoring",
            instructions = """[REFACTORING PROTOCOL]
1. Target 'God Classes' and 'Long Methods'. Apply Extract Method / Extract Class.
2. Replace nested conditionals with Guard Clauses or Polymorphism.
3. Decouple logic using Dependency Injection (Dagger/Hilt/Koin).
4. Remove Dead Code proactively. Ensure proper encapsulation (private by default).
5. Ensure 100% test compatibility. Do NOT break existing APIs without warning.
6. Present the refactored code seamlessly, emphasizing the changes made.""",
            isBuiltin = true
        ),
        Skill(
            id = "test_writer",
            name = "TDD & Test Writer",
            description = "Generate comprehensive test suites",
            instructions = """[TEST WRITING PROTOCOL]
1. Standardize on the AAA pattern: Arrange, Act, Assert.
2. Test the public contract, not private implementation details.
3. Identify edge cases: null inputs, boundary values, empty lists, timeout scenarios.
4. Use MockK/Mockito for external boundaries (Network, DB). Do NOT mock pure functions.
5. Provide parameterized tests where inputs vary systematically.
6. Aim for 90%+ branch coverage.""",
            isBuiltin = true
        ),
        Skill(
            id = "performance",
            name = "Performance Optimization",
            description = "Identify and fix performance bottlenecks",
            instructions = """[PERFORMANCE OPTIMIZATION PROTOCOL]
1. Reject O(N^2) algorithms where O(N log N) or O(N) is possible. 
2. Identify Memory Leaks (Retained contexts, unclosed streams, missing dispose/cancel).
3. Check for N+1 queries in ORM/Database calls.
4. Minimize allocations in tight loops. Use primitive arrays if latency is critical.
5. Recommend caching (LRU, Redis, Memory) and Memoization for pure functions.
6. Explain the before/after Big-O complexity of the proposed changes.""",
            isBuiltin = true
        ),
        Skill(
            id = "multimodal_analyst",
            name = "Multimodal Analyst",
            description = "Process embedded images, audio, and video",
            instructions = """[MULTIMODAL PROTOCOL]
1. When you detect `<image>`, `<audio>`, or `<video>` tags in the prompt, analyze the assumed visual/audio context.
2. Describe what you observe with extreme detail (UI elements, layout, text in images, object detection).
3. If code or architecture diagrams are embedded, transcribe the logic into text/code structures.
4. Highlight any UI/UX inconsistencies if analyzing a design mockup.
5. Translate audio transcripts logically into action items or code.""",
            isBuiltin = true
        ),
        Skill(
            id = "android_expert",
            name = "Android Master",
            description = "Deep Android platform knowledge",
            instructions = """[ANDROID DEV PROTOCOL]
1. Enforce Modern Android Development (MAD): Kotlin, Coroutines, Jetpack Compose, state hoisting.
2. Warn about main-thread blocking operations. 
3. Recommend Flow/StateFlow for reactive architectures over LiveData.
4. Point out lifecycle leaks (ViewModel scoping, LaunchedEffect parameters).
5. Emphasize Clean Architecture (Presentation -> Domain <- Data).
6. Optimize layouts: reduce overdraw, avoid nested layouts.""",
            isBuiltin = true
        )
    )

    fun initialize() {
        val custom = loadCustomSkills()
        _skills.value = builtinSkills + custom

        // Default: enable all built-in skills
        _activeSkillIds.value = builtinSkills.map { it.id }.toSet()

        Log.i(TAG, "SkillsParser initialized: ${_skills.value.size} skills")
    }

    fun enableSkill(id: String) {
        _activeSkillIds.value = _activeSkillIds.value + id
    }

    fun disableSkill(id: String) {
        _activeSkillIds.value = _activeSkillIds.value - id
    }

    fun toggleSkill(id: String) {
        if (id in _activeSkillIds.value) disableSkill(id) else enableSkill(id)
    }

    fun getActiveSkillsPrompt(): String {
        val activeSkills = _skills.value.filter { it.id in _activeSkillIds.value }
        if (activeSkills.isEmpty()) return ""

        return buildString {
            appendLine("## Active Skills")
            activeSkills.forEach { skill ->
                appendLine("### ${skill.name}")
                appendLine(skill.instructions)
                appendLine()
            }
        }
    }

    /**
     * Import a skill from a markdown file
     */
    fun importSkill(markdownContent: String, filename: String): Skill? {
        val frontmatterRegex = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", RegexOption.DOT_MATCHES_ALL)
        val match = frontmatterRegex.find(markdownContent)

        val name: String
        val description: String
        val instructions: String

        if (match != null) {
            val frontmatter = match.groupValues[1]
            instructions = match.groupValues[2].trim()
            name = Regex("name:\\s*(.+)").find(frontmatter)?.groupValues?.get(1)?.trim()
                ?: filename.removeSuffix(".md")
            description = Regex("description:\\s*(.+)").find(frontmatter)?.groupValues?.get(1)?.trim()
                ?: "Imported skill"
        } else {
            name = filename.removeSuffix(".md")
            description = "Imported from $filename"
            instructions = markdownContent.trim()
        }

        val id = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        val skill = Skill(id = id, name = name, description = description,
            instructions = instructions, isBuiltin = false)

        // Save
        File(skillsDir, "$id.md").writeText(markdownContent)
        _skills.value = builtinSkills + loadCustomSkills()
        _activeSkillIds.value = _activeSkillIds.value + id

        Log.i(TAG, "Skill imported: $name")
        return skill
    }

    fun deleteSkill(id: String) {
        if (builtinSkills.any { it.id == id }) return
        File(skillsDir, "$id.md").delete()
        _skills.value = builtinSkills + loadCustomSkills()
        _activeSkillIds.value = _activeSkillIds.value - id
    }

    private fun loadCustomSkills(): List<Skill> {
        return skillsDir.listFiles()?.filter { it.extension == "md" }?.mapNotNull { file ->
            try {
                val content = file.readText()
                val frontmatterRegex = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", RegexOption.DOT_MATCHES_ALL)
                val match = frontmatterRegex.find(content)

                if (match != null) {
                    val fm = match.groupValues[1]
                    Skill(
                        id = file.nameWithoutExtension,
                        name = Regex("name:\\s*(.+)").find(fm)?.groupValues?.get(1)?.trim()
                            ?: file.nameWithoutExtension,
                        description = Regex("description:\\s*(.+)").find(fm)?.groupValues?.get(1)?.trim() ?: "",
                        instructions = match.groupValues[2].trim(),
                        isBuiltin = false
                    )
                } else {
                    Skill(
                        id = file.nameWithoutExtension,
                        name = file.nameWithoutExtension,
                        description = "Custom skill",
                        instructions = content,
                        isBuiltin = false
                    )
                }
            } catch (e: Exception) { null }
        } ?: emptyList()
    }
}

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val isBuiltin: Boolean = false
)
