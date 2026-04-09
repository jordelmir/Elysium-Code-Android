package com.elysium.code.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — ConversationManager
 * ═══════════════════════════════════════════════════════════════
 *
 * Persistent conversation storage. Saves and loads chat sessions
 * so the agent maintains context across app restarts.
 */
class ConversationManager(private val context: Context) {

    companion object {
        private const val CONVERSATIONS_DIR = "conversations"
        private const val MAX_CONVERSATIONS = 100
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val convDir: File
        get() = File(context.filesDir, CONVERSATIONS_DIR).also { it.mkdirs() }

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    fun initialize() {
        loadConversationList()
    }

    /**
     * Save a conversation
     */
    fun saveConversation(
        id: String,
        title: String,
        messages: List<ChatMessage>
    ) {
        val conversation = SavedConversation(
            id = id,
            title = title,
            messages = messages,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val file = File(convDir, "$id.json")
        file.writeText(json.encodeToString(conversation))

        loadConversationList()
    }

    /**
     * Load a conversation by ID
     */
    fun loadConversation(id: String): SavedConversation? {
        val file = File(convDir, "$id.json")
        if (!file.exists()) return null

        return try {
            json.decodeFromString<SavedConversation>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete a conversation
     */
    fun deleteConversation(id: String) {
        File(convDir, "$id.json").delete()
        loadConversationList()
    }

    /**
     * Create a new conversation and return its ID
     */
    fun createNewConversation(title: String = "New Chat"): String {
        val id = "conv_${System.currentTimeMillis()}"
        saveConversation(id, title, emptyList())
        return id
    }

    /**
     * Export conversation as markdown
     */
    fun exportAsMarkdown(id: String): String? {
        val conv = loadConversation(id) ?: return null

        return buildString {
            appendLine("# ${conv.title}")
            appendLine("*${java.util.Date(conv.createdAt)}*")
            appendLine()

            conv.messages.forEach { msg ->
                when (msg.role) {
                    MessageRole.USER -> {
                        appendLine("## 👤 User")
                        appendLine(msg.content)
                    }
                    MessageRole.ASSISTANT -> {
                        appendLine("## 🤖 Elysium")
                        appendLine(msg.content)
                    }
                    MessageRole.TOOL -> {
                        appendLine("## ⚙️ Tool: ${msg.toolName}")
                        appendLine("```")
                        appendLine(msg.content)
                        appendLine("```")
                    }
                    MessageRole.SYSTEM -> {
                        appendLine("> ℹ️ ${msg.content}")
                    }
                }
                appendLine()
            }
        }
    }

    private fun loadConversationList() {
        val files = convDir.listFiles()?.filter { it.extension == "json" } ?: return

        val summaries = files.mapNotNull { file ->
            try {
                val conv = json.decodeFromString<SavedConversation>(file.readText())
                ConversationSummary(
                    id = conv.id,
                    title = conv.title,
                    messageCount = conv.messages.size,
                    lastMessage = conv.messages.lastOrNull()?.content?.take(100) ?: "",
                    updatedAt = conv.updatedAt
                )
            } catch (e: Exception) { null }
        }.sortedByDescending { it.updatedAt }

        _conversations.value = summaries.take(MAX_CONVERSATIONS)
    }
}

@Serializable
data class SavedConversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val lastMessage: String,
    val updatedAt: Long
)
