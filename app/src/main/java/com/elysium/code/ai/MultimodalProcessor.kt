package com.elysium.code.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — MultimodalProcessor
 * ═══════════════════════════════════════════════════════════════
 *
 * Processes images, audio, and video into formats suitable for
 * Gemma 4 E4B multimodal inference. Handles all media encoding
 * and preprocessing locally on-device.
 */
class MultimodalProcessor(private val context: Context) {

    companion object {
        private const val TAG = "MultimodalProcessor"
        private const val MAX_IMAGE_SIZE = 1024
        private const val VIDEO_FPS_SAMPLE = 1 // 1 frame per second
        private const val MAX_VIDEO_FRAMES = 30
        private const val AUDIO_SAMPLE_RATE = 16000
    }

    // ═══════════════════════════════════════════════════════════
    // IMAGE PROCESSING
    // ═══════════════════════════════════════════════════════════

    /**
     * Process an image file into a base64-encoded representation
     * for inclusion in the prompt. Handles variable aspect ratios.
     */
    suspend fun processImage(imagePath: String): ProcessedImage = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: throw IllegalArgumentException("Cannot decode image: $imagePath")
        processBitmap(bitmap)
    }

    suspend fun processImage(uri: Uri): ProcessedImage = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image URI: $uri")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        processBitmap(bitmap)
    }

    suspend fun processBitmap(bitmap: Bitmap): ProcessedImage = withContext(Dispatchers.IO) {
        // Resize maintaining aspect ratio
        val scaled = resizeBitmap(bitmap, MAX_IMAGE_SIZE)

        // Convert to JPEG bytes (Gemma 4 E4B accepts JPEG/PNG directly)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()

        Log.i(TAG, "Image processed: ${scaled.width}x${scaled.height}, ${bytes.size} bytes")

        ProcessedImage(
            data = bytes,
            width = scaled.width,
            height = scaled.height,
            mimeType = "image/jpeg"
        )
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = min(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ═══════════════════════════════════════════════════════════
    // AUDIO PROCESSING
    // ═══════════════════════════════════════════════════════════

    /**
    * Process an audio file into PCM data for speech recognition.
    * Gemma 4 E4B supports native audio input for speech.
     */
    suspend fun processAudio(audioPath: String): ProcessedAudio = withContext(Dispatchers.IO) {
        val file = File(audioPath)
        if (!file.exists()) throw IllegalArgumentException("Audio file not found: $audioPath")

        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioPath)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex < 0) {
                throw IllegalArgumentException("No audio track found in: $audioPath")
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000_000 // seconds

            // Read raw PCM data
            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            val pcmData = ByteArrayOutputStream()

            while (true) {
                val readSize = extractor.readSampleData(buffer, 0)
                if (readSize < 0) break
                val bytes = ByteArray(readSize)
                buffer.get(bytes, 0, readSize)
                pcmData.write(bytes)
                buffer.clear()
                extractor.advance()
            }

            extractor.release()

            val audioBytes = pcmData.toByteArray()
            Log.i(TAG, "Audio processed: ${duration}s, ${sampleRate}Hz, " +
                    "${channelCount}ch, ${audioBytes.size} bytes")

            ProcessedAudio(
                data = audioBytes,
                sampleRate = sampleRate,
                channels = channelCount,
                durationSeconds = duration.toFloat(),
                format = "pcm_s16le"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio processing error", e)
            throw e
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VIDEO PROCESSING
    // ═══════════════════════════════════════════════════════════

    /**
    * Extract frames from video at specified FPS for video understanding.
    * Gemma 4 E4B processes video as sequences of frames.
     */
    suspend fun processVideo(
        videoPath: String,
        fps: Int = VIDEO_FPS_SAMPLE,
        maxFrames: Int = MAX_VIDEO_FRAMES
    ): ProcessedVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val durationSec = durationMs / 1000f
            val intervalMs = (1000L / fps)
            val totalFrames = min((durationSec * fps).toInt(), maxFrames)

            val frames = mutableListOf<ProcessedImage>()

            for (i in 0 until totalFrames) {
                val timeUs = (i * intervalMs * 1000).toLong()
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val processed = processBitmap(frame)
                    frames.add(processed)
                }
            }

            Log.i(TAG, "Video processed: ${durationSec}s, ${frames.size} frames extracted")

            ProcessedVideo(
                frames = frames,
                durationSeconds = durationSec,
                fps = fps,
                totalFrames = frames.size
            )
        } finally {
            retriever.release()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // PROMPT BUILDING
    // ═══════════════════════════════════════════════════════════

    /**
     * Build a multimodal prompt combining text with media inputs.
     * Formats the prompt according to Gemma 4 chat template.
     */
    fun buildMultimodalPrompt(
        text: String,
        images: List<ProcessedImage> = emptyList(),
        audio: ProcessedAudio? = null,
        systemPrompt: String = ""
    ): String {
        val sb = StringBuilder()

        // System prompt
        if (systemPrompt.isNotBlank()) {
            sb.appendLine("<start_of_turn>system")
            sb.appendLine(systemPrompt)
            sb.appendLine("<end_of_turn>")
        }

        // User turn with multimodal content
        sb.appendLine("<start_of_turn>user")

        // Image tokens
        images.forEach { _ ->
            sb.appendLine("<image>")
        }

        // Audio token
        if (audio != null) {
            sb.appendLine("<audio>")
        }

        // Text
        sb.appendLine(text)
        sb.appendLine("<end_of_turn>")

        // Model turn start
        sb.append("<start_of_turn>model\n")

        return sb.toString()
    }
}

// ═══════════════════════════════════════════════════════════════
// Data Classes for Processed Media
// ═══════════════════════════════════════════════════════════════

data class ProcessedImage(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedImage) return false
        return data.contentEquals(other.data) && width == other.width && height == other.height
    }
    override fun hashCode() = data.contentHashCode()
}

data class ProcessedAudio(
    val data: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val durationSeconds: Float,
    val format: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedAudio) return false
        return data.contentEquals(other.data)
    }
    override fun hashCode() = data.contentHashCode()
}

data class ProcessedVideo(
    val frames: List<ProcessedImage>,
    val durationSeconds: Float,
    val fps: Int,
    val totalFrames: Int
)
