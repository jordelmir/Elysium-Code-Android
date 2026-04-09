package com.elysium.code.ai

import android.content.Context
import android.os.Build
import com.elysium.code.memory.MemoryEngine
import com.elysium.code.plugins.PersonalityEngine
import com.elysium.code.plugins.SkillsParser
import java.text.SimpleDateFormat
import java.util.*

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — AdvancedPromptEngine
 * ═══════════════════════════════════════════════════════════════
 *
 * World-class prompt construction system that builds
 * context-rich, personality-aware, memory-augmented prompts
 * for Gemma 4 E4B. This is the brain behind the brain.
 *
 * Architecture:
 * ┌────────────────────────────────────────┐
 * │ System Identity + Capabilities         │
 * ├────────────────────────────────────────┤
 * │ Active Personality Prompt              │
 * ├────────────────────────────────────────┤
 * │ Active Skills Instructions             │
 * ├────────────────────────────────────────┤
 * │ Memory Context (recalled knowledge)    │
 * ├────────────────────────────────────────┤
 * │ User Preferences                       │
 * ├────────────────────────────────────────┤
 * │ Tool Descriptions (ReAct-format)       │
 * ├────────────────────────────────────────┤
 * │ Conversation History                   │
 * ├────────────────────────────────────────┤
 * │ Current User Query                     │
 * └────────────────────────────────────────┘
 */
