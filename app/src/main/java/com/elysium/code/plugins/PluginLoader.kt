package com.elysium.code.plugins

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — PluginLoader
 * ═══════════════════════════════════════════════════════════════
 *
 * Manages plugins that extend Elysium's capabilities. Plugins
 * can add new tools, MCP servers, skills, and UI components.
 *
 * Plugin structure:
 * /elysium/plugins/<plugin-name>/
 *   ├── plugin.json       (manifest)
 *   ├── skills/            (skill .md files)
 *   ├── personality.md     (optional personality override)
 *   ├── tools.json         (custom tool definitions)
 *   └── mcp_config.json    (MCP server configuration)
 */
class PluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "PluginLoader"
        private const val PLUGINS_DIR = "plugins"
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val pluginsDir: File
        get() = File(context.filesDir, PLUGINS_DIR).also { it.mkdirs() }

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins.asStateFlow()

    fun initialize() {
        scanPlugins()
        Log.i(TAG, "PluginLoader initialized: ${_plugins.value.size} plugins found")
    }

    /**
     * Scan the plugins directory and load manifests
     */
    fun scanPlugins() {
        val pluginList = mutableListOf<PluginInfo>()

        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val manifestFile = File(dir, "plugin.json")
            if (manifestFile.exists()) {
                try {
                    val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
                    pluginList.add(PluginInfo(
                        manifest = manifest,
                        path = dir.absolutePath,
                        isEnabled = loadPluginState(manifest.id)
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load plugin at ${dir.name}: ${e.message}")
                }
            }
        }

        _plugins.value = pluginList
    }

    /**
     * Install a plugin from a directory or zip
     */
    suspend fun installPlugin(sourcePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            if (!source.exists()) return@withContext false

            val manifest = File(source, "plugin.json")
            if (!manifest.exists()) {
                Log.e(TAG, "No plugin.json found in $sourcePath")
                return@withContext false
            }

            val pluginManifest = json.decodeFromString<PluginManifest>(manifest.readText())
            val targetDir = File(pluginsDir, pluginManifest.id)
            
            source.copyRecursively(targetDir, overwrite = true)
            scanPlugins()
            
            Log.i(TAG, "Plugin installed: ${pluginManifest.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Plugin installation failed", e)
            false
        }
    }

    fun enablePlugin(pluginId: String) {
        savePluginState(pluginId, true)
        _plugins.value = _plugins.value.map {
            if (it.manifest.id == pluginId) it.copy(isEnabled = true) else it
        }
    }

    fun disablePlugin(pluginId: String) {
        savePluginState(pluginId, false)
        _plugins.value = _plugins.value.map {
            if (it.manifest.id == pluginId) it.copy(isEnabled = false) else it
        }
    }

    fun uninstallPlugin(pluginId: String) {
        File(pluginsDir, pluginId).deleteRecursively()
        scanPlugins()
        Log.i(TAG, "Plugin uninstalled: $pluginId")
    }

    /**
     * Get all skills from enabled plugins
     */
    fun getPluginSkills(): List<File> {
        return _plugins.value
            .filter { it.isEnabled }
            .flatMap { plugin ->
                val skillsDir = File(plugin.path, "skills")
                skillsDir.listFiles()?.filter { it.extension == "md" }?.toList() ?: emptyList()
            }
    }

    /**
     * Get MCP configurations from enabled plugins
     */
    fun getPluginMcpConfigs(): List<File> {
        return _plugins.value
            .filter { it.isEnabled }
            .mapNotNull { plugin ->
                val mcpConfig = File(plugin.path, "mcp_config.json")
                if (mcpConfig.exists()) mcpConfig else null
            }
    }

    /**
     * Create a new empty plugin scaffold
     */
    fun createPluginScaffold(name: String, description: String): String {
        val id = name.lowercase().replace(Regex("[^a-z0-9]"), "-")
        val dir = File(pluginsDir, id)
        dir.mkdirs()
        File(dir, "skills").mkdirs()

        val manifest = PluginManifest(
            id = id,
            name = name,
            version = "1.0.0",
            description = description,
            author = "User",
            minElysiumVersion = "1.0.0"
        )

        File(dir, "plugin.json").writeText(json.encodeToString(manifest))
        scanPlugins()

        return dir.absolutePath
    }

    private fun loadPluginState(pluginId: String): Boolean {
        val prefs = context.getSharedPreferences("elysium_plugins", Context.MODE_PRIVATE)
        return prefs.getBoolean("plugin_$pluginId", true)
    }

    private fun savePluginState(pluginId: String, enabled: Boolean) {
        context.getSharedPreferences("elysium_plugins", Context.MODE_PRIVATE)
            .edit().putBoolean("plugin_$pluginId", enabled).apply()
    }
}

@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val minElysiumVersion: String = "1.0.0",
    val tools: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val mcpServers: List<String> = emptyList()
)

data class PluginInfo(
    val manifest: PluginManifest,
    val path: String,
    val isEnabled: Boolean = true
)
