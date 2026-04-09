package com.elysium.code.ai

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — LlamaEngine
 * ═══════════════════════════════════════════════════════════════
 *
 * Kotlin wrapper over the native llama.cpp JNI bridge.
 * Manages model lifecycle, streaming inference, and provides
 * a clean coroutine-based API for the rest of the app.
 */
class LlamaEngine {

    companion object {
        private const val TAG = "LlamaEngine"
        private var isNativeLibraryLoaded = false

        init {
            try {
                System.loadLibrary("elysium_native")
                isNativeLibraryLoaded = true
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                isNativeLibraryLoaded = false
            }
        }
    }

    // ═══ State ═══
    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _tokenStream = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val tokenStream: Flow<String> = _tokenStream.asSharedFlow()

    private val _inferenceSpeed = MutableStateFlow(0f)
    val inferenceSpeed: StateFlow<Float> = _inferenceSpeed.asStateFlow()

    private var inferenceJob: Job? = null

    // ═══ Native methods ═══
    private external fun nativeInit()
    private external fun nativeLoadModel(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
        nGpuLayers: Int,
        useMmap: Boolean,
        useMlock: Boolean
    ): Boolean
    private external fun nativeInfer(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: InferenceCallback
    ): String
    private external fun nativeCancelInference()
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeIsGenerating(): Boolean
    private external fun nativeGetModelInfo(): String
    private external fun nativeUnloadModel()
    private external fun nativeCleanup()
    private external fun nativeTokenize(text: String): Int
    private external fun nativeGetContextSize(): Int

    // ═══ Public API ═══

    fun initialize() {
        Log.i(TAG, "Initializing LlamaEngine")
        if (!isNativeLibraryLoaded) {
            Log.e(TAG, "Native library not loaded! Cannot initialize.")
            _state.value = EngineState.ERROR
            return
        }
        try {
            nativeInit()
            _state.value = EngineState.INITIALIZED
            Log.i(TAG, "LlamaEngine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native engine", e)
            _state.value = EngineState.ERROR
        }
    }

    suspend fun loadModel(config: ModelConfig): Boolean = withContext(Dispatchers.IO) {
        _state.value = EngineState.LOADING
        
        if (!isNativeLibraryLoaded) {
            Log.e(TAG, "Native library not loaded, cannot load model")
            _state.value = EngineState.ERROR
            return@withContext false
        }

        try {
            val success = nativeLoadModel(
                modelPath = config.modelPath,
                nCtx = config.contextSize,
                nThreads = config.threadCount,
                nGpuLayers = config.gpuLayers,
                useMmap = config.useMmap,
                useMlock = config.useMlock
            )

            _state.value = if (success) EngineState.READY else EngineState.ERROR
            Log.i(TAG, "Model load result: $success (path: ${config.modelPath})")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _state.value = EngineState.ERROR
            false
        }
    }

    suspend fun generate(
        prompt: String,
        config: InferenceConfig = InferenceConfig()
    ): String = withContext(Dispatchers.IO) {
        if (_state.value != EngineState.READY) {
            throw IllegalStateException("Engine not ready. Current state: ${_state.value}")
        }

        _state.value = EngineState.GENERATING
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        val callback = object : InferenceCallback {
            override fun onToken(token: String) {
                tokenCount++
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                if (elapsed > 0) {
                    _inferenceSpeed.value = tokenCount / elapsed
                }
                _tokenStream.tryEmit(token)
            }

            override fun onComplete(result: String) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                Log.i(TAG, "Generation complete: $tokenCount tokens in ${elapsed}s " +
                        "(${if (elapsed > 0) tokenCount / elapsed else 0f} tok/s)")
            }
        }

        try {
            val result = nativeInfer(
                prompt = prompt,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                repeatPenalty = config.repeatPenalty,
                callback = callback
            )
            _state.value = EngineState.READY
            result
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            _state.value = EngineState.ERROR
            throw e
        }
    }

    fun cancelGeneration() {
        nativeCancelInference()
        inferenceJob?.cancel()
    }

    fun getModelInfo(): String = nativeGetModelInfo()

    fun tokenCount(text: String): Int = nativeTokenize(text)

    fun getContextSize(): Int = nativeGetContextSize()

    fun isReady(): Boolean = _state.value == EngineState.READY

    fun isGenerating(): Boolean = nativeIsGenerating()

    fun unloadModel() {
        nativeUnloadModel()
        _state.value = EngineState.INITIALIZED
    }

    fun cleanup() {
        nativeCleanup()
        _state.value = EngineState.UNINITIALIZED
    }
}

// ═══════════════════════════════════════════════════════════════
// Data Classes
// ═══════════════════════════════════════════════════════════════

enum class EngineState {
    UNINITIALIZED,
    INITIALIZED,
    LOADING,
    READY,
    GENERATING,
    ERROR
}

data class ModelConfig(
    val modelPath: String,
    val contextSize: Int = 4096,
    val threadCount: Int = 4,
    val gpuLayers: Int = 0,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false
)

data class InferenceConfig(
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val systemPrompt: String = "",
    val stopSequences: List<String> = emptyList()
)

interface InferenceCallback {
    fun onToken(token: String)
    fun onComplete(result: String)
}
