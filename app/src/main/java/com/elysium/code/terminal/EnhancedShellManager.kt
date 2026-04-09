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
 * ENHANCED Terminal Process Manager
 * ═══════════════════════════════════════════════════════════════
 *
 * Executes shell commands directly without PTY complexity
 * (More reliable for app environment)
 */
class SimpleCommandExecutor(private val scope: CoroutineScope) {
    
    companion object {
        private const val TAG = "CommandExecutor"
    }

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    val output: SharedFlow<String> = _output.asSharedFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _lastExitCode = MutableStateFlow<Int?>(null)
    val lastExitCode: StateFlow<Int?> = _lastExitCode.asStateFlow()

    var customShellPath: String? = null
    var workingDir: String? = null

    /**
     * Execute a command and stream output
     */
    fun executeCommand(command: String): Job = scope.launch(Dispatchers.IO) {
        try {
            _isExecuting.value = true
            _output.emit("$ $command\n")
            
            val shell = customShellPath ?: "/system/bin/sh"
            val processBuilder = if (shell.contains("busybox")) {
                ProcessBuilder(shell, "sh", "-c", command)
            } else {
                ProcessBuilder(shell, "-c", command)
            }
            processBuilder.directory(java.io.File(workingDir ?: "/"))
            processBuilder.redirectErrorStream(true)
            
            val process = try {
                processBuilder.start()
            } catch (e: Exception) {
                Log.e(TAG, "Process start failed: ${e.message}")
                _output.emit("\n[ERROR] Could not start shell: ${e.message}\n")
                _isExecuting.value = false
                return@launch
            }
            
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    _output.emit(line!! + "\n")
                }
            }
            
            val exitCode = process.waitFor()
            _lastExitCode.value = exitCode
            
            if (exitCode != 0) {
                _output.emit("\n[Exit code: $exitCode]\n")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            _output.emit("\n[ERROR] ${e.message}\n")
            _lastExitCode.value = -1
        } finally {
            _isExecuting.value = false
        }
    }

    /**
     * Execute command and capture output
     */
    suspend fun executeAndCapture(command: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val shell = customShellPath ?: "/system/bin/sh"
            val processBuilder = ProcessBuilder(shell, "-c", command)
            processBuilder.directory(java.io.File(workingDir ?: "/"))
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            _lastExitCode.value = exitCode
            output
        } catch (e: Exception) {
            Log.e(TAG, "Execute and capture failed", e)
            ""
        }
    }
}

/**
 * ═══════════════════════════════════════════════════════════════
 * Enhanced Shell Manager with Direct Command Execution
 * ═══════════════════════════════════════════════════════════════
 */
class EnhancedShellManager(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "EnhancedShellManager"
    }

    private val commandExecutor = SimpleCommandExecutor(scope)
    
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _terminalOutputList = MutableStateFlow<List<String>>(emptyList())
    val terminalOutput: StateFlow<List<String>> = _terminalOutputList.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    init {
        // Forward executor output
        scope.launch {
            commandExecutor.output.collect { output ->
                _terminalOutputList.value = _terminalOutputList.value + output
            }
        }
        
        // Forward executing state
        scope.launch {
            commandExecutor.isExecuting.collect { executing ->
                _isExecuting.value = executing
            }
        }
    }

    /**
     * Execute a shell command
     */
    fun executeCommand(command: String) {
        Log.d(TAG, "Executing command: $command")
        
        // Add to history
        _commandHistory.value = _commandHistory.value + command
        
        // Execute
        commandExecutor.executeCommand(command)
    }

    /**
     * Execute command and return output
     */
    suspend fun executeAndCapture(command: String): String {
        _commandHistory.value = _commandHistory.value + command
        return commandExecutor.executeAndCapture(command)
    }

    /**
     * Clear terminal output
     */
    fun clearTerminal() {
        Log.d(TAG, "Clearing terminal")
        _terminalOutputList.value = emptyList()
    }

    /**
     * Clear history
     */
    fun clearHistory() {
        _commandHistory.value = emptyList()
    }

    /**
     * Set dynamic execution environment
     */
    fun setEnvironment(shellPath: String?, workDir: String?) {
        commandExecutor.customShellPath = shellPath
        commandExecutor.workingDir = workDir
    }

    /**
     * Get last exit code
     */
    fun getLastExitCode(): Int? = commandExecutor.lastExitCode.value

    /**
     * Check if executing
     */
    fun isExecuting(): Boolean = _isExecuting.value
}
