package com.elysium.code.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.elysium.code.ui.theme.ElysiumTheme

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — Editor Screen
 * ═══════════════════════════════════════════════════════════════
 *
 * Professional code editor with:
 * - Syntax highlighting for 50+ languages
 * - Line numbers with current line highlight
 * - File tree sidebar
 * - Multi-tab editing
 * - AI inline suggestions
 * - Find & replace
 * - Git status indicators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    var showFileTree by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    val openTabs = remember {
        mutableStateListOf(
            EditorTab("main.py", "python", samplePythonCode()),
            EditorTab("app.kt", "kotlin", sampleKotlinCode())
        )
    }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ElysiumTheme.colors.editorBg)
    ) {
        // ═══ Toolbar ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElysiumTheme.colors.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File tree toggle
            IconButton(onClick = { showFileTree = !showFileTree }, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (showFileTree) Icons.Filled.FolderOpen else Icons.Outlined.Folder,
                    "Files",
                    tint = ElysiumTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Tab bar
            ScrollableTabRow(
                selectedTabIndex = activeTabIndex.coerceIn(0, openTabs.size - 1),
                containerColor = Color.Transparent,
                contentColor = ElysiumTheme.colors.textPrimary,
                edgePadding = 0.dp,
                divider = {},
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
            ) {
                openTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTabIndex == index,
                        onClick = { activeTabIndex = index },
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                getFileIcon(tab.language),
                                null,
                                tint = getLanguageColor(tab.language),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                tab.filename,
                                style = ElysiumTheme.typography.codeSmall,
                                color = if (activeTabIndex == index) ElysiumTheme.colors.textPrimary
                                        else ElysiumTheme.colors.textTertiary
                            )
                            if (tab.isModified) {
                                Spacer(Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ElysiumTheme.colors.warning)
                                )
                            }
                        }
                    }
                }
            }

            // Search
            IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Search, "Search", tint = ElysiumTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            }

            // AI assist
            IconButton(onClick = { /* AI assist */ }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.AutoAwesome, "AI", tint = ElysiumTheme.colors.primary, modifier = Modifier.size(18.dp))
            }

            // Save
            IconButton(onClick = { /* Save */ }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Save, "Save", tint = ElysiumTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        // ═══ Search bar ═══
        AnimatedVisibility(visible = showSearch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElysiumTheme.colors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = ElysiumTheme.typography.codeMedium.copy(color = ElysiumTheme.colors.textPrimary),
                    cursorBrush = SolidColor(ElysiumTheme.colors.primary),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .background(ElysiumTheme.colors.surfaceCard, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text("Find...", style = ElysiumTheme.typography.codeMedium, color = ElysiumTheme.colors.textTertiary)
                            }
                            innerTextField()
                        }
                    }
                )
                IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ArrowUpward, "Previous", tint = ElysiumTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ArrowDownward, "Next", tint = ElysiumTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ═══ Main Content ═══
        Row(modifier = Modifier.weight(1f)) {
            // File Tree Sidebar
            if (showFileTree) {
                FileTreeSidebar(modifier = Modifier.width(200.dp))
            }

            // Divider
            if (showFileTree) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(ElysiumTheme.colors.border)
                )
            }

            // Code Area
            if (openTabs.isNotEmpty() && activeTabIndex < openTabs.size) {
                CodeArea(
                    code = openTabs[activeTabIndex].content,
                    language = openTabs[activeTabIndex].language,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Code, "No file",
                            tint = ElysiumTheme.colors.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Open a file to start editing",
                            style = ElysiumTheme.typography.bodyMedium,
                            color = ElysiumTheme.colors.textTertiary
                        )
                    }
                }
            }
        }

        // ═══ Status Bar ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElysiumTheme.colors.surface)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (openTabs.isNotEmpty() && activeTabIndex < openTabs.size) {
                Text(
                    openTabs[activeTabIndex].language.uppercase(),
                    style = ElysiumTheme.typography.labelSmall,
                    color = getLanguageColor(openTabs[activeTabIndex].language)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "UTF-8",
                    style = ElysiumTheme.typography.labelSmall,
                    color = ElysiumTheme.colors.textTertiary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Ln 1, Col 1",
                    style = ElysiumTheme.typography.labelSmall,
                    color = ElysiumTheme.colors.textTertiary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Spaces: 4",
                    style = ElysiumTheme.typography.labelSmall,
                    color = ElysiumTheme.colors.textTertiary
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Editor Components
// ═══════════════════════════════════════════════════════════════

data class EditorTab(
    val filename: String,
    val language: String,
    val content: String,
    val isModified: Boolean = false,
    val path: String = ""
)

@Composable
fun FileTreeSidebar(modifier: Modifier = Modifier) {
    val expandedDirs = remember { mutableStateListOf("project", "src") }

    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .background(ElysiumTheme.colors.surface)
            .padding(4.dp)
    ) {
        // Mock file tree
        item { FileTreeHeader("PROJECT") }
        item { FileTreeItem("src", isDir = true, level = 0, expanded = true) }
        item { FileTreeItem("main.py", isDir = false, level = 1, language = "python") }
        item { FileTreeItem("app.kt", isDir = false, level = 1, language = "kotlin") }
        item { FileTreeItem("utils.ts", isDir = false, level = 1, language = "typescript") }
        item { FileTreeItem("styles.css", isDir = false, level = 1, language = "css") }
        item { FileTreeItem("tests", isDir = true, level = 0, expanded = false) }
        item { FileTreeItem("docs", isDir = true, level = 0, expanded = false) }
        item { FileTreeItem("README.md", isDir = false, level = 0, language = "markdown") }
        item { FileTreeItem("package.json", isDir = false, level = 0, language = "json") }
        item { FileTreeItem(".gitignore", isDir = false, level = 0, language = "git") }
    }
}

