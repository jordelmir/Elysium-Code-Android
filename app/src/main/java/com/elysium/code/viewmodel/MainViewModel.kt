package com.elysium.code.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.code.agent.AgentOrchestrator
import com.elysium.code.agent.ConversationManager
import com.elysium.code.agent.ToolExecutor
import com.elysium.code.agent.ToolRegistry
import com.elysium.code.agent.EnhancedAgentOrchestrator
import com.elysium.code.ai.LlamaEngine
import com.elysium.code.ai.ModelConfig
import com.elysium.code.ai.ModelManager
import com.elysium.code.ai.MultimodalProcessor
import com.elysium.code.mcp.McpClient
import com.elysium.code.memory.MemoryEngine
import com.elysium.code.plugins.PersonalityEngine
import com.elysium.code.plugins.PluginLoader
import com.elysium.code.plugins.SkillsParser
import com.elysium.code.terminal.ShellManager
import com.elysium.code.terminal.EnhancedShellManager
import com.elysium.code.terminal.RootfsInstaller
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import androidx.documentfile.provider.DocumentFile

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — MainViewModel
 * ═══════════════════════════════════════════════════════════════
 *
 * Central ViewModel that owns all engine instances and exposes
 * them to the UI layer. Manages the full app lifecycle from
 * model extraction to agent initialization.
 *
 * SINGLE SOURCE OF TRUTH: The AgentOrchestrator is the primary
 * brain. The EnhancedAgentOrchestrator is a lightweight
 * companion for quick slash commands and simple chat.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ═══ Boot State ═══
    enum class BootPhase {
        INITIALIZING,
        EXTRACTING_MODEL,
        LOADING_MODEL,
        LOADING_MEMORY,
        LOADING_PLUGINS,
        BOOTSTRAPPING_LINUX,
        READY,
        ERROR
    }

    private val _bootPhase = MutableStateFlow(BootPhase.INITIALIZING)
    val bootPhase: StateFlow<BootPhase> = _bootPhase.asStateFlow()

    private val _bootMessage = MutableStateFlow("Starting Elysium...")
    val bootMessage: StateFlow<String> = _bootMessage.asStateFlow()

    private val _bootProgress = MutableStateFlow(0f)
    val bootProgress: StateFlow<Float> = _bootProgress.asStateFlow()
    
    private val _linuxSetupProgress = MutableStateFlow(0f)
    val linuxSetupProgress: StateFlow<Float> = _linuxSetupProgress.asStateFlow()

    // ═══ Core Engines ═══
    val llamaEngine = LlamaEngine()
    val modelManager = ModelManager(application)
    val multimodalProcessor = MultimodalProcessor(application)
    val shellManager = ShellManager(viewModelScope)
    val memoryEngine = MemoryEngine(application)
    val personalityEngine = PersonalityEngine(application)
    val skillsParser = SkillsParser(application)
    val pluginLoader = PluginLoader(application)
    val conversationManager = ConversationManager(application)
    val mcpClient = McpClient(application, HttpClient(OkHttp))
    val toolRegistry = ToolRegistry()
    val toolExecutor = ToolExecutor()

    // ═══ Enhanced Components ═══
    val enhancedShellManager = EnhancedShellManager(viewModelScope)
    
    // EnhancedAgent gets the real LlamaEngine reference
    val enhancedAgent = EnhancedAgentOrchestrator(
        scope = viewModelScope, 
        shellManager = enhancedShellManager,
        llamaEngine = llamaEngine
    )

    // ═══ Primary Agent Orchestrator ═══
    lateinit var agentOrchestrator: AgentOrchestrator
        private set

    // ═══ Initialization ═══
    var customShellPath: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            bootSequence()
        }
    }

    private suspend fun bootSequence() {
        try {
            val appContext = getApplication<Application>().applicationContext

            // Phase 1: Initialize base systems
            _bootPhase.value = BootPhase.INITIALIZING
            _bootMessage.value = "Initializing systems..."
            _bootProgress.value = 0.05f

            llamaEngine.initialize()
            conversationManager.initialize()

            // Initialize Orchestrator EARLIER so we can track LOADING state
            agentOrchestrator = AgentOrchestrator(
                engine = llamaEngine,
                toolRegistry = toolRegistry,
                toolExecutor = toolExecutor,
                memoryEngine = memoryEngine,
                personalityEngine = personalityEngine,
                skillsParser = skillsParser,
                multimodalProcessor = multimodalProcessor,
                scope = viewModelScope
            )
            
            // Bootstrap Shell (Busybox — instant, zero download)
            try {
                customShellPath = RootfsInstaller.setupBusybox(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Busybox setup failed, will use system shell", e)
            }
            
            _bootProgress.value = 0.15f

            // Phase 2: Try to find/extract the model (NON-BLOCKING)
            _bootPhase.value = BootPhase.EXTRACTING_MODEL
            _bootMessage.value = "Searching for AI model..."

            var modelReady = false
            try {
                viewModelScope.launch {
                    modelManager.extractionProgress.collect { progress ->
                        if (progress > 0f && progress < 1f) {
                            _bootProgress.value = 0.15f + (progress * 0.35f)
                            _bootMessage.value = "Migrating AI Model... ${(progress * 100).toInt()}%"
                        }
                    }
                }

                val extracted = modelManager.extractModelIfNeeded()
                if (extracted) {
                    // Phase 3: Load model into memory
                    _bootPhase.value = BootPhase.LOADING_MODEL
                    _bootMessage.value = "Streaming model weights into RAM... (15-20s)"
                    _bootProgress.value = 0.30f
                    
                    agentOrchestrator.updateState(com.elysium.code.agent.AgentState.LOADING)

                    modelReady = llamaEngine.loadModel(ModelConfig(
                        modelPath = modelManager.getModelPath(),
                        contextSize = 2048,
                        threadCount = 6,
                        gpuLayers = 0,
                        useMmap = true,
                        useMlock = false
                    ))

                    if (!modelReady) {
                        Log.w(TAG, "Model load failed. Check Logcat (LlamaNative).")
                        _bootMessage.value = "AI Engine error: Check Logcat (LlamaNative)"
                        agentOrchestrator.updateState(com.elysium.code.agent.AgentState.ERROR)
                    } else {
                        _bootMessage.value = "Model loaded successfully."
                        agentOrchestrator.updateState(com.elysium.code.agent.AgentState.IDLE)
                    }
                } else {
                    Log.w(TAG, "Model not found. App will continue without AI. Place model in /sdcard/Download/")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model setup failed, continuing without AI", e)
            }

            _bootProgress.value = 0.70f

            // Phase 4: Load memory and knowledge (always runs)
            _bootPhase.value = BootPhase.LOADING_MEMORY
            _bootMessage.value = "Loading agent memory..."
            try { memoryEngine.initialize() } catch (e: Exception) { Log.w(TAG, "Memory init failed", e) }
            _bootProgress.value = 0.80f

            // Phase 5: Load plugins and skills (always runs)
            _bootPhase.value = BootPhase.LOADING_PLUGINS
            _bootMessage.value = "Loading plugins..."
            try {
                personalityEngine.initialize()
                skillsParser.initialize()
                pluginLoader.initialize()
                mcpClient.initialize()
            } catch (e: Exception) { Log.w(TAG, "Plugin init failed", e) }
            _bootProgress.value = 0.90f

            // Phase 6: Shell environment (always runs)
            _bootPhase.value = BootPhase.BOOTSTRAPPING_LINUX
            _bootMessage.value = "Bootstrapping shell..."

            val homeDir = File(appContext.filesDir, "home")
            if (!homeDir.exists()) homeDir.mkdirs()

            if (customShellPath != null) {
                enhancedShellManager.setEnvironment(customShellPath!!, homeDir.absolutePath)
            } else {
                enhancedShellManager.setEnvironment("/system/bin/sh", homeDir.absolutePath)
            }

            _bootProgress.value = 0.95f

            // Phase 7: Orchestrator is already initialized in Phase 1
            _bootPhase.value = BootPhase.READY
            _bootProgress.value = 1.0f
            _bootMessage.value = if (modelReady) {
                "Elysium Code ready."
            } else {
                "AI offline: Model load failed. Check Logcat (LlamaNative)."
            }

            Log.i(TAG, "Boot complete. Model: $modelReady")

        } catch (e: Exception) {
            Log.e(TAG, "Boot sequence failed", e)
            
            // EVEN ON TOTAL FAILURE: create agent and go to READY so the terminal works
            if (!::agentOrchestrator.isInitialized) {
                agentOrchestrator = AgentOrchestrator(
                    engine = llamaEngine,
                    toolRegistry = toolRegistry,
                    toolExecutor = toolExecutor,
                    memoryEngine = memoryEngine,
                    personalityEngine = personalityEngine,
                    skillsParser = skillsParser,
                    multimodalProcessor = multimodalProcessor,
                    scope = viewModelScope
                )
            }
            _bootProgress.value = 1f
            _bootPhase.value = BootPhase.READY
            _bootMessage.value = "Terminal ready (AI offline: ${e.message?.take(80)})"
        }
    }

    /**
     * Imports a model or projector directly from a user-selected file URI.
     * This bypasses Android 11+ Scoped Storage restrictions.
     */
    fun importModelFromUri(uri: android.net.Uri, isProjector: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _bootPhase.value = BootPhase.EXTRACTING_MODEL
            _bootMessage.value = "Importing " + if (isProjector) "Multimodal Projector..." else "AI Model..."
            _bootProgress.value = 0f
            
            try {
                val destFile = if (isProjector) modelManager.mmprojFile else modelManager.modelFile
                val cr = getApplication<Application>().contentResolver
                
                // Get estimated size if possible
                var totalSize = 5L * 1024 * 1024 * 1024 // Assume 5GB defaults
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            totalSize = cursor.getLong(sizeIndex)
                        }
                    }
                }

                cr.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(4 * 1024 * 1024) // 4MB chunks
                        var totalCopied = 0L
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalCopied += bytesRead
                            
                            val progress = totalCopied.toFloat() / totalSize
                            _bootProgress.value = progress.coerceIn(0f, 1f)
                            _bootMessage.value = "Copying... ${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
                        }
                        output.flush()
                    }
                }
                
                _bootMessage.value = "Import successful! Please restart Elysium Code."
                _bootProgress.value = 1f
                
                // If it's the main model, force a reload if possible or just require restart
                if (!isProjector) {
                    modelManager.extractModelIfNeeded() // Updates model info
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _bootMessage.value = "Import failed: ${e.message}"
            }
            
            kotlinx.coroutines.delay(3000)
            _bootPhase.value = BootPhase.READY
        }
    }

    /**
     * Scans a selected directory URI for model files and imports the first match.
     */
    fun importModelFromDirectoryUri(treeUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            
            // Search for model or zip
            val modelFiles = rootDoc.listFiles()
            var candidateUri: android.net.Uri? = null
            
            for (file in modelFiles) {
                if (file == null) continue
                val name = file.name?.lowercase() ?: ""
                if (name.contains("gemma") && (name.endsWith(".gguf") || name.endsWith(".zip"))) {
                    candidateUri = file.uri
                    break
                }
            }
            
            if (candidateUri != null) {
                importModelFromUri(candidateUri, isProjector = false)
            } else {
                _bootMessage.value = "No valid model found in selected folder."
                kotlinx.coroutines.delay(3000)
                _bootPhase.value = BootPhase.READY
            }
        }
    }

    // ═══ Agent API — Single Source of Truth ═══

    // ═══ Multimodal State ═══
    private val _pendingImageData = MutableStateFlow<ByteArray?>(null)
    val pendingImageData: StateFlow<ByteArray?> = _pendingImageData.asStateFlow()

    private val _pendingVideoData = MutableStateFlow<ByteArray?>(null)
    val pendingVideoData: StateFlow<ByteArray?> = _pendingVideoData.asStateFlow()

    private val _pendingAudioData = MutableStateFlow<ByteArray?>(null)
    val pendingAudioData: StateFlow<ByteArray?> = _pendingAudioData.asStateFlow()

    fun clearAttachments() {
        _pendingImageData.value = null
        _pendingVideoData.value = null
        _pendingAudioData.value = null
    }

    /**
     * Send message to the PRIMARY agent orchestrator (ReAct loop).
     */
    fun sendMessage(text: String) {
        if (!::agentOrchestrator.isInitialized) {
            Log.w(TAG, "AgentOrchestrator not initialized, using EnhancedAgent fallback")
            enhancedAgent.processMessage(text)
            return
        }
        
        val image = _pendingImageData.value
        val audio = _pendingAudioData.value
        // Note: Video handling depends on the underlying engine's video support; 
        // for now we pass the first frame if needed or keep for reference.
        
        agentOrchestrator.processUserMessage(text, image, audio)
        clearAttachments()
    }

    /**
     * Extracts byte data from a content URI and stores it in the pending state.
     */
    fun handleMediaUri(uri: android.net.Uri, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { 
                    it.readBytes()
                }
                when (type) {
                    "image" -> _pendingImageData.value = bytes
                    "video" -> _pendingVideoData.value = bytes
                    "audio" -> _pendingAudioData.value = bytes
                }
                Log.i(TAG, "Successfully loaded $type attachment (${bytes?.size ?: 0} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read media URI", e)
            }
        }
    }

    fun cancelGeneration() {
        if (::agentOrchestrator.isInitialized) {
            agentOrchestrator.cancelProcessing()
        }
    }

    /**
     * Resolve a pending tool execution (Approve/Deny)
     */
    fun resolvePendingToolCall(approved: Boolean) {
        if (::agentOrchestrator.isInitialized) {
            agentOrchestrator.resolvePendingToolCall(approved)
        }
    }

    fun createTerminalSession(): Boolean {
        return shellManager.createSession(shellPath = customShellPath ?: "/system/bin/sh") != null
    }

    fun sendTerminalCommand(command: String) {
        shellManager.sendCommand(command)
    }

    /**
     * Staff Level Maintenance: Completely wipes the agent's knowledge and learned history.
     */
    fun clearAgentMemory() {
        viewModelScope.launch {
            memoryEngine.clearAll()
        }
    }

    /**
     * Swaps the agent's persona and communicative directives instantly.
     */
    fun updateAgentPersonality(personalityId: String) {
        personalityEngine.setActive(personalityId)
    }

    override fun onCleared() {
        super.onCleared()
        shellManager.closeAll()
        llamaEngine.cleanup()
    }
}
