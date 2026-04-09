package com.elysium.code.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — CodeIntelligence
 * ═══════════════════════════════════════════════════════════════
 *
 * Local code analysis engine: language detection, structural
 * analysis, complexity metrics, project scanning, code smells.
 * All offline — no network required.
 */
class CodeIntelligence {

    companion object {
        private const val TAG = "CodeIntelligence"
    }

    // ═══ LANGUAGE DETECTION ═══

    fun detectLanguage(filename: String, content: String = ""): LanguageInfo {
        val ext = filename.substringAfterLast('.', "").lowercase()
        val id = extensionMap[ext] ?: detectFromContent(content)
        return LanguageInfo(
            id = id,
            name = languageNames[id] ?: id,
            extension = ext,
            commentPrefix = commentPrefixes[id] ?: "//"
        )
    }

    private fun detectFromContent(content: String): String {
        val first = content.lines().firstOrNull()?.trim() ?: ""
        return when {
            first.startsWith("#!/usr/bin/env python") -> "python"
            first.startsWith("#!/usr/bin/env node") -> "javascript"
            first.startsWith("#!/bin/bash") -> "shell"
            first.startsWith("<?php") -> "php"
            content.contains("fun ") && content.contains("val ") -> "kotlin"
            content.contains("def ") && content.contains(":") -> "python"
            else -> "plaintext"
        }
    }

    // ═══ STRUCTURAL ANALYSIS ═══

    fun analyzeStructure(code: String, language: String): CodeStructure {
        val lines = code.lines()
        val imports = extractImports(code, language)
        val classes = extractClasses(code, language, lines)
        val functions = extractFunctions(code, language, lines)

        return CodeStructure(
            imports = imports,
            symbols = classes + functions,
            lineCount = lines.size,
            blankLineCount = lines.count { it.isBlank() },
            commentLineCount = lines.count { isComment(it, language) }
        )
    }

