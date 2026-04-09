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
            // Phase 1: Initialize base systems
            _bootPhase.value = BootPhase.INITIALIZING
            _bootMessage.value = "Initializing systems..."
            _bootProgress.value = 0.05f

            llamaEngine.initialize()
            conversationManager.initialize()
            
            // Bootstrap Nivel Staff Shell (Busybox)
            customShellPath = RootfsInstaller.setupBusybox(getApplication<Application>().applicationContext)
            
            _bootProgress.value = 0.1f

            // Phase 2: Extract model from assets
            _bootPhase.value = BootPhase.EXTRACTING_MODEL
            _bootMessage.value = "Preparing AI model..."

            viewModelScope.launch {
                modelManager.extractionProgress.collect { progress ->
                    _bootProgress.value = 0.1f + (progress * 0.4f)
                }
            }

            val extracted = modelManager.extractModelIfNeeded()
            if (!extracted) {
                _bootPhase.value = BootPhase.ERROR
                _bootMessage.value = "Failed to extract model. Check storage space."
                return
            }

            // Phase 3: Load model into memory
            _bootPhase.value = BootPhase.LOADING_MODEL
            _bootMessage.value = "Loading Gemma 4 E4B into memory..."
            _bootProgress.value = 0.55f

            val modelLoaded = llamaEngine.loadModel(ModelConfig(
                modelPath = modelManager.getModelPath(),
                contextSize = 8192,
                threadCount = 8,
                gpuLayers = 0,
                useMmap = true,
                useMlock = false
            ))

            if (!modelLoaded) {
                _bootPhase.value = BootPhase.ERROR
                _bootMessage.value = "Failed to load model. Device may not have enough RAM."
                return
            }
            _bootProgress.value = 0.75f

            // Phase 4: Load memory and knowledge
            _bootPhase.value = BootPhase.LOADING_MEMORY
            _bootMessage.value = "Loading agent memory..."
            memoryEngine.initialize()
            _bootProgress.value = 0.85f

            // Phase 5: Load plugins and skills
            _bootPhase.value = BootPhase.LOADING_PLUGINS
            _bootMessage.value = "Loading personalities, skills & plugins..."
            personalityEngine.initialize()
            skillsParser.initialize()
            pluginLoader.initialize()
            mcpClient.initialize()
            _bootProgress.value = 0.95f

            // Phase 6: Universal Linux Bootstrap (PRoot + Ubuntu)
            _bootPhase.value = BootPhase.BOOTSTRAPPING_LINUX
            _bootMessage.value = "Bootstrapping Linux Environment (Staff Level)..."
            
            val busyboxPath = RootfsInstaller.setupBusybox(getApplication())
            val prootPath = RootfsInstaller.setupProot(getApplication())
            
            val rootfsPath = RootfsInstaller.setupUbuntu(getApplication()) { progress ->
                _linuxSetupProgress.value = progress
                _bootMessage.value = "Downloading Linux Rootfs: ${(progress * 100).toInt()}%"
            }

            if (rootfsPath == null) {
                Log.w(TAG, "Linux bootstrap failed, falling back to Busybox-only mode.")
            } else {
                // Staff Level Gain: Configure ToolExecutor to use the PRoot jail by default
                toolExecutor.prootPath = prootPath
                toolExecutor.rootfsDir = rootfsPath
                
                if (prootPath != null) {
                    val wrapperPath = RootfsInstaller.createStaffShellWrapper(getApplication(), prootPath, rootfsPath)
                    customShellPath = wrapperPath
                    enhancedShellManager.setEnvironment(wrapperPath, getApplication<Application>().filesDir.absolutePath)
                }
            }

            // Phase 7: Create agent orchestrator
            agentOrchestrator = AgentOrchestrator(
                engine = llamaEngine,
                toolRegistry = toolRegistry,
                toolExecutor = toolExecutor,
                memoryEngine = memoryEngine,
                personalityEngine = personalityEngine,
                skillsParser = skillsParser,
                scope = viewModelScope
            )

            _bootProgress.value = 1f
            _bootPhase.value = BootPhase.READY
            _bootMessage.value = "Elysium Code ready."

            Log.i(TAG, "Boot sequence complete. Memory: ${memoryEngine.stats.value}")

        } catch (e: Exception) {
            Log.e(TAG, "Boot sequence failed", e)
            _bootPhase.value = BootPhase.ERROR
            _bootMessage.value = "Error: ${e.message}"
        }
    }

    // ═══ Agent API — Single Source of Truth ═══

    /**
     * Send message to the PRIMARY agent orchestrator (ReAct loop).
     * This is the real deal — connects to LlamaEngine for inference,
     * uses tools, records memories, and executes autonomously.
     */
    fun sendMessage(text: String, imageData: ByteArray? = null, audioData: ByteArray? = null) {
        if (!::agentOrchestrator.isInitialized) {
            // If the full agent isn't ready yet, fallback to enhanced agent
            Log.w(TAG, "AgentOrchestrator not initialized, using EnhancedAgent fallback")
            enhancedAgent.processMessage(text)
            return
        }
        agentOrchestrator.processUserMessage(text, imageData, audioData)
    }

    /**
     * Extracts byte data from a content URI for multimodal processing.
     */
    fun handleMediaUri(uri: android.net.Uri, type: String): ByteArray? {
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { 
                it.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read media URI", e)
            null
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
