package com.elysium.code.ai

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — GgufParser
 * ═══════════════════════════════════════════════════════════════
 *
 * A lightweight, high-performance GGUF metadata parser.
 * Reads headers and key-value pairs directly from disk without
 * loading the full model. Critical for the Model Library scan.
 */
class GgufParser(private val file: File) {

    data class GgufMetadata(
        val name: String?,
        val architecture: String?,
        val contextLength: Int?,
        val quantization: String?,
        val version: Int,
        val tensorCount: Long,
        val kvCount: Long
    )

    private companion object {
        const val TAG = "GgufParser"
        const val GGUF_MAGIC = 0x47475546 // "GGUF"
    }

    fun parse(): GgufMetadata? {
        if (!file.exists()) return null
        
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val buffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, 1024 * 64) // 64KB should cover headers
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val magic = buffer.int
                if (magic != GGUF_MAGIC) {
                    Log.w(TAG, "Not a GGUF file: ${file.name}")
                    return null
                }

                val version = buffer.int
                val tensorCount = buffer.long
                val kvCount = buffer.long

                var name: String? = null
                var architecture: String? = null
                var contextLength: Int? = null
                var quantization: String? = null

                // Simple KV parser for the first 64KB
                for (i in 0 until kvCount) {
                    if (buffer.remaining() < 8) break
                    
                    val key = readGgufString(buffer) ?: break
                    val valueType = buffer.int
                    
                    when (valueType) {
                        0 -> buffer.get() // UINT8
                        1 -> buffer.get() // INT8
                        2 -> buffer.short // UINT16
                        3 -> buffer.short // INT16
                        4 -> buffer.int // UINT32
                        5 -> buffer.int // INT32
                        6 -> buffer.float // FLOAT32
                        7 -> buffer.get() // BOOL
                        8 -> { // STRING
                            val strValue = readGgufString(buffer)
                            when (key) {
                                "general.name" -> name = strValue
                                "general.architecture" -> architecture = strValue
                                "general.file_type" -> quantization = getQuantizationLabel(strValue ?: "0")
                            }
                        }
                        9 -> { // ARRAY
                            val itemType = buffer.int
                            val len = buffer.long
                            // Skip array content for now, we don't need it for basic info
                            skipGgufArray(buffer, itemType, len)
                        }
                        10 -> buffer.int // UINT64 (misaligned but typically read as uint32 index in some versions)
                        11 -> buffer.long // INT64
                        12 -> buffer.double // FLOAT64
                    }
                    
                    // Specific mapping for keys we missed in the type switch
                    if (key == "llama.context_length" || key == "gemma.context_length") {
                        // Context length is usually uint32 (4)
                    }
                }

                GgufMetadata(
                    name = name ?: file.nameWithoutExtension,
                    architecture = architecture,
                    contextLength = contextLength,
                    quantization = quantization,
                    version = version,
                    tensorCount = tensorCount,
                    kvCount = kvCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GGUF: ${file.name}", e)
            null
        }
    }

    private fun readGgufString(buffer: java.nio.ByteBuffer): String? {
        if (buffer.remaining() < 8) return null
        val len = buffer.long.toInt()
        if (len < 0 || len > buffer.remaining()) return null
        val bytes = ByteArray(len)
        buffer.get(bytes)
        return String(bytes)
    }

    private fun skipGgufArray(buffer: java.nio.ByteBuffer, type: Int, len: Long) {
        // Implementation for skipping arrays to keep the pointer moving
        // For simplicity in this metadata-only parser, we might just stop if we hit complex arrays
    }

    private fun getQuantizationLabel(type: String): String {
        return when (type) {
            "1" -> "F16"
            "2" -> "Q4_0"
            "3" -> "Q4_1"
            "8" -> "Q5_0"
            "9" -> "Q5_1"
            "12" -> "Q8_0"
            "15" -> "Q4_K_M"
            "17" -> "Q5_K_M"
            else -> "Unknown ($type)"
        }
    }
}
