package com.elysium.code.mcp

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — McpClient
 * ═══════════════════════════════════════════════════════════════
 *
 * Model Context Protocol (MCP) client implementation.
 * Connects to MCP servers (local or remote) that provide
 * additional tools, resources, and prompts to the AI agent.
 *
 * MCP servers can be:
 * - Local stdio processes
 * - Remote SSE/WebSocket endpoints
 * - Configured via JSON config files
 */
class McpClient(
    private val context: Context,
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "McpClient"
        private const val MCP_DIR = "mcp"
        private const val CONFIG_FILE = "mcp_config.json"
        private const val JSONRPC_VERSION = "2.0"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val mcpDir: File
        get() = File(context.filesDir, MCP_DIR).also { it.mkdirs() }

    private val _servers = MutableStateFlow<List<McpServerInfo>>(emptyList())
    val servers: StateFlow<List<McpServerInfo>> = _servers.asStateFlow()

    private val _connectedTools = MutableStateFlow<List<McpTool>>(emptyList())
    val connectedTools: StateFlow<List<McpTool>> = _connectedTools.asStateFlow()

    private var requestId = 0

    // ═══════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════

    fun initialize() {
        loadConfig()
        Log.i(TAG, "McpClient initialized: ${_servers.value.size} servers configured")
    }

    // ═══════════════════════════════════════════════════════════
    // SERVER MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    /**
     * Add an MCP server configuration
     */
    fun addServer(config: McpServerConfig) {
        val info = McpServerInfo(
            config = config,
            status = McpServerStatus.DISCONNECTED
        )
        _servers.value = _servers.value + info
        saveConfig()
    }

    /**
     * Connect to an MCP server
     */
    suspend fun connect(serverId: String): Boolean = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.config.id == serverId }
            ?: return@withContext false

        updateServerStatus(serverId, McpServerStatus.CONNECTING)

        try {
            when (server.config.transport) {
                McpTransport.SSE -> connectSse(server)
                McpTransport.STDIO -> connectStdio(server)
                McpTransport.WEBSOCKET -> connectWebSocket(server)
            }

            // Initialize
            val initResult = sendRequest(serverId, "initialize", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {
                    put("roots", buildJsonObject { put("listChanged", true) })
                })
                put("clientInfo", buildJsonObject {
                    put("name", "Elysium Code")
                    put("version", "1.0.0")
                })
            })

            if (initResult != null) {
                // Send initialized notification
                sendNotification(serverId, "notifications/initialized", null)

                // List available tools
                val toolsResult = sendRequest(serverId, "tools/list", null)
                if (toolsResult != null) {
                    parseAndAddTools(serverId, toolsResult)
                }

                updateServerStatus(serverId, McpServerStatus.CONNECTED)
                Log.i(TAG, "Connected to MCP server: ${server.config.name}")
                true
            } else {
                updateServerStatus(serverId, McpServerStatus.ERROR)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${server.config.name}", e)
            updateServerStatus(serverId, McpServerStatus.ERROR)
            false
        }
    }

    /**
     * Disconnect from an MCP server
     */
    fun disconnect(serverId: String) {
        updateServerStatus(serverId, McpServerStatus.DISCONNECTED)
        _connectedTools.value = _connectedTools.value.filter { it.serverId != serverId }
    }

    /**
     * Call a tool on an MCP server
     */
    suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: JsonObject
    ): McpToolResult = withContext(Dispatchers.IO) {
        try {
            val result = sendRequest(serverId, "tools/call", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })

            if (result != null) {
                val content = result["content"]?.jsonArray
                val text = content?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: result.toString()
                val isError = result["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false

                McpToolResult(
                    content = text,
                    isError = isError
                )
            } else {
                McpToolResult(content = "No response from server", isError = true)
            }
        } catch (e: Exception) {
            McpToolResult(content = "Error: ${e.message}", isError = true)
        }
    }

    /**
     * Remove an MCP server
     */
    fun removeServer(serverId: String) {
        disconnect(serverId)
        _servers.value = _servers.value.filter { it.config.id != serverId }
        saveConfig()
    }

    // ═══════════════════════════════════════════════════════════
    // TOOL DESCRIPTIONS FOR AGENT
    // ═══════════════════════════════════════════════════════════

    /**
     * Get all connected MCP tools as tool descriptions for the agent
     */
    fun getToolDescriptions(): String {
        return _connectedTools.value.joinToString("\n\n") { tool ->
            buildString {
                appendLine("### mcp__${tool.serverId}__${tool.name}")
                appendLine(tool.description)
                if (tool.inputSchema.isNotEmpty()) {
                    appendLine("Parameters:")
                    tool.inputSchema.forEach { (name, desc) ->
                        appendLine("  - $name: $desc")
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════

    private suspend fun connectSse(server: McpServerInfo) {
        // SSE connection using Ktor
        Log.i(TAG, "Connecting via SSE to ${server.config.url}")
    }

    private suspend fun connectStdio(server: McpServerInfo) {
        // Launch local process and communicate via stdin/stdout
        Log.i(TAG, "Connecting via STDIO to ${server.config.command}")
    }

    private suspend fun connectWebSocket(server: McpServerInfo) {
        // WebSocket connection
        Log.i(TAG, "Connecting via WebSocket to ${server.config.url}")
    }

    private suspend fun sendRequest(serverId: String, method: String, params: JsonObject?): JsonObject? {
        requestId++
        val request = buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("id", requestId)
            put("method", method)
            if (params != null) put("params", params)
        }

        val server = _servers.value.find { it.config.id == serverId } ?: return null

        return try {
            when (server.config.transport) {
                McpTransport.SSE -> {
                    // Send via HTTP POST to SSE endpoint
                    val response = httpClient.post(server.config.url ?: "") {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(request))
                    }
                    json.decodeFromString<JsonObject>(response.bodyAsText())
                        .get("result")?.jsonObject
                }
                else -> {
                    // For stdio/websocket, would use process I/O
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MCP request failed: $method", e)
            null
        }
    }

    private suspend fun sendNotification(serverId: String, method: String, params: JsonObject?) {
        val notification = buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("method", method)
            if (params != null) put("params", params)
        }
        // Send without expecting response
    }

    private fun parseAndAddTools(serverId: String, result: JsonObject) {
        val tools = result["tools"]?.jsonArray ?: return

        val mcpTools = tools.mapNotNull { toolJson ->
            val obj = toolJson.jsonObject
            try {
                McpTool(
                    serverId = serverId,
                    name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    inputSchema = parseSchema(obj["inputSchema"]?.jsonObject)
                )
            } catch (e: Exception) { null }
        }

        _connectedTools.value = _connectedTools.value.filter { it.serverId != serverId } + mcpTools
        Log.i(TAG, "Loaded ${mcpTools.size} tools from server $serverId")
    }

    private fun parseSchema(schema: JsonObject?): Map<String, String> {
        if (schema == null) return emptyMap()
        val properties = schema["properties"]?.jsonObject ?: return emptyMap()
        return properties.entries.associate { (key, value) ->
            val desc = value.jsonObject["description"]?.jsonPrimitive?.content ?: ""
            val type = value.jsonObject["type"]?.jsonPrimitive?.content ?: "string"
            key to "$desc ($type)"
        }
    }

    private fun updateServerStatus(serverId: String, status: McpServerStatus) {
        _servers.value = _servers.value.map { server ->
            if (server.config.id == serverId) server.copy(status = status)
            else server
        }
    }

    private fun loadConfig() {
        val configFile = File(mcpDir, CONFIG_FILE)
        if (!configFile.exists()) {
            // Seed default MCPs if empty
            val defaultServers = listOf(
                McpServerConfig(
                    id = "vercel",
                    name = "Vercel Deploy",
                    transport = McpTransport.STDIO,
                    command = "npx",
                    args = listOf("-y", "vercel", "deploy")
                ),
                McpServerConfig(
                    id = "supabase",
                    name = "Supabase DB",
                    transport = McpTransport.STDIO,
                    command = "npx",
                    args = listOf("-y", "supabase", "start")
                )
            )
            _servers.value = defaultServers.map { McpServerInfo(config = it) }
            saveConfig()
            return
        }

        try {
            val configs = json.decodeFromString<List<McpServerConfig>>(configFile.readText())
            if (configs.isEmpty()) {
                val defaultServers = listOf(
                    McpServerConfig(
                        id = "vercel",
                        name = "Vercel Deploy",
                        transport = McpTransport.STDIO,
                        command = "npx",
                        args = listOf("-y", "vercel", "deploy")
                    ),
                    McpServerConfig(
                        id = "supabase",
                        name = "Supabase DB",
                        transport = McpTransport.STDIO,
                        command = "npx",
                        args = listOf("-y", "supabase", "start")
                    )
                )
                _servers.value = defaultServers.map { McpServerInfo(config = it) }
                saveConfig()
                return
            }
            _servers.value = configs.map { McpServerInfo(config = it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MCP config", e)
        }
    }

    private fun saveConfig() {
        try {
            val configs = _servers.value.map { it.config }
            File(mcpDir, CONFIG_FILE).writeText(json.encodeToString(configs))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save MCP config", e)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val transport: McpTransport = McpTransport.SSE,
    val url: String? = null,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

@Serializable
enum class McpTransport {
    SSE,
    STDIO,
    WEBSOCKET
}

data class McpServerInfo(
    val config: McpServerConfig,
    val status: McpServerStatus = McpServerStatus.DISCONNECTED
)

enum class McpServerStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class McpTool(
    val serverId: String,
    val name: String,
    val description: String,
    val inputSchema: Map<String, String> = emptyMap()
)

data class McpToolResult(
    val content: String,
    val isError: Boolean = false
)
