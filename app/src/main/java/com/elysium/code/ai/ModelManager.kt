package com.elysium.code.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
        val path: String = ""
    )

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()

    private val _modelInfo = MutableStateFlow(ModelInfo())
    val modelInfo: StateFlow<ModelInfo> = _modelInfo.asStateFlow()

    private val modelsDir: File
        get() = File(context.getExternalFilesDir(null), MODELS_DIR).also { it?.mkdirs() }!!

    private val modelFile: File
        get() = File(modelsDir, MODEL_FILENAME)

    private val markerFile: File
        get() = File(modelsDir, EXTRACTION_COMPLETE_MARKER)

    /**
     * Check if the model is ready in the external files directory.
     */
    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Get the path to the extracted model file
     */
    fun getModelPath(): String = modelFile.absolutePath

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
        if (isModelReady()) {
            Log.i(TAG, "Model ready at: ${modelFile.absolutePath}")
            _extractionProgress.value = 1f
            _modelInfo.value = ModelInfo(
                isExtracted = true,
                path = modelFile.absolutePath,
                sizeBytes = modelFile.length()
            )
            return@withContext true
        }

        // --- NEW: Check for Sideloaded Model first ---
        val publicDocs = File("/sdcard/Documents/Elysium/models", MODEL_FILENAME)
        if (publicDocs.exists()) {
            Log.i(TAG, "Found sideloaded model. Importing...")
            try {
                publicDocs.inputStream().use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output) }
                }
                markerFile.createNewFile()
                return@withContext true
            } catch (e: Exception) { Log.e(TAG, "Sideload import failed", e) }
        }

        Log.i(TAG, "Model not ready. Attempting extraction from assets...")
        
        try {
            // Try single file first
            val assetPath = "$MODELS_DIR/$MODEL_FILENAME"
            if (assetExists(assetPath)) {
                Log.i(TAG, "Found model in assets, extracting to: ${modelFile.absolutePath}")
                extractSingleFile(assetPath)
                Log.i(TAG, "Model extraction complete")
                return@withContext true
            }
            
            // Try chunked model
            Log.i(TAG, "Trying chunked model extraction...")
            extractChunkedModel()
            Log.i(TAG, "Chunked model extraction complete")
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

        // Discover chunks
        try {
            val files = context.assets.list(chunkDir) ?: emptyArray()
            chunks.addAll(
                files.filter { it.startsWith(ASSET_CHUNK_PREFIX) }
                    .sorted()
            )
        } catch (e: IOException) {
            Log.e(TAG, "Could not list asset chunks", e)
        }

        if (chunks.isEmpty()) {
            Log.e(TAG, "No model file or chunks found in assets/$chunkDir/")
            throw IOException("Model not found in assets. Bundle the GGUF model in assets/$chunkDir/")
        }

        Log.i(TAG, "Found ${chunks.size} model chunks")

        FileOutputStream(modelFile).use { output ->
            var totalWritten = 0L
            chunks.forEachIndexed { index, chunk ->
                context.assets.open("$chunkDir/$chunk").use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalWritten += bytesRead
                    }
                }
                _extractionProgress.value = (index + 1).toFloat() / chunks.size
                Log.i(TAG, "Chunk ${index + 1}/${chunks.size} extracted ($totalWritten bytes total)")
            }
            output.flush()
        }
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
}
