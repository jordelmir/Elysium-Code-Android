package com.elysium.code.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.elysium.code.editor.EditorViewModel
import com.elysium.code.ui.theme.ElysiumTheme
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════
 * ENHANCED Code Editor - VS Code Style
 * ═══════════════════════════════════════════════════════════════
 *
 * Features:
 * - File explorer with CRUD operations
 * - Multi-tab editing
 * - Real file save/load
 * - Keyboard shortcuts
 * - Line numbers
 * - Search functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedEditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Create ViewModel
    val viewModel = remember {
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application)
            .create(EditorViewModel::class.java)
    }

    val fileTree by viewModel.fileTree.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabIndex by viewModel.activeTabIndex.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isModified by viewModel.isModified.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showFileTree by remember { mutableStateOf(true) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var isFolder by remember { mutableStateOf(false) }
    var editorTextFieldValue by remember { mutableStateOf(TextFieldValue(fileContent)) }

    LaunchedEffect(fileContent) {
        editorTextFieldValue = TextFieldValue(fileContent, TextRange(fileContent.length))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ElysiumTheme.colors.background)
    ) {
        // ═══ Header ═══
        Surface(
            color = ElysiumTheme.colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF7C3AED).copy(alpha = 0.4f),
                                Color(0xFF00D4FF).copy(alpha = 0.4f),
                                Color(0xFF7C3AED).copy(alpha = 0.4f)
                            )
                        ),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2f
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showFileTree = !showFileTree },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (showFileTree) Icons.Filled.Folder else Icons.Filled.FolderOpen,
                            contentDescription = "Toggle file tree",
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        "📝 Elysium Editor",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF7C3AED)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    if (isModified) {
                        Icon(
                            Icons.Filled.Circle,
                            contentDescription = "Modified",
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(8.dp)
                        )
                    }

                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00D4FF),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // ═══ File Explorer ═══
            if (showFileTree) {
                Surface(
                    color = ElysiumTheme.colors.surface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .border(1.dp, Color(0xFF7C3AED).copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        // Directory header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "📁 Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { showNewFileDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "New",
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Divider(color = Color(0xFF7C3AED).copy(alpha = 0.1f))

                        // File list
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(fileTree) { node ->
                                FileTreeItem(
                                    node = node,
                                    onOpen = { viewModel.openFile(node.path) },
                                    onNavigate = { if (node.isDirectory) viewModel.navigateTo(node.path) },
                                    onDelete = { viewModel.deleteFile(node.path) }
                                )
                            }
                        }

                        // Current path
                        Text(
                            currentDirectory.substringAfterLast('/'),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00D4FF).copy(alpha = 0.5f),
                            fontSize = 9.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }

            // ═══ Editor Area ═══
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Tab bar
                if (openTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ElysiumTheme.colors.surface.copy(alpha = 0.3f))
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        openTabs.forEachIndexed { index, tab ->
                            Surface(
                                color = if (index == activeTabIndex)
                                    Color(0xFF7C3AED).copy(alpha = 0.2f)
                                else
                                    Color(0xFF7C3AED).copy(alpha = 0.05f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectTab(index) },
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFF7C3AED).copy(alpha = if (index == activeTabIndex) 0.5f else 0.2f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text(
                                        tab.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF7C3AED),
                                        fontSize = 12.sp
                                    )

                                    if (tab.isModified) {
                                        Icon(
                                            Icons.Filled.Circle,
                                            contentDescription = "Modified",
                                            tint = Color(0xFFFF6B00),
                                            modifier = Modifier.size(6.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.closeTab(index) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFF7C3AED),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = Color(0xFF7C3AED).copy(alpha = 0.1f))
                }

                // Editor
                if (openTabs.isNotEmpty()) {
                    BasicTextField(
                        value = editorTextFieldValue,
                        onValueChange = {
                            editorTextFieldValue = it
                            viewModel.updateContent(it.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(0.dp))
                            .background(Color(0xFF0A0E27))
                            .border(1.dp, Color(0xFF7C3AED).copy(alpha = 0.2f))
                            .padding(12.dp)
                            .onKeyEvent { keyEvent ->
                                when {
                                    keyEvent.isCtrlPressed && keyEvent.key == Key.S && keyEvent.type == KeyEventType.KeyDown -> {
                                        scope.launch { viewModel.saveFile() }
                                        true
                                    }
                                    keyEvent.isCtrlPressed && keyEvent.key == Key.N && keyEvent.type == KeyEventType.KeyDown -> {
                                        showNewFileDialog = true
                                        true
                                    }
                                    else -> false
                                }
                            },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFF7C3AED),
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(Color(0xFF7C3AED)),
                        decorationBox = { innerTextField ->
                            innerTextField()
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0A0E27)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Select a file to edit",
                            color = Color(0xFF7C3AED).copy(alpha = 0.3f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // ═══ Status Bar ═══
        Surface(
            color = ElysiumTheme.colors.surface.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF7C3AED).copy(alpha = 0.4f),
                                Color(0xFF00D4FF).copy(alpha = 0.4f),
                                Color(0xFF7C3AED).copy(alpha = 0.4f)
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5f
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (openTabs.isNotEmpty()) {
                        Text(
                            "Line: ${editorTextFieldValue.selection.start}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00D4FF),
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        "💾 Ctrl+S to save",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7C3AED).copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }

                IconButton(
                    onClick = { scope.launch { viewModel.saveFile() } },
                    enabled = isModified,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "Save",
                        tint = if (isModified) Color(0xFF00FF88) else Color(0xFF7C3AED).copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // New file dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(if (isFolder) "New Folder" else "New File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { isFolder = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isFolder) Color(0xFF7C3AED) else Color(0xFF7C3AED).copy(alpha = 0.3f)
                            )
                        ) {
                            Text("File")
                        }

                        Button(
                            onClick = { isFolder = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFolder) Color(0xFF7C3AED) else Color(0xFF7C3AED).copy(alpha = 0.3f)
                            )
                        ) {
                            Text("Folder")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemName.isNotEmpty()) {
                            if (isFolder) {
                                viewModel.createNewFolder(newItemName)
                            } else {
                                viewModel.createNewFile(newItemName)
                            }
                            newItemName = ""
                            showNewFileDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FileTreeItem(
    node: com.elysium.code.editor.FileTreeNode,
    onOpen: () -> Unit,
    onNavigate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable {
                if (node.isDirectory) onNavigate() else onOpen()
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                if (node.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (node.isDirectory) Color(0xFF00D4FF) else Color(0xFF7C3AED),
                modifier = Modifier.size(14.dp)
            )

            Text(
                node.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7C3AED),
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Delete",
                tint = Color(0xFFFF4444),
                modifier = Modifier.size(12.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${node.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