class AdvancedPromptEngine(
    private val context: Context,
    private val memoryEngine: MemoryEngine,
    private val personalityEngine: PersonalityEngine,
    private val skillsParser: SkillsParser
) {
    companion object {
        private const val TAG = "AdvancedPromptEngine"
        private const val MAX_CONTEXT_TOKENS = 3800 // Leave room for response
        private const val MAX_HISTORY_TURNS = 12
        private const val MAX_MEMORY_ITEMS = 8
    }

    // ═══════════════════════════════════════════════════════════
    // CORE SYSTEM IDENTITY
    // ═══════════════════════════════════════════════════════════

    private val systemIdentity = """
<system>
You are **Elysium**, the most advanced and capable software engineer in human history, running locally on this device via Gemma 4 E4B.
You possess a flawless understanding of computer science, algorithms, SOLID principles, Clean Architecture, and defensive programming.

## Core Engineering Principles
1. **PERFECTION & TDD**: Never write code blindly. Write code that is testable, robust, and handles every possible edge case (nulls, bounds, concurrency, memory leaks).
2. **ALGORITHMIC EXCELLENCE**: Always optimize for Time (Big-O) and Space complexity. If a more optimal data structure exists, use it.
3. **SELF-HEALING & DEFENSIVE**: Your code must never crash uncontrollably. Use extensive error boundaries and graceful fallbacks.
4. **LOCAL-FIRST AGENTIC**: You autonomously execute shell commands, read/write files, and navigate codebases using the ReAct loop. USE your tools to verify your work.
5. **ARCHITECTURAL VISION**: Anticipate technical debt. If you spot a bad pattern, refactor it instantly. Do not provide placeholders.

## Capabilities
- 💻 Write, debug, refactor, and review code in 50+ languages
- 📁 Create, read, edit, and delete files on the device
- 🔧 Execute shell commands (ls, grep, git, python, node, etc.)
- 🔍 Search through codebases with grep and find
- 📊 Analyze project architecture and dependencies
- 📷 Process images and screenshots (multimodal)
- 🎤 Process audio transcriptions (multimodal)
- 🎥 Analyze video content frame-by-frame (multimodal)
- 🧠 Persistent memory — learns from every interaction
- 🔄 Multi-step reasoning with autonomous tool use

## Output Format
- Use markdown formatting for structured responses
- Wrap code in fenced code blocks with language tags
- For file edits, show the exact path and changes
- Be concise but thorough — never waste the user's time
- When you use a tool, explain what you're doing and why

## Error Handling
- If a command fails, diagnose the root cause before retrying
- Never silently ignore errors — always report and explain
- If unsure, ask the user rather than guessing

## Current Environment
- Device: ${Build.MANUFACTURER} ${Build.MODEL}
- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
- Architecture: ${Build.SUPPORTED_ABIS.joinToString(", ")}
- Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}
- Working Directory: /data/data/com.elysium.code/files/home
</system>
""".trimIndent()

    // ═══════════════════════════════════════════════════════════
    // REACT TOOL FORMAT
    // ═══════════════════════════════════════════════════════════

    private val reactInstructions = """
<react_protocol>
You use a Reason + Reflect + Act (ReAct-Reflect) loop to solve complex tasks. Follow this EXACT pattern:

**Thought**: Analyze the situation. Break down the problem logically and algorithmically.
**Reflection**: Critically evaluate your thought. Is there a more optimal solution (Big-O)? Did you account for edge cases? Does this violate Clean Architecture? Correct yourself if needed.
**Action**: Choose a tool and provide the arguments as JSON.
**Observation**: Read the tool's output.
**Repeat** until you have absolute confidence in the solution's stability and optimality.
**Answer**: Provide your final, definitive response to the user.

### Tool Format
When you need to use a tool, output EXACTLY this format:
```
<tool_call>
{"name": "tool_name", "args": {"param1": "value1", "param2": "value2"}}
</tool_call>
```

### Available Tools

#### execute_command
Execute a shell command on the device.
- `command` (string, required): The command to run
- `timeout_seconds` (integer, optional): Max execution time (default 30)

#### read_file
Read the contents of a file.
- `path` (string, required): Absolute path to the file
- `max_lines` (integer, optional): Maximum lines to read

#### write_file
Create or overwrite a file with content.
- `path` (string, required): Absolute path to the file
- `content` (string, required): File contents

#### edit_file
Replace text in an existing file (surgical edit).
- `path` (string, required): Absolute path to the file
- `old_text` (string, required): Exact text to find and replace
- `new_text` (string, required): Replacement text

#### list_directory
List files and directories.
- `path` (string, required): Directory path
- `recursive` (boolean, optional): Include subdirectories

#### search_files
Search for text patterns across files.
- `query` (string, required): Text or regex to search for
- `path` (string, optional): Directory to search in
- `file_pattern` (string, optional): Glob pattern (e.g., "*.kt")

#### create_directory
Create a directory (and parents).
- `path` (string, required): Directory path

#### delete_file
Delete a file.
- `path` (string, required): Path to delete

#### file_info
Get metadata about a file.
- `path` (string, required): Path to inspect

#### git_status
Show git repository status.
- `path` (string, optional): Repository path

#### git_diff
Show git diff.
- `path` (string, optional): Repository path
- `file` (string, optional): Specific file

#### git_log
Show recent git commits.
- `path` (string, optional): Repository path

### Rules
1. Always implement **Thought** and **Reflection** before **Action**.
2. Be highly critical of your own code. If it's O(N^2) and can be O(N log N), fix it in the Reflection step.
3. Use the MINIMUM number of tool calls needed, but VERIFY the output every time.
4. Chain tool calls logically (e.g., read before edit, test after edit).
5. If the task is simple, just answer directly with perfection.
</react_protocol>
""".trimIndent()

    // ═══════════════════════════════════════════════════════════
    // PROMPT CONSTRUCTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Build the complete prompt for a user query.
     * This is the primary entry point used by AgentOrchestrator.
     */
    fun buildPrompt(
        userQuery: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        activeToolResults: List<Pair<String, String>> = emptyList(),
        projectContext: String = "",
        isMultimodal: Boolean = false,
        multimodalPrefix: String = ""
    ): String {
        return buildString {
            // 1. System identity
            append(systemIdentity)
            append("\n\n")

            // 2. Active personality
            val personalityPrompt = personalityEngine.getActivePersonalityPrompt()
            if (personalityPrompt.isNotBlank()) {
                append("<personality>\n")
                append(personalityPrompt)
                append("\n</personality>\n\n")
            }

            // 3. Active skills
            val activeSkills = buildActiveSkillsContext()
            if (activeSkills.isNotBlank()) {
                append(activeSkills)
                append("\n\n")
            }

            // 4. User preferences
            val prefsContext = memoryEngine.getPreferencesContext()
            if (prefsContext.isNotBlank()) {
                append(prefsContext)
                append("\n\n")
            }

            // 5. Memory context (relevant to this query)
            val memoryContext = memoryEngine.buildMemoryContext(userQuery)
            if (memoryContext.isNotBlank()) {
                append(memoryContext)
                append("\n\n")
            }

            // 6. Project context (if working on specific project)
            if (projectContext.isNotBlank()) {
                append("<project_context>\n")
                append(projectContext)
                append("\n</project_context>\n\n")
            }

            // 7. ReAct tool instructions
            append(reactInstructions)
            append("\n\n")

            // 8. Conversation history
            if (conversationHistory.isNotEmpty()) {
                append("<conversation_history>\n")
                val recentHistory = conversationHistory.takeLast(MAX_HISTORY_TURNS)
                recentHistory.forEach { (role, content) ->
                    val truncatedContent = if (content.length > 800) {
                        content.take(800) + "\n[...truncated]"
                    } else content
                    append("**${role.capitalize()}**: $truncatedContent\n\n")
                }
                append("</conversation_history>\n\n")
            }

            // 9. Active tool results (for ReAct loop iterations)
            if (activeToolResults.isNotEmpty()) {
                append("<tool_results>\n")
                activeToolResults.forEach { (toolName, result) ->
                    val truncatedResult = if (result.length > 1500) {
                        result.take(1500) + "\n[...output truncated]"
                    } else result
                    append("### $toolName\n```\n$truncatedResult\n```\n\n")
                }
                append("</tool_results>\n\n")
            }

            // 10. Multimodal prefix (image/audio/video description)
            if (isMultimodal && multimodalPrefix.isNotBlank()) {
                append("<multimodal_input>\n")
                append(multimodalPrefix)
                append("\n</multimodal_input>\n\n")
            }

            // 11. User query
            append("**User**: $userQuery\n\n")
            append("**Elysium**: ")
        }
    }

    /**
     * Build a lightweight prompt for quick responses (no tools needed)
     */
    fun buildQuickPrompt(
        userQuery: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): String {
        return buildString {
            append(systemIdentity)
            append("\n\n")

            val personalityPrompt = personalityEngine.getActivePersonalityPrompt()
            if (personalityPrompt.isNotBlank()) {
                append("<personality>\n$personalityPrompt\n</personality>\n\n")
            }

            if (conversationHistory.isNotEmpty()) {
                val recent = conversationHistory.takeLast(6)
                recent.forEach { (role, content) ->
                    append("**${role.capitalize()}**: ${content.take(500)}\n\n")
                }
            }

            append("**User**: $userQuery\n\n")
            append("**Elysium**: ")
        }
    }

    /**
     * Build a prompt specifically for code analysis/review
     */
    fun buildCodeAnalysisPrompt(
        code: String,
        language: String,
        filePath: String,
        analysisType: CodeAnalysisType
    ): String {
        val instructions = when (analysisType) {
            CodeAnalysisType.REVIEW -> "Perform a thorough code review. Check for bugs, security issues, performance, and code quality."
            CodeAnalysisType.REFACTOR -> "Suggest refactoring improvements. Focus on readability, maintainability, and design patterns."
            CodeAnalysisType.EXPLAIN -> "Explain this code step by step. What does it do, how does it work, and what are the key design decisions?"
            CodeAnalysisType.OPTIMIZE -> "Analyze for performance. Identify bottlenecks, unnecessary allocations, and optimization opportunities."
            CodeAnalysisType.SECURITY -> "Perform a security audit. Check for vulnerabilities, injection risks, and unsafe patterns."
            CodeAnalysisType.TEST -> "Generate comprehensive unit tests for this code."
            CodeAnalysisType.DOCUMENT -> "Generate thorough documentation including docstrings, usage examples, and architecture notes."
        }

        return buildString {
            append(systemIdentity)
            append("\n\n")
            append("<task>\n$instructions\n</task>\n\n")
            append("<code file=\"$filePath\" language=\"$language\">\n")
            append(code)
            append("\n</code>\n\n")
            append("**Elysium**: ")
        }
    }

    /**
     * Build prompt for project scaffolding
     */
    fun buildScaffoldPrompt(
        description: String,
        language: String,
        framework: String = "",
        features: List<String> = emptyList()
    ): String {
        return buildString {
            append(systemIdentity)
            append("\n\n")
            append(reactInstructions)
            append("\n\n")
            append("<scaffold_request>\n")
            append("Generate a complete, production-ready project scaffold.\n\n")
            append("Description: $description\n")
            append("Primary Language: $language\n")
            if (framework.isNotBlank()) append("Framework: $framework\n")
            if (features.isNotEmpty()) append("Required Features: ${features.joinToString(", ")}\n")
            append("\nCreate ALL necessary files using the write_file tool. Include:\n")
            append("1. Project structure with proper directory layout\n")
            append("2. Main application entry point\n")
            append("3. Configuration files (package.json, build.gradle, etc.)\n")
            append("4. README.md with setup instructions\n")
            append("5. .gitignore\n")
            append("6. At least one example test\n")
            append("7. Proper import statements and dependencies\n")
            append("</scaffold_request>\n\n")
            append("**Elysium**: ")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private fun buildActiveSkillsContext(): String {
        val activeSkills = skillsParser.skills.value
            .filter { skill -> skill.id in skillsParser.activeSkillIds.value }
        if (activeSkills.isEmpty()) return ""

        return buildString {
            appendLine("<active_skills>")
            appendLine("The following skill modules are active. Apply their instructions when relevant:")
            appendLine()
            activeSkills.forEach { skill ->
                appendLine("### ${skill.name}")
                appendLine(skill.instructions)
                appendLine()
            }
            appendLine("</active_skills>")
        }
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

enum class CodeAnalysisType {
    REVIEW, REFACTOR, EXPLAIN, OPTIMIZE, SECURITY, TEST, DOCUMENT
}