    private fun extractImports(code: String, lang: String): List<String> {
        val pattern = when (lang) {
            "kotlin", "java" -> Regex("""^import\s+(\S+);?""", RegexOption.MULTILINE)
            "python" -> Regex("""^(?:import|from)\s+(\S+)""", RegexOption.MULTILINE)
            "javascript", "typescript" -> Regex("""(?:import|require)\s*\(?['"]([\w./@-]+)""")
            "go" -> Regex(""""([\w./-]+)"""")
            "rust" -> Regex("""^use\s+(\S+);""", RegexOption.MULTILINE)
            "swift", "dart" -> Regex("""^import\s+[']?(\S+)[']?;?""", RegexOption.MULTILINE)
            else -> return emptyList()
        }
        return pattern.findAll(code).map { it.groupValues[1] }.toList()
    }

    private fun extractClasses(code: String, lang: String, lines: List<String>): List<SymbolInfo> {
        val pattern = when (lang) {
            "kotlin" -> Regex("""(?:class|object|interface|enum class|sealed class|data class)\s+(\w+)""")
            "java", "csharp" -> Regex("""(?:class|interface|enum)\s+(\w+)""")
            "python" -> Regex("""^class\s+(\w+)""", RegexOption.MULTILINE)
            "javascript", "typescript" -> Regex("""class\s+(\w+)""")
            "go" -> Regex("""type\s+(\w+)\s+struct""")
            "rust" -> Regex("""(?:pub\s+)?struct\s+(\w+)""")
            "swift" -> Regex("""(?:class|struct|protocol|enum)\s+(\w+)""")
            "dart" -> Regex("""(?:abstract\s+)?class\s+(\w+)""")
            else -> return emptyList()
        }
        return pattern.findAll(code).map { m ->
            val ln = lines.indexOfFirst { it.contains(m.value) } + 1
            SymbolInfo(m.groupValues[1], SymbolType.CLASS, ln)
        }.toList()
    }

    private fun extractFunctions(code: String, lang: String, lines: List<String>): List<SymbolInfo> {
        val pattern = when (lang) {
            "kotlin" -> Regex("""(?:fun|suspend fun|private fun|override fun)\s+(\w+)\s*\(""")
            "java" -> Regex("""(?:public|private|protected|static|\s)+[\w<>\[\]]+\s+(\w+)\s*\(""")
            "python" -> Regex("""^(?:def|async def)\s+(\w+)\s*\(""", RegexOption.MULTILINE)
            "javascript", "typescript" -> Regex("""(?:function|async function)\s+(\w+)""")
            "go" -> Regex("""^func\s+(?:\(\s*\w+\s+\*?\w+\s*\)\s+)?(\w+)\s*\(""", RegexOption.MULTILINE)
            "rust" -> Regex("""(?:pub\s+)?(?:async\s+)?fn\s+(\w+)""")
            "swift" -> Regex("""func\s+(\w+)\s*\(""")
            "dart" -> Regex("""(?:Future|void|String|int|Widget)\s+(\w+)\s*\(""")
            else -> return emptyList()
        }
        return pattern.findAll(code).map { m ->
            val ln = lines.indexOfFirst { it.contains(m.value) } + 1
            SymbolInfo(m.groupValues[1], SymbolType.FUNCTION, ln)
        }.toList()
    }

    private fun isComment(line: String, lang: String): Boolean {
        val t = line.trim()
        return when (lang) {
            "python", "shell", "ruby" -> t.startsWith("#")
            "sql", "lua", "haskell" -> t.startsWith("--")
            "html", "xml" -> t.startsWith("<!--")
            else -> t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
        }
    }

    // ═══ COMPLEXITY ═══

    fun calculateComplexity(code: String): ComplexityResult {
        val decisions = listOf("\\bif\\b", "\\bfor\\b", "\\bwhile\\b", "\\bwhen\\b",
            "\\bcatch\\b", "\\bcase\\b", "&&", "\\|\\|")
        val complexity = 1 + decisions.sumOf { Regex(it).findAll(code).count() }
        val codeLines = code.lines().count { it.isNotBlank() && !it.trim().startsWith("//") && !it.trim().startsWith("#") }
        var nesting = 0; var maxNesting = 0
        code.forEach { c -> if (c == '{') { nesting++; maxNesting = maxOf(maxNesting, nesting) }; if (c == '}') nesting = (nesting - 1).coerceAtLeast(0) }
        val rating = when { complexity <= 5 -> ComplexityRating.LOW; complexity <= 10 -> ComplexityRating.MODERATE; complexity <= 20 -> ComplexityRating.HIGH; else -> ComplexityRating.CRITICAL }
        return ComplexityResult(complexity, (complexity * 1.2 + maxNesting * 3).toInt(), codeLines, maxNesting, rating)
    }

    // ═══ PROJECT ANALYSIS ═══

    suspend fun analyzeProject(projectPath: String): ProjectAnalysis = withContext(Dispatchers.IO) {
        val root = File(projectPath)
        if (!root.exists()) return@withContext ProjectAnalysis(error = "Not found: $projectPath")
        val langCounts = mutableMapOf<String, Int>()
        val files = mutableListOf<FileAnalysis>()
        var totalLines = 0; var totalSize = 0L
        val excludes = setOf("node_modules", ".git", "build", ".gradle", "__pycache__", ".idea")
        root.walkTopDown().filter { it.isFile }.filter { f -> excludes.none { f.path.contains("/$it/") } }.take(500).forEach { file ->
            val lang = detectLanguage(file.name); langCounts[lang.id] = (langCounts[lang.id] ?: 0) + 1; totalSize += file.length()
            try { val lines = file.readLines().size; totalLines += lines; files.add(FileAnalysis(file.relativeTo(root).path, lang.id, lines, file.length())) } catch (_: Exception) {}
        }
        val type = when {
            File(root, "build.gradle.kts").exists() -> "Android/Kotlin (Gradle)"
            File(root, "package.json").exists() -> "Node.js"
            File(root, "Cargo.toml").exists() -> "Rust"
            File(root, "go.mod").exists() -> "Go"
            File(root, "requirements.txt").exists() -> "Python"
            File(root, "pubspec.yaml").exists() -> "Flutter/Dart"
            else -> "Unknown"
        }
        ProjectAnalysis(projectPath, type, langCounts.maxByOrNull { it.value }?.key ?: "unknown",
            files.size, totalLines, totalSize, langCounts, files.sortedByDescending { it.lines },
            File(root, ".git").exists(), root.walkTopDown().any { it.name.contains("test", true) && it.isDirectory },
            File(root, ".github").exists(), File(root, "Dockerfile").exists())
    }

    // ═══ CODE SMELLS ═══

    fun detectCodeSmells(code: String): List<CodeSmell> {
        val smells = mutableListOf<CodeSmell>()
        val lines = code.lines()
        lines.forEachIndexed { i, l ->
            if (l.length > 120 && !l.trim().startsWith("//")) smells.add(CodeSmell(SmellType.LONG_LINE, "Line ${i+1} is ${l.length} chars", i+1, Severity.LOW))
            if (l.contains("TODO", true) || l.contains("FIXME", true)) smells.add(CodeSmell(SmellType.TODO_COMMENT, l.trim().take(80), i+1, Severity.INFO))
        }
        Regex("""catch\s*\([^)]*\)\s*\{\s*\}""").findAll(code).forEach { m ->
            smells.add(CodeSmell(SmellType.EMPTY_CATCH, "Empty catch — errors silently swallowed", code.substring(0, m.range.first).count { it == '\n' } + 1, Severity.HIGH))
        }
        return smells
    }

    // ═══ STATIC DATA ═══
    private val extensionMap = mapOf("kt" to "kotlin", "kts" to "kotlin", "java" to "java", "py" to "python", "js" to "javascript", "jsx" to "javascript", "ts" to "typescript", "tsx" to "typescript", "c" to "c", "h" to "c", "cpp" to "cpp", "cs" to "csharp", "go" to "go", "rs" to "rust", "rb" to "ruby", "php" to "php", "swift" to "swift", "dart" to "dart", "lua" to "lua", "r" to "r", "scala" to "scala", "html" to "html", "css" to "css", "scss" to "scss", "xml" to "xml", "json" to "json", "yaml" to "yaml", "yml" to "yaml", "toml" to "toml", "md" to "markdown", "sh" to "shell", "sql" to "sql", "graphql" to "graphql", "proto" to "protobuf", "vue" to "vue", "svelte" to "svelte", "gradle" to "groovy")
    private val languageNames = mapOf("kotlin" to "Kotlin", "java" to "Java", "python" to "Python", "javascript" to "JavaScript", "typescript" to "TypeScript", "c" to "C", "cpp" to "C++", "csharp" to "C#", "go" to "Go", "rust" to "Rust", "ruby" to "Ruby", "php" to "PHP", "swift" to "Swift", "dart" to "Dart", "shell" to "Shell", "sql" to "SQL")
    private val commentPrefixes = mapOf("kotlin" to "//", "java" to "//", "python" to "#", "javascript" to "//", "typescript" to "//", "c" to "//", "cpp" to "//", "go" to "//", "rust" to "//", "ruby" to "#", "swift" to "//", "dart" to "//", "shell" to "#", "sql" to "--", "lua" to "--")
}

// ═══ DATA CLASSES ═══
data class LanguageInfo(val id: String, val name: String, val extension: String, val commentPrefix: String = "//")
data class CodeStructure(val packageName: String = "", val imports: List<String> = emptyList(), val symbols: List<SymbolInfo> = emptyList(), val lineCount: Int = 0, val blankLineCount: Int = 0, val commentLineCount: Int = 0) { val codeLineCount get() = lineCount - blankLineCount - commentLineCount; val classes get() = symbols.filter { it.type == SymbolType.CLASS }; val functions get() = symbols.filter { it.type == SymbolType.FUNCTION } }
data class SymbolInfo(val name: String, val type: SymbolType, val line: Int)
enum class SymbolType { CLASS, FUNCTION, PROPERTY, INTERFACE, ENUM, CONSTANT }
data class ComplexityResult(val cyclomatic: Int, val cognitive: Int, val linesOfCode: Int, val maxNesting: Int, val rating: ComplexityRating)
enum class ComplexityRating { LOW, MODERATE, HIGH, CRITICAL }
data class ProjectAnalysis(val path: String = "", val projectType: String = "", val primaryLanguage: String = "", val totalFiles: Int = 0, val totalLines: Int = 0, val totalSizeBytes: Long = 0, val languageBreakdown: Map<String, Int> = emptyMap(), val files: List<FileAnalysis> = emptyList(), val hasGit: Boolean = false, val hasTests: Boolean = false, val hasCi: Boolean = false, val hasDocker: Boolean = false, val error: String? = null) {
    fun toSummary() = buildString { if (error != null) { append("Error: $error"); return@buildString }; appendLine("📁 $path"); appendLine("🏗️ $projectType | 💻 $primaryLanguage"); appendLine("📄 $totalFiles files | 📏 $totalLines lines | 💾 ${totalSizeBytes/1024}KB"); appendLine("🔧 Git:${if(hasGit)"✅"else"❌"} Tests:${if(hasTests)"✅"else"❌"} CI:${if(hasCi)"✅"else"❌"} Docker:${if(hasDocker)"✅"else"❌"}") }
}
data class FileAnalysis(val path: String, val language: String, val lines: Int, val sizeBytes: Long)
data class CodeSmell(val type: SmellType, val message: String, val line: Int, val severity: Severity)
enum class SmellType { LONG_METHOD, LONG_LINE, MAGIC_NUMBER, TODO_COMMENT, EMPTY_CATCH, DUPLICATE_CODE, GOD_CLASS }
enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
