package com.elysium.code.editor

import android.content.Context
import android.util.Log
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * File Manager - CRUD Operations
 * ═══════════════════════════════════════════════════════════════
 */
class FileManager(private val context: Context) {

    companion object {
        private const val TAG = "FileManager"
    }

    /**
     * Get root directories to explore
     */
    fun getRootDirectories(): List<FileTreeNode> {
        val roots = mutableListOf<FileTreeNode>()
        
        // App cache directory
        context.cacheDir?.let { roots.add(FileTreeNode(it, true)) }
        
        // App files directory
        context.filesDir?.let { roots.add(FileTreeNode(it, true)) }
        
        // External storage
        context.getExternalFilesDir(null)?.let { roots.add(FileTreeNode(it, true)) }
        
        return roots
    }

    /**
     * List files and folders in a directory
     */
    fun listDirectory(path: String): List<FileTreeNode> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                ?.map { FileTreeNode(it, true) }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory: $path", e)
            emptyList()
        }
    }

    /**
     * Read file content
     */
    fun readFile(path: String): String {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: $path", e)
            ""
        }
    }

    /**
     * Write to file
     */
    fun writeFile(path: String, content: String): Boolean {
        return try {
            File(path).writeText(content)
            Log.d(TAG, "File written: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file: $path", e)
            false
        }
    }

    /**
     * Create new file
     */
    fun createFile(path: String, fileName: String): Boolean {
        return try {
            val file = File(path, fileName)
            val created = file.createNewFile()
            Log.d(TAG, "File created: ${file.absolutePath}")
            created
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file: $path/$fileName", e)
            false
        }
    }

    /**
     * Create new folder
     */
    fun createFolder(path: String, folderName: String): Boolean {
        return try {
            val folder = File(path, folderName)
            val created = folder.mkdirs()
            Log.d(TAG, "Folder created: ${folder.absolutePath}")
            created
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder: $path/$folderName", e)
            false
        }
    }

    /**
     * Delete file or folder
     */
    fun delete(path: String): Boolean {
        return try {
            val file = File(path)
            val deleted = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            Log.d(TAG, "Deleted: $path")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting: $path", e)
            false
        }
    }

    /**
     * Rename file or folder
     */
    fun rename(oldPath: String, newName: String): Boolean {
        return try {
            val oldFile = File(oldPath)
            val newFile = File(oldFile.parent, newName)
            val renamed = oldFile.renameTo(newFile)
            Log.d(TAG, "Renamed: $oldPath -> ${newFile.absolutePath}")
            renamed
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming: $oldPath", e)
            false
        }
    }

    /**
     * Check if path is directory
     */
    fun isDirectory(path: String): Boolean = File(path).isDirectory

    /**
     * Get file extension
     */
    fun getExtension(path: String): String {
        return File(path).extension
    }

    /**
     * Get file size
     */
    fun getFileSize(path: String): Long {
        return File(path).length()
    }

    /**
     * Check if file exists
     */
    fun exists(path: String): Boolean = File(path).exists()
}

/**
 * File tree node for UI representation
 */
data class FileTreeNode(
    val file: File,
    val isAccessible: Boolean
) {
    val path: String get() = file.absolutePath
    val name: String get() = file.name
    val isDirectory: Boolean get() = file.isDirectory
    val size: Long get() = file.length()
    val lastModified: Long get() = file.lastModified()
}
