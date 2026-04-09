package com.elysium.code.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — PtyProcess
 * ═══════════════════════════════════════════════════════════════
 *
 * Manages native PTY (pseudo-terminal) processes for real
 * shell sessions. Each PtyProcess represents one terminal
 * session with its own shell, environment, and I/O streams.
 */
class PtyProcess private constructor(
    val sessionId: Int,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PtyProcess"

        init {
            System.loadLibrary("elysium_native")
        }

        fun create(
            shellPath: String = "/system/bin/sh",
            rows: Int = 24,
            cols: Int = 80,
            envVars: Array<String> = arrayOf(),
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): PtyProcess? {
            val sessionId = nativeCreateSession(shellPath, rows, cols, envVars)
            if (sessionId < 0) {
                Log.e(TAG, "Failed to create PTY session")
                return null
            }
            Log.i(TAG, "PTY session created: $sessionId")
            val process = PtyProcess(sessionId, scope)
            process.startReadLoop()
            return process
        }

        @JvmStatic
        private external fun nativeCreateSession(
            shellPath: String?,
            rows: Int,
            cols: Int,
            envVars: Array<String>?
        ): Int
    }

    // Native methods (instance-level for JNI)
    private external fun nativeRead(sessionId: Int, maxBytes: Int): ByteArray?
    private external fun nativeWrite(sessionId: Int, data: ByteArray): Int
    private external fun nativeResize(sessionId: Int, rows: Int, cols: Int)
    private external fun nativeSendSignal(sessionId: Int, signal: Int)
    private external fun nativeIsAlive(sessionId: Int): Boolean
    private external fun nativeDestroySession(sessionId: Int)
    private external fun nativeGetExitCode(sessionId: Int): Int

    // ═══ State ═══
    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 4096)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private val _isAlive = MutableStateFlow(true)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private var readJob: Job? = null

    // ═══ Screen buffer for ANSI terminal ═══
    private val _screenBuffer = MutableStateFlow(TerminalBuffer())
    val screenBuffer: StateFlow<TerminalBuffer> = _screenBuffer.asStateFlow()

    private val ansiParser = AnsiParser { text ->
        _output.tryEmit(text)
    }

    // ═══ Public API ═══

    fun write(data: String) {
        scope.launch(Dispatchers.IO) {
            val bytes = data.toByteArray(Charsets.UTF_8)
            nativeWrite(sessionId, bytes)
        }
    }

    fun write(data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            nativeWrite(sessionId, data)
        }
    }

    fun sendCommand(command: String) {
        write("$command\n")
    }

    fun sendInterrupt() {
        nativeSendSignal(sessionId, 2) // SIGINT
    }

    fun sendEof() {
        write(byteArrayOf(4)) // Ctrl+D
    }

    fun resize(rows: Int, cols: Int) {
        nativeResize(sessionId, rows, cols)
    }

    fun checkAlive(): Boolean {
        val alive = nativeIsAlive(sessionId)
        _isAlive.value = alive
        return alive
    }

    fun getExitCode(): Int = nativeGetExitCode(sessionId)

    fun destroy() {
        readJob?.cancel()
        nativeDestroySession(sessionId)
        _isAlive.value = false
        Log.i(TAG, "Session $sessionId destroyed")
    }

    // ═══ Internal ═══

    private fun startReadLoop() {
        readJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val data = nativeRead(sessionId, 8192)
                    if (data != null && data.isNotEmpty()) {
                        val text = String(data, Charsets.UTF_8)
                        _output.tryEmit(text)
                    } else if (!nativeIsAlive(sessionId)) {
                        _isAlive.value = false
                        break
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "Read error on session $sessionId", e)
                    }
                    break
                }
                delay(16) // ~60fps read rate
            }
        }
    }
}

/**
 * Simple ANSI escape code parser for terminal output
 */
class AnsiParser(private val onOutput: (String) -> Unit) {
    fun process(data: String) {
        onOutput(data)
    }
}

/**
 * Terminal screen buffer holding the display state
 */
data class TerminalBuffer(
    val lines: MutableList<String> = mutableListOf(""),
    val cursorRow: Int = 0,
    val cursorCol: Int = 0,
    val scrollback: Int = 10000
)
