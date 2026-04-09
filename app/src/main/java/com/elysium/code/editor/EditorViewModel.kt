package com.elysium.code.editor

import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════
 * Editor ViewModel - File and Tab Management
 * ═══════════════════════════════════════════════════════════════
 */
class EditorViewModel(application: android.app.Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "EditorViewModel"
    }

    private val fileManager = FileManager(application)

    // ═══ State ═══
    private val _fileTree = MutableStateFlow<List<FileTreeNode>>(emptyList())
    val fileTree: StateFlow<List<FileTreeNode>> = _fileTree.asStateFlow()

    private val _currentDirectory = MutableStateFlow<String>(application.filesDir.absolutePath)
    val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

    private val _openTabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val openTabs: StateFlow<List<EditorTab>> = _openTabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent.asStateFlow()

    private val _isModified = MutableStateFlow(false)
    val isModified: StateFlow<Boolean> = _isModified.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        refreshFileTree()
    }

    /**
     * Refresh file tree
     */
    fun refreshFileTree() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = fileManager.listDirectory(_currentDirectory.value)
            _fileTree.value = files
            Log.d(TAG, "File tree refreshed: ${files.size} items")
        }
    }

    /**
     * Navigate to directory
     */
    fun navigateTo(path: String) {
        if (fileManager.isDirectory(path)) {
            _currentDirectory.value = path
            refreshFileTree()
            _statusMessage.value = "Navigated to: ${path.substringAfterLast('/')}"
        }
    }

    /**
     * Open file in editor
     */
    fun openFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = fileManager.readFile(path)
                _fileContent.value = content
                _isModified.value = false

                // Add to tabs if not already open
                val existingTab = _openTabs.value.find { it.path == path }
                if (existingTab == null) {
                    val tab = EditorTab(
                        name = path.substringAfterLast('/'),
                        path = path,
                        content = content,
                        language = getLanguage(path)
                    )
                    _openTabs.value = _openTabs.value + tab
                    _activeTabIndex.value = _openTabs.value.size - 1
                } else {
                    _activeTabIndex.value = _openTabs.value.indexOf(existingTab)
                }

                _statusMessage.value = "Opened: ${path.substringAfterLast('/')}"
                Log.d(TAG, "File opened: $path")
            } catch (e: Exception) {
                _statusMessage.value = "Error opening file: ${e.message}"
                Log.e(TAG, "Error opening file: $path", e)
            }
        }
    }

    /**
     * Save current file
     */
    fun saveFile() {
        val activeTab = _openTabs.value.getOrNull(_activeTabIndex.value)
        if (activeTab != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    fileManager.writeFile(activeTab.path, _fileContent.value)
                    _isModified.value = false

                    // Update tab
                    val updatedTabs = _openTabs.value.toMutableList()
                    updatedTabs[_activeTabIndex.value] = activeTab.copy(
                        content = _fileContent.value,
                        isModified = false
                    )
                    _openTabs.value = updatedTabs

                    _statusMessage.value = "Saved: ${activeTab.name}"
                    Log.d(TAG, "File saved: ${activeTab.path}")
                } catch (e: Exception) {
                    _statusMessage.value = "Error saving file: ${e.message}"
                    Log.e(TAG, "Error saving file", e)
                }
            }
        }
    }

    /**
     * Update file content
     */
    fun updateContent(newContent: String) {
        _fileContent.value = newContent
        _isModified.value = true

        // Mark tab as modified
        val updatedTabs = _openTabs.value.toMutableList()
        if (_activeTabIndex.value < updatedTabs.size) {
            updatedTabs[_activeTabIndex.value] = updatedTabs[_activeTabIndex.value].copy(
                isModified = true
            )
            _openTabs.value = updatedTabs
        }
    }

    /**
     * Close tab
     */
    fun closeTab(index: Int) {
        if (index < _openTabs.value.size) {
            val updatedTabs = _openTabs.value.toMutableList()
            updatedTabs.removeAt(index)
            _openTabs.value = updatedTabs

            if (_activeTabIndex.value >= updatedTabs.size) {
                _activeTabIndex.value = updatedTabs.size - 1
            }

            _statusMessage.value = "Tab closed"
        }
    }

    /**
     * Select tab
     */
    fun selectTab(index: Int) {
        if (index < _openTabs.value.size) {
            _activeTabIndex.value = index
            _fileContent.value = _openTabs.value[index].content
            _isModified.value = _openTabs.value[index].isModified
        }
    }

    /**
     * Create new file
     */
    fun createNewFile(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = fileManager.createFile(_currentDirectory.value, fileName)
                if (success) {
                    refreshFileTree()
                    _statusMessage.value = "File created: $fileName"
                } else {
                    _statusMessage.value = "Failed to create file"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Create new folder
     */
    fun createNewFolder(folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = fileManager.createFolder(_currentDirectory.value, folderName)
                if (success) {
                    refreshFileTree()
                    _statusMessage.value = "Folder created: $folderName"
                } else {
                    _statusMessage.value = "Failed to create folder"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Delete file or folder
     */
    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = fileManager.delete(path)
                if (success) {
                    refreshFileTree()
                    _statusMessage.value = "Deleted: ${path.substringAfterLast('/')}"
                } else {
                    _statusMessage.value = "Failed to delete"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Rename file or folder
     */
    fun renameFile(path: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = fileManager.rename(path, newName)
                if (success) {
                    refreshFileTree()
                    _statusMessage.value = "Renamed to: $newName"
                } else {
                    _statusMessage.value = "Failed to rename"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Update search query
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Get language from file extension
     */
    private fun getLanguage(path: String): String {
        return when (fileManager.getExtension(path)) {
            "kt", "kts" -> "kotlin"
            "py" -> "python"
            "js", "ts", "tsx" -> "typescript"
            "java" -> "java"
            "cpp", "c", "h" -> "cpp"
            "xml" -> "xml"
            "json" -> "json"
            "md" -> "markdown"
            "txt" -> "text"
            else -> "text"
        }
    }
}

/**
 * Editor tab data class
 */
data class EditorTab(
    val name: String,
    val path: String,
    val content: String = "",
    val language: String = "text",
    val isModified: Boolean = false
)