@Composable
fun FileTreeHeader(text: String) {
    Text(
        text = text,
        style = ElysiumTheme.typography.labelSmall,
        color = ElysiumTheme.colors.textTertiary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun FileTreeItem(
    name: String,
    isDir: Boolean,
    level: Int,
    expanded: Boolean = false,
    language: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(start = (8 + level * 16).dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDir) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronRight,
                null,
                tint = ElysiumTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                null,
                tint = ElysiumTheme.colors.warning,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Spacer(Modifier.width(16.dp))
            Icon(
                getFileIcon(language),
                null,
                tint = getLanguageColor(language),
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            name,
            style = ElysiumTheme.typography.codeSmall,
            color = ElysiumTheme.colors.textSecondary
        )
    }
}

@Composable
fun CodeArea(code: String, language: String, modifier: Modifier = Modifier) {
    val lines = code.lines()
    val lineNumberWidth = 40.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ElysiumTheme.colors.editorBg)
    ) {
        itemsIndexed(lines) { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index == 0) ElysiumTheme.colors.editorCurrentLine
                        else Color.Transparent
                    )
            ) {
                // Line number
                Box(
                    modifier = Modifier
                        .width(lineNumberWidth)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "${index + 1}",
                        style = ElysiumTheme.typography.codeSmall,
                        color = if (index == 0) ElysiumTheme.colors.textSecondary
                                else ElysiumTheme.colors.editorLineNumber
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(ElysiumTheme.colors.border.copy(alpha = 0.3f))
                )

                // Code content with syntax highlighting
                Text(
                    text = highlightSyntax(line, language),
                    style = ElysiumTheme.typography.codeMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Syntax Highlighting
// ═══════════════════════════════════════════════════════════════

@Composable
fun highlightSyntax(line: String, language: String) = buildAnnotatedString {
    val keywords = when (language) {
        "python" -> setOf("def", "class", "import", "from", "return", "if", "else", "elif", "for", "while", "try", "except", "with", "as", "in", "not", "and", "or", "True", "False", "None", "self", "async", "await", "yield", "lambda", "pass", "break", "continue", "raise")
        "kotlin" -> setOf("fun", "class", "val", "var", "if", "else", "when", "for", "while", "return", "import", "package", "object", "data", "sealed", "interface", "abstract", "override", "private", "public", "internal", "suspend", "coroutine", "companion", "init", "by", "lazy", "null", "true", "false", "is", "as", "in", "this", "super")
        "javascript", "typescript" -> setOf("function", "const", "let", "var", "if", "else", "for", "while", "return", "import", "export", "from", "class", "async", "await", "try", "catch", "throw", "new", "this", "true", "false", "null", "undefined", "typeof", "instanceof")
        else -> emptySet()
    }

    val commentColor = Color(0xFF6A737D)
    val keywordColor = Color(0xFFFF79C6)
    val stringColor = Color(0xFFF1FA8C)
    val numberColor = Color(0xFFBD93F9)
    val funcColor = Color(0xFF50FA7B)
    val defaultColor = ElysiumTheme.colors.textPrimary

    // Simple token-based highlighting
    if (line.trimStart().startsWith("#") || line.trimStart().startsWith("//")) {
        withStyle(SpanStyle(color = commentColor)) { append(line) }
        return@buildAnnotatedString
    }

    val tokens = line.split(Regex("(?<=\\s)|(?=\\s)|(?<=[(){}\\[\\].,;:=+\\-*/<>!&|])|(?=[(){}\\[\\].,;:=+\\-*/<>!&|])"))
    tokens.forEach { token ->
        val trimmed = token.trim()
        when {
            trimmed in keywords -> withStyle(SpanStyle(color = keywordColor)) { append(token) }
            trimmed.startsWith("\"") || trimmed.startsWith("'") || trimmed.startsWith("`") ->
                withStyle(SpanStyle(color = stringColor)) { append(token) }
            trimmed.toDoubleOrNull() != null -> withStyle(SpanStyle(color = numberColor)) { append(token) }
            trimmed.endsWith("(") -> withStyle(SpanStyle(color = funcColor)) { append(token) }
            else -> withStyle(SpanStyle(color = defaultColor)) { append(token) }
        }
    }
}

fun getFileIcon(language: String) = when (language) {
    "python" -> Icons.Outlined.Code
    "kotlin", "java" -> Icons.Outlined.PhoneAndroid
    "javascript", "typescript" -> Icons.Outlined.Javascript
    "css" -> Icons.Outlined.Palette
    "markdown" -> Icons.Outlined.Description
    "json" -> Icons.Outlined.DataObject
    "git" -> Icons.Outlined.AccountTree
    else -> Icons.Outlined.InsertDriveFile
}

fun getLanguageColor(language: String) = when (language) {
    "python" -> Color(0xFF3572A5)
    "kotlin" -> Color(0xFF7F52FF)
    "java" -> Color(0xFFB07219)
    "javascript" -> Color(0xFFF7DF1E)
    "typescript" -> Color(0xFF3178C6)
    "css" -> Color(0xFF563D7C)
    "markdown" -> Color(0xFF083FA1)
    "json" -> Color(0xFF292929)
    "git" -> Color(0xFFF05032)
    else -> Color(0xFF9898A8)
}

// Sample code for demo
fun samplePythonCode() = """import asyncio
from dataclasses import dataclass
from typing import List, Optional

@dataclass
class Agent:
    name: str
    model: str
    tools: List[str]
    memory: Optional[dict] = None

    async def process(self, query: str) -> str:
        \"\"\"Process a user query through the agent pipeline.\"\"\"
        # Recall relevant memories
        context = self.recall_memories(query)
        
        # Build prompt with context
        prompt = self.build_prompt(query, context)
        
        # Generate response
        response = await self.generate(prompt)
        
        # Learn from this interaction
        self.record_memory(query, response)
        
        return response

    def recall_memories(self, query: str) -> dict:
        if self.memory is None:
            return {}
        return {k: v for k, v in self.memory.items() 
                if any(term in k for term in query.split())}

    async def generate(self, prompt: str) -> str:
        # Local inference with Gemma 4 E4B
        result = await asyncio.to_thread(
            self.model.infer, prompt
        )
        return result

# Initialize agent
agent = Agent(
    name="Elysium",
    model="gemma-4-e4b",
    tools=["terminal", "editor", "search"],
    memory={}
)
""".trimIndent()

fun sampleKotlinCode() = """package com.elysium.code

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Elysium Code — Main Application Entry
 * Local AI coding assistant powered by Gemma 4 E4B
 */
class ElysiumAgent(
    private val engine: LlamaEngine,
    private val memory: MemoryEngine
) {
    suspend fun processQuery(query: String): Flow<String> = flow {
        // 1. Search memories for relevant context
        val memories = memory.recall(query)
        emit("[Recalled ${'$'}{memories.size} memories]\n")
        
        // 2. Build system prompt
        val systemPrompt = buildSystemPrompt(memories)
        
        // 3. Stream response tokens
        engine.generate(systemPrompt + query).collect { token ->
            emit(token)
        }
        
        // 4. Record what we learned
        memory.recordKnowledge(
            title = query.take(100),
            content = "Task completed successfully",
            category = KnowledgeCategory.CODING_PATTERN
        )
    }
    
    private fun buildSystemPrompt(
        memories: List<MemoryResult>
    ): String = buildString {
        appendLine("You are Elysium, running locally.")
        memories.forEach { mem ->
            appendLine("[Memory] ${'$'}{mem.title}")
        }
    }
}
""".trimIndent()
