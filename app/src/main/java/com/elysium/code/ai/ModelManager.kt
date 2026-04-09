package com.elysium.code.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — ModelManager
 * ═══════════════════════════════════════════════════════════════
 *
 * Manages the embedded Gemma 4 E4B model. The GGUF model is
 * bundled inside the APK assets and extracted to internal
 * storage on first launch. No downloads needed — works offline
 * from the very first second.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_FILENAME = "gemma-4-e4b-it-q4_k_m.gguf"
        private const val MODEL_ZIP_FILENAME = "gemma-4-e4b-it-q4_k_m.zip"
        private const val MMPROJ_FILENAME = "mmproj-model-f16.gguf"
        private const val MODELS_DIR = "models"
        private const val ASSET_CHUNK_PREFIX = "model_part_"  // For split assets >150MB
        private const val EXTRACTION_COMPLETE_MARKER = ".extracted"
    }

    data class ModelInfo(
        val name: String = "Gemma 4 E4B",
        val filename: String = MODEL_FILENAME,
        val sizeBytes: Long = 0,
        val quantization: String = "Q4_K_M",
        val isExtracted: Boolean = false,
        val path: String = "",
        val hasMmProj: Boolean = false,
        val mmProjPath: String = ""
    )

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()

    private val _modelInfo = MutableStateFlow(ModelInfo())
    val modelInfo: StateFlow<ModelInfo> = _modelInfo.asStateFlow()

    private val modelsDir: File
        get() = File(context.getExternalFilesDir(null), MODELS_DIR).also { it?.mkdirs() }!!

    val modelFile: File
        get() = File(modelsDir, MODEL_FILENAME)

    val mmprojFile: File
        get() = File(modelsDir, MMPROJ_FILENAME)

    private val markerFile: File
        get() = File(modelsDir, EXTRACTION_COMPLETE_MARKER)

    /**
     * Check if the model is ready in the external files directory.
     */
    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Get the path to the current model file
     */
    fun getModelPath(): String = _modelInfo.value.path.ifEmpty { modelFile.absolutePath }

    /**
     * Extract the model from APK assets to internal storage.
     * This only runs on first launch — subsequent launches skip extraction.
     *
    * The model can be stored in assets as:
    * 1. A single file: assets/models/gemma-4-e4b-it-q4_k_m.gguf
     * 2. Split chunks: assets/models/model_part_00, model_part_01, etc.
     *    (needed because Android assets have a ~150MB per-file limit
     *     with aapt compression. We use noCompress + split for large models)
     */
    suspend fun extractModelIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (markerFile.exists()) {
            _modelInfo.value = ModelInfo(
                isExtracted = true,
                path = modelFile.absolutePath,
                sizeBytes = modelFile.length()
            )
            return@withContext true
        }

        // 1. Check if model folder exists
        if (!modelFile.parentFile.exists()) {
            modelFile.parentFile.mkdirs()
        }

        // 2. Magic Discovery: Search common Android directories for the model (including subdirectories)
        val searchPaths = listOf(
            "/sdcard/Download",
            "/sdcard/Documents",
            context.getExternalFilesDir(null)?.absolutePath ?: ""
        )

        var foundCandidate: File? = null
        var isZip = false
        
        for (path in searchPaths) {
            if (path.isBlank()) continue
            val baseDir = File(path)
            if (!baseDir.exists() || !baseDir.isDirectory) continue
            
            // 2a. Check for raw GGUF
            val candidate = File(baseDir, MODEL_FILENAME)
            if (candidate.exists()) {
                foundCandidate = candidate
                isZip = false
                break
            }
            
            // 2b. Check for ZIP
            val zipCandidate = File(baseDir, MODEL_ZIP_FILENAME)
            if (zipCandidate.exists()) {
                foundCandidate = zipCandidate
                isZip = true
                break
            }
            
            // check subdirectories
            val subdirs = baseDir.listFiles { file -> file.isDirectory }
            if (subdirs != null) {
                for (subdir in subdirs) {
                    val subCandidate = File(subdir, MODEL_FILENAME)
                    if (subCandidate.exists()) {
                        foundCandidate = subCandidate
                        isZip = false
                        break
                    }
                    val subZip = File(subdir, MODEL_ZIP_FILENAME)
                    if (subZip.exists()) {
                        foundCandidate = subZip
                        isZip = true
                        break
                    }
                }
            }
            if (foundCandidate != null) break
        }

        if (foundCandidate != null) {
            val candidate = foundCandidate
            Log.i(TAG, "Magic Discovery: Found ${if (isZip) "ZIP" else "GGUF"} at ${candidate.absolutePath}. Starting Auto-Migration...")
            
            try {
                if (isZip) {
                    // Extract from ZIP
                    FileInputStream(candidate).use { fis ->
                        ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            var foundInZip = false
                            while (entry != null) {
                                if (entry.name.endsWith(".gguf")) {
                                    foundInZip = true
                                    val totalSize = entry.size
                                    FileOutputStream(modelFile).use { fos ->
                                        val buffer = ByteArray(1024 * 1024 * 4)
                                        var totalCopied = 0L
                                        var bytesRead: Int
                                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                                            fos.write(buffer, 0, bytesRead)
                                            totalCopied += bytesRead
                                            if (totalSize > 0) {
                                                _extractionProgress.value = totalCopied.toFloat() / totalSize
                                            }
                                        }
                                        fos.flush()
                                    }
                                    break
                                }
                                entry = zis.nextEntry
                            }
                            if (!foundInZip) throw IOException("Model file not found inside ZIP archive")
                        }
                    }
                } else {
                    // Standard Copy
                    candidate.inputStream().use { input ->
                        FileOutputStream(modelFile).use { output ->
                            val buffer = ByteArray(1024 * 1024 * 4)
                            var totalCopied = 0L
                            val totalSize = candidate.length()
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalCopied += bytesRead
                                _extractionProgress.value = totalCopied.toFloat() / totalSize
                            }
                            output.flush()
                        }
                    }
                }
                
                markerFile.createNewFile()
                Log.i(TAG, "Auto-Migration complete.")
                
                _modelInfo.value = ModelInfo(
                    isExtracted = true,
                    path = modelFile.absolutePath,
                    sizeBytes = modelFile.length()
                )
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Auto-Migration failed for ${candidate.absolutePath}", e)
                modelFile.delete()
            }
        }

        Log.i(TAG, "Model not found in Docs. Attempting extraction from assets...")
        
        try {
            // Try single file first
            val assetPath = "$MODELS_DIR/$MODEL_FILENAME"
            if (assetExists(assetPath)) {
                Log.i(TAG, "Found model in assets, extracting to: ${modelFile.absolutePath}")
                extractSingleFile(assetPath)
                Log.i(TAG, "Model extraction complete")
                _modelInfo.value = ModelInfo(
                    isExtracted = true,
                    path = modelFile.absolutePath,
                    sizeBytes = modelFile.length()
                )
                return@withContext true
            }
            
            // Try chunked model
            Log.i(TAG, "Trying chunked model extraction...")
            extractChunkedModel()
            Log.i(TAG, "Chunked model extraction complete")
            _modelInfo.value = ModelInfo(
                isExtracted = true,
                path = modelFile.absolutePath,
                sizeBytes = modelFile.length()
            )
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "Model extraction failed", e)
            Log.e(TAG, "Model missing! Please ensure the model is bundled in assets/models/")
            _extractionProgress.value = 0f
            return@withContext false
        }
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).use { true }
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun extractSingleFile(assetPath: String) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(modelFile).use { output ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var totalCopied = 0L
                val totalSize = input.available().toLong()
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalCopied += bytesRead
                    if (totalSize > 0) {
                        _extractionProgress.value = totalCopied.toFloat() / totalSize
                    }
                }
                output.flush()
            }
        }
    }

    private suspend fun extractChunkedModel() {
        val chunkDir = MODELS_DIR
        val chunks = mutableListOf<String>()

        // Discover chunks in assets/models/
        try {
            val files = context.assets.list(chunkDir) ?: emptyArray()
            // Sorter for aa, ab, ac... suffixes
            chunks.addAll(
                files.filter { it.startsWith(ASSET_CHUNK_PREFIX) }
                    .sortedBy { it.substringAfter(ASSET_CHUNK_PREFIX) }
            )
        } catch (e: IOException) {
            Log.e(TAG, "Could not list asset chunks", e)
        }

        if (chunks.isEmpty()) {
            Log.e(TAG, "No model file or chunks found in assets/$chunkDir/")
            throw IOException("Model not found in assets. Bundle the GGUF model parts in assets/$chunkDir/")
        }

        Log.i(TAG, "Reconstructing model from ${chunks.size} chunks...")

        var totalSize = 0L
        // Roughly estimate or use a hardcoded total size if known
        // Gemma 4 E4B Q4_K_M is ~4.97 bil bytes
        val estimatedTotal = 4977164672L 

        FileOutputStream(modelFile).use { output ->
            var totalWritten = 0L
            chunks.forEachIndexed { index, chunk ->
                context.assets.open("$chunkDir/$chunk").use { input ->
                    val buffer = ByteArray(1024 * 1024 * 2) // 2MB faster buffer
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalWritten += bytesRead
                        _extractionProgress.value = totalWritten.toFloat() / estimatedTotal
                    }
                }
                Log.i(TAG, "Chunk $chunk ($index/${chunks.size}) reconstructed. Total: $totalWritten")
            }
            output.flush()
        }
        markerFile.createNewFile()
    }

    /**
     * Delete the extracted model to free storage
     */
    fun deleteModel() {
        modelFile.delete()
        markerFile.delete()
        _modelInfo.value = ModelInfo()
        _extractionProgress.value = 0f
        Log.i(TAG, "Model deleted from storage")
    }

    /**
     * Get available storage space in bytes
     */
    fun getAvailableStorageBytes(): Long {
        return context.filesDir.usableSpace
    }

    /**
     * Get the path to the multimodal projector if it exists
     */
    fun getMmprojPath(): String? {
        if (mmprojFile.exists() && mmprojFile.length() > 0) return mmprojFile.absolutePath
        return null
    }

    /**
     * Magic Discovery for multimodal projector (mmproj) files.
     * Scans standard directories for any file matching *mmproj* or *projector* patterns.
     */
    suspend fun discoverMmProj(): Boolean = withContext(Dispatchers.IO) {
        if (mmprojFile.exists() && mmprojFile.length() > 0) {
            Log.i(TAG, "MmProj already in storage: ${mmprojFile.absolutePath}")
            return@withContext true
        }

        val searchPaths = listOf(
            "/sdcard/Documents/Elysium/models",
            "/sdcard/Download/Elysium/models",
            "/sdcard/Download",
            "/sdcard/Documents",
            context.getExternalFilesDir(null)?.absolutePath ?: ""
        )

        for (path in searchPaths) {
            if (path.isBlank()) continue
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) continue

            dir.listFiles()?.forEach { file ->
                val name = file.name.lowercase()
                if (name.contains("mmproj") || name.contains("projector")) {
                    Log.i(TAG, "Magic Discovery: Found mmproj at ${file.absolutePath}")
                    try {
                        file.copyTo(mmprojFile, overwrite = true)
                        Log.i(TAG, "MmProj migrated to ${mmprojFile.absolutePath}")
                        return@withContext true
                    } catch (e: Exception) {
                        Log.e(TAG, "MmProj migration failed", e)
                    }
                }
            }
        }

        Log.w(TAG, "No mmproj file found. Multimodal features disabled.")
        return@withContext false
    }
}
