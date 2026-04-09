package com.elysium.code.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — ShellManager
 * ═══════════════════════════════════════════════════════════════
 *
 * Manages multiple terminal sessions (tabs). Handles session
 * creation, switching, persistence, and lifecycle management.
 */
class ShellManager(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "ShellManager"
        private const val MAX_SESSIONS = 10
    }

    data class SessionInfo(
        val id: Int,
        val name: String,
        val process: PtyProcess,
        val createdAt: Long = System.currentTimeMillis(),
        val workingDir: String = "~"
    )

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Int?>(null)
    val activeSessionId: StateFlow<Int?> = _activeSessionId.asStateFlow()

    private var sessionCounter = 0

    /**
     * Create a new terminal session
     */
    fun createSession(
        name: String? = null,
        shellPath: String = "/system/bin/sh",
        rows: Int = 24,
        cols: Int = 80,
        envVars: Array<String> = arrayOf()
    ): SessionInfo? {
        if (_sessions.value.size >= MAX_SESSIONS) {
            Log.w(TAG, "Maximum session count reached ($MAX_SESSIONS)")
            return null
        }

        val process = PtyProcess.create(
            shellPath = shellPath,
            rows = rows,
            cols = cols,
            envVars = envVars,
            scope = scope
        ) ?: return null

        sessionCounter++
        val sessionName = name ?: "Session $sessionCounter"

        val info = SessionInfo(
            id = process.sessionId,
            name = sessionName,
            process = process
        )

        _sessions.value = _sessions.value + info
        _activeSessionId.value = info.id

        // Monitor session lifecycle
        scope.launch {
            process.isAlive.collect { alive ->
                if (!alive) {
                    Log.i(TAG, "Session ${info.id} ('${info.name}') terminated")
                }
            }
        }

        Log.i(TAG, "Session created: ${info.id} ('${info.name}')")
        return info
    }

    /**
     * Switch to an existing session
     */
    fun switchSession(sessionId: Int): Boolean {
        val session = _sessions.value.find { it.id == sessionId }
        if (session != null) {
            _activeSessionId.value = sessionId
            return true
        }
        return false
    }

    /**
     * Get the currently active session
     */
    fun getActiveSession(): SessionInfo? {
        val activeId = _activeSessionId.value ?: return null
        return _sessions.value.find { it.id == activeId }
    }

    /**
     * Close a session
     */
    fun closeSession(sessionId: Int) {
        val session = _sessions.value.find { it.id == sessionId } ?: return

        session.process.destroy()
        _sessions.value = _sessions.value.filter { it.id != sessionId }

        // Switch to another session if we closed the active one
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = _sessions.value.lastOrNull()?.id
        }

        Log.i(TAG, "Session closed: $sessionId")
    }

    /**
     * Send command to active session
     */
    fun sendCommand(command: String): Boolean {
        val session = getActiveSession() ?: return false
        session.process.sendCommand(command)
        return true
    }

    /**
     * Send command to specific session and capture output
     */
    suspend fun executeAndCapture(
        sessionId: Int,
        command: String,
        timeoutMs: Long = 30_000
    ): String {
        val session = _sessions.value.find { it.id == sessionId }
            ?: throw IllegalArgumentException("Session $sessionId not found")

        val output = StringBuilder()
        val job = scope.launch {
            session.process.output.collect { text ->
                output.append(text)
            }
        }

        session.process.sendCommand(command)
        delay(timeoutMs.coerceAtMost(500)) // Wait for output
        job.cancel()

        return output.toString()
    }

    /**
     * Rename a session
     */
    fun renameSession(sessionId: Int, newName: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) session.copy(name = newName)
            else session
        }
    }

    /**
     * Close all sessions
     */
    fun closeAll() {
        _sessions.value.forEach { it.process.destroy() }
        _sessions.value = emptyList()
        _activeSessionId.value = null
    }

    /**
     * Get session count
     */
    fun sessionCount(): Int = _sessions.value.size
}
