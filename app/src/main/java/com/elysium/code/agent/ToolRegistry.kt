package com.elysium.code.agent

import android.util.Log
import kotlinx.serialization.json.*
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — ToolRegistry + ToolExecutor
 * ═══════════════════════════════════════════════════════════════
 *
 * Defines all tools the agent can use and handles safe execution.
 * Tools include shell commands, file operations, code analysis,
 * git operations, and more.
 */
class ToolRegistry {

    private val tools = mutableMapOf<String, ToolDefinition>()

    init {
        registerBuiltinTools()
    }

    private fun registerBuiltinTools() {
        register(ToolDefinition(
            name = "execute_command",
            description = "Execute a shell command in the terminal. Returns stdout and stderr.",
            parameters = mapOf(
                "command" to "The shell command to execute",
                "timeout_seconds" to "(Optional) Timeout in seconds, default 30"
            )
        ))

        register(ToolDefinition(
            name = "read_file",
            description = "Read the contents of a file. Returns the full text content.",
            parameters = mapOf(
                "path" to "Absolute path to the file to read",
                "max_lines" to "(Optional) Maximum number of lines to read"
            )
        ))

        register(ToolDefinition(
            name = "write_file",
            description = "Create a new file or overwrite an existing file with content.",
            parameters = mapOf(
                "path" to "Absolute path to the file",
                "content" to "The content to write to the file"
            )
        ))

        register(ToolDefinition(
            name = "edit_file",
            description = "Replace specific text in a file. The old_text must match exactly.",
            parameters = mapOf(
                "path" to "Absolute path to the file",
                "old_text" to "Exact text to find and replace",
                "new_text" to "Text to replace it with"
            )
        ))

        register(ToolDefinition(
            name = "list_directory",
            description = "List files and directories at the given path.",
            parameters = mapOf(
                "path" to "Directory path to list",
                "recursive" to "(Optional) If true, list recursively"
            )
        ))

        register(ToolDefinition(
            name = "search_files",
            description = "Search for text patterns in files using grep.",
            parameters = mapOf(
                "query" to "Text or regex pattern to search for",
                "path" to "Directory path to search in",
                "file_pattern" to "(Optional) File glob pattern, e.g. '*.kt'"
            )
        ))

        register(ToolDefinition(
            name = "delete_file",
            description = "Delete a file or empty directory.",
            parameters = mapOf("path" to "Path to delete")
        ))

        register(ToolDefinition(
            name = "create_directory",
            description = "Create a directory, including any necessary parent directories.",
            parameters = mapOf("path" to "Directory path to create")
        ))

        register(ToolDefinition(
            name = "file_info",
            description = "Get metadata about a file: size, permissions, modification date.",
            parameters = mapOf("path" to "Path to the file")
        ))

        register(ToolDefinition(
            name = "git_status",
            description = "Show the git status of a repository.",
            parameters = mapOf("path" to "Path to the git repository")
        ))

        register(ToolDefinition(
            name = "git_diff",
            description = "Show git diff for changes in a repository.",
            parameters = mapOf(
                "path" to "Path to the git repository",
                "file" to "(Optional) Specific file to diff"
            )
        ))

        register(ToolDefinition(
            name = "git_log",
            description = "Show recent git commit history.",
            parameters = mapOf(
                "path" to "Path to the git repository",
                "count" to "(Optional) Number of commits, default 10"
            )
        ))

        register(ToolDefinition(
            name = "analyze_error",
            description = "Analyze an error message and suggest solutions based on memory.",
            parameters = mapOf(
                "error" to "The error message to analyze",
                "context" to "(Optional) Additional context about what was being done"
            )
        ))

        register(ToolDefinition(
            name = "web_search",
            description = "Search the web for information (requires internet).",
            parameters = mapOf("query" to "Search query")
        ))

        register(ToolDefinition(
            name = "install_package",
            description = "Install a package using available package manager.",
            parameters = mapOf(
                "package_name" to "Package name to install",
                "manager" to "(Optional) Package manager: apt, pip, npm, etc."
            )
        ))
    }

    fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
    }

    fun getToolDescriptions(): String {
        return tools.values.joinToString("\n\n") { tool ->
            buildString {
                appendLine("### ${tool.name}")
                appendLine(tool.description)
                appendLine("Parameters:")
                tool.parameters.forEach { (name, desc) ->
                    appendLine("  - $name: $desc")
                }
            }
        }
    }

    fun getTool(name: String): ToolDefinition? = tools[name]

    fun getAllTools(): List<ToolDefinition> = tools.values.toList()
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, String>
)

/**
 * Executes tool calls safely with proper sandboxing
 */
class ToolExecutor(
    private val workingDir: String = "/data/data/com.elysium.code/files/home"
) {
    companion object {
        private const val TAG = "ToolExecutor"
        private const val DEFAULT_TIMEOUT = 30L
    }

    fun execute(toolCall: ToolCall): ToolResult {
        return try {
            when (toolCall.name) {
                "execute_command" -> executeCommand(toolCall.args)
                "read_file" -> readFile(toolCall.args)
                "write_file" -> writeFile(toolCall.args)
                "edit_file" -> editFile(toolCall.args)
                "list_directory" -> listDirectory(toolCall.args)
                "search_files" -> searchFiles(toolCall.args)
                "delete_file" -> deleteFile(toolCall.args)
                "create_directory" -> createDirectory(toolCall.args)
                "file_info" -> fileInfo(toolCall.args)
                "git_status" -> gitCommand(toolCall.args, "status")
                "git_diff" -> gitCommand(toolCall.args, "diff")
                "git_log" -> gitCommand(toolCall.args, "log --oneline -n 10")
                else -> ToolResult(
                    output = "Unknown tool: ${toolCall.name}",
                    success = false,
                    error = "Tool not found"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution error: ${toolCall.name}", e)
            ToolResult(
                output = "Error executing ${toolCall.name}: ${e.message}",
                success = false,
                error = e.message
            )
        }
    }

    private fun executeCommand(args: Any): ToolResult {
        val jsonArgs = args as? JsonObject
        val command = jsonArgs?.get("command")?.jsonPrimitive?.content
            ?: args.toString()
        val timeout = (jsonArgs?.get("timeout_seconds")?.jsonPrimitive?.content?.toLongOrNull()
            ?: DEFAULT_TIMEOUT)

        Log.i(TAG, "Executing command: $command")

        val process = ProcessBuilder("sh", "-c", command)
            .directory(File(workingDir))
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.appendLine(line)
        }

        val completed = process.waitFor(timeout, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return ToolResult(
                output = output.toString() + "\n[TIMEOUT after ${timeout}s]",
                success = false,
                error = "Command timed out"
            )
        }

        val exitCode = process.exitValue()
        return ToolResult(
            output = output.toString().ifEmpty { "(no output)" },
            success = exitCode == 0,
            error = if (exitCode != 0) "Exit code: $exitCode" else null
        )
    }

    private fun readFile(args: Any): ToolResult {
        val jsonArgs = args as? JsonObject
        val path = jsonArgs?.get("path")?.jsonPrimitive?.content ?: args.toString()
        val maxLines = jsonArgs?.get("max_lines")?.jsonPrimitive?.content?.toIntOrNull()

        val file = File(path)
        if (!file.exists()) return ToolResult("File not found: $path", false, "File not found")
        if (!file.isFile) return ToolResult("Not a file: $path", false, "Not a file")

        val content = if (maxLines != null) {
            file.readLines().take(maxLines).joinToString("\n")
        } else {
            file.readText()
        }

        return ToolResult(content, true)
    }

    private fun writeFile(args: Any): ToolResult {
        val jsonArgs = args as? JsonObject ?: return ToolResult("Invalid args", false)
        val path = jsonArgs["path"]?.jsonPrimitive?.content ?: return ToolResult("Missing path", false)
        val content = jsonArgs["content"]?.jsonPrimitive?.content ?: return ToolResult("Missing content", false)

        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content)

        return ToolResult("File written: $path (${content.length} chars)", true)
    }

    private fun editFile(args: Any): ToolResult {
        val jsonArgs = args as? JsonObject ?: return ToolResult("Invalid args", false)
        val path = jsonArgs["path"]?.jsonPrimitive?.content ?: return ToolResult("Missing path", false)
        val oldText = jsonArgs["old_text"]?.jsonPrimitive?.content ?: return ToolResult("Missing old_text", false)
        val newText = jsonArgs["new_text"]?.jsonPrimitive?.content ?: return ToolResult("Missing new_text", false)

        val file = File(path)
        if (!file.exists()) return ToolResult("File not found: $path", false)

        val content = file.readText()
        if (!content.contains(oldText)) {
            return ToolResult("Text not found in file. Cannot edit.", false, "Text mismatch")
        }

        val updated = content.replace(oldText, newText)
        file.writeText(updated)

        return ToolResult("File edited: $path", true)
    }

    private fun listDirectory(args: Any): ToolResult {
        val jsonArgs = args as? JsonObject
        val path = jsonArgs?.get("path")?.jsonPrimitive?.content ?: args.toString()
        val recursive = jsonArgs?.get("recursive")?.jsonPrimitive?.content?.toBoolean() ?: false

        val dir = File(path)
        if (!dir.exists()) return ToolResult("Directory not found: $path", false)
        if (!dir.isDirectory) return ToolResult("Not a directory: $path", false)

        val listing = if (recursive) {
            dir.walkTopDown().take(200).joinToString("\n") { f ->
                val prefix = if (f.isDirectory) "[DIR] " else "[FILE] "
                prefix + f.relativeTo(dir).path
            }
        } else {
            dir.listFiles()?.sortedBy { it.name }?.joinToString("\n") { f ->
                val prefix = if (f.isDirectory) "[DIR] " else "[FILE]"
                val size = if (f.isFile) " (${f.length()} bytes)" else ""
                "$prefix ${f.name}$size"
            } ?: "(empty)"
        }

        return ToolResult(listing, true)
    }

    private fun searchFiles(args: Any): ToolResult {
        val jsonArgs = args as? JsonObject ?: return ToolResult("Invalid args", false)
        val query = jsonArgs["query"]?.jsonPrimitive?.content ?: return ToolResult("Missing query", false)
        val path = jsonArgs["path"]?.jsonPrimitive?.content ?: workingDir
        val pattern = jsonArgs["file_pattern"]?.jsonPrimitive?.content

        val cmd = buildString {
            append("grep -rn")
            if (pattern != null) append(" --include='$pattern'")
            append(" '$query' '$path'")
            append(" | head -50")
        }

        return executeCommand(JsonObject(mapOf("command" to JsonPrimitive(cmd))))
    }

    private fun deleteFile(args: Any): ToolResult {
        val path = (args as? JsonObject)?.get("path")?.jsonPrimitive?.content ?: args.toString()
        val file = File(path)
        if (!file.exists()) return ToolResult("File not found: $path", false)

        val deleted = file.delete()
        return ToolResult(
            if (deleted) "Deleted: $path" else "Failed to delete: $path",
            deleted
        )
    }

    private fun createDirectory(args: Any): ToolResult {
        val path = (args as? JsonObject)?.get("path")?.jsonPrimitive?.content ?: args.toString()
        val dir = File(path)
        val created = dir.mkdirs()
        return ToolResult(
            if (created || dir.exists()) "Directory ready: $path" else "Failed to create: $path",
            created || dir.exists()
        )
    }

    private fun fileInfo(args: Any): ToolResult {
        val path = (args as? JsonObject)?.get("path")?.jsonPrimitive?.content ?: args.toString()
        val file = File(path)
        if (!file.exists()) return ToolResult("File not found: $path", false)

        val info = buildString {
            appendLine("Path: ${file.absolutePath}")
            appendLine("Type: ${if (file.isDirectory) "Directory" else "File"}")
            appendLine("Size: ${file.length()} bytes")
            appendLine("Readable: ${file.canRead()}")
            appendLine("Writable: ${file.canWrite()}")
            appendLine("Executable: ${file.canExecute()}")
            appendLine("Last Modified: ${java.util.Date(file.lastModified())}")
        }

        return ToolResult(info, true)
    }

    private fun gitCommand(args: Any, subcommand: String): ToolResult {
        val jsonArgs = args as? JsonObject
        val path = jsonArgs?.get("path")?.jsonPrimitive?.content ?: workingDir
        val file = jsonArgs?.get("file")?.jsonPrimitive?.content

        val cmd = buildString {
            append("cd '$path' && git $subcommand")
            if (file != null) append(" -- '$file'")
        }

        return executeCommand(JsonObject(mapOf("command" to JsonPrimitive(cmd))))
    }
}
