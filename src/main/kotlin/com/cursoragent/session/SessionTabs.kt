package com.cursoragent.session

import com.cursoragent.service.AgentTransport
import com.cursoragent.settings.AgentMode
import java.util.UUID

/** Immutable view of one open tab. IDs belong to the plugin, not to the CLI. */
data class SessionTab(
    val id: String,
    val chatId: String? = null,
    val conversationId: String = UUID.randomUUID().toString(),
    val title: String = NEW_AGENT_TITLE,
    val renamedByUser: Boolean = false,
    val mode: AgentMode = AgentMode.AGENT,
    val modelId: String = "",
    val draft: String = "",
    val caret: Int = 0,
    val run: SessionRunToken? = null,
    val transport: AgentTransport = AgentTransport.PRINT,
    val transportLocked: Boolean = false,
) {
    companion object {
        const val NEW_AGENT_TITLE = "New Agent"
    }
}

/** Opaque identity; callers retain the issued token rather than constructing one. */
class SessionRunToken internal constructor(val tabId: String) {
    val turnId: String = UUID.randomUUID().toString()
}

data class SessionTurn(
    val token: SessionRunToken,
    val chatId: String?,
    val mode: AgentMode,
    val modelId: String,
    val prompt: String,
    val transport: AgentTransport = AgentTransport.PRINT,
)

data class SessionTabsSnapshot(val tabs: List<SessionTab>, val selectedId: String) {
    val selected: SessionTab get() = tabs.first { it.id == selectedId }
}

/**
 * Project-local state for open tabs. No Swing, process, filesystem or global settings access.
 * All mutations are synchronized; returned values are immutable, detached snapshots.
 * The integration layer owns timelines and processes keyed by tab ID, never selectedId.
 */
class SessionTabs(
    private val initialMode: AgentMode = AgentMode.AGENT,
    private val initialModelId: String = "",
) {
    private val tabs = mutableListOf(newTab())
    private var selectedId = tabs.first().id

    @Synchronized
    fun snapshot(): SessionTabsSnapshot = SessionTabsSnapshot(tabs.toList(), selectedId)

    @Synchronized
    fun open(chatId: String? = null, title: String? = null, conversationId: String? = null, transport: AgentTransport = AgentTransport.PRINT): SessionTab {
        require(chatId == null || chatId.isNotBlank()) { "CLI chat ID must not be blank" }
        if (conversationId != null) {
            tabs.firstOrNull { it.conversationId == conversationId }?.let { selectedId = it.id; return it }
        }
        if (chatId != null) {
            tabs.firstOrNull { it.chatId == chatId && it.transport == transport }?.let {
                selectedId = it.id
                return it
            }
        }
        val fresh = newTab()
        val tab = fresh.copy(chatId = chatId, title = cleanTitle(title) ?: SessionTab.NEW_AGENT_TITLE,
            conversationId = conversationId ?: fresh.conversationId, transport = transport, transportLocked = conversationId != null)
        tabs.add(tab)
        selectedId = tab.id
        return tab
    }

    @Synchronized
    fun select(id: String): Boolean {
        if (tabs.none { it.id == id }) return false
        selectedId = id
        return true
    }

    /**
     * Invalidates callbacks BEFORE returning. The caller must then cancel preparation and
     * destroy only the returned tab's process (including a process still being created).
     * Closing an inactive tab preserves the current selection; active close chooses right,
     * or left at the end. Closing the last tab creates a fresh empty tab.
     */
    @Synchronized
    fun close(id: String): SessionTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        val removed = tabs.removeAt(index)
        if (tabs.isEmpty()) tabs.add(newTab())
        if (selectedId == id) selectedId = tabs[index.coerceAtMost(tabs.lastIndex)].id
        return removed
    }

    /** Close only the captured IDs; never include the fresh tab created by the last close. */
    @Synchronized
    fun closeAll(ids: List<String>): List<SessionTab> = ids.mapNotNull(::close)

    /** Final zero-based index after removing the source tab. Invalid requests are no-ops. */
    @Synchronized
    fun move(id: String, targetIndex: Int): Boolean {
        val from = tabs.indexOfFirst { it.id == id }
        if (from < 0 || targetIndex !in tabs.indices || from == targetIndex) return false
        tabs.add(targetIndex, tabs.removeAt(from))
        return true
    }

    @Synchronized
    fun updateComposer(id: String, mode: AgentMode, modelId: String, draft: String, caret: Int): Boolean =
        update(id) { it.copy(mode = mode, modelId = modelId, draft = draft, caret = caret.coerceIn(0, draft.length)) }

    @Synchronized
    fun selectTransport(id: String, transport: AgentTransport): Boolean {
        val tab = tabs.firstOrNull { it.id == id } ?: return false
        if (tab.transportLocked || tab.chatId != null || tab.run != null) return false
        return update(id) { it.copy(transport = transport) }
    }

    @Synchronized
    fun rename(id: String, title: String): Boolean {
        val name = cleanTitle(title) ?: return false
        return update(id) { it.copy(title = name, renamedByUser = true) }
    }

    /**
     * Atomically snapshots the original user text and settings and clears the submitted draft.
     * A blank draft or already-running tab cannot start another turn. The caller snapshots
     * execution policy/workspace separately before background preparation (#65).
     */
    @Synchronized
    fun beginTurn(id: String): SessionTurn? {
        val tab = tabs.firstOrNull { it.id == id } ?: return null
        if (tab.run != null || tab.draft.isBlank()) return null
        val token = SessionRunToken(id)
        update(id) { it.copy(run = token, draft = "", caret = 0, transportLocked = true) }
        return SessionTurn(token, tab.chatId, tab.mode, tab.modelId, tab.draft, tab.transport)
    }

    @Synchronized
    fun accepts(token: SessionRunToken): Boolean = tabs.any { it.id == token.tabId && it.run === token }

    /** Reject duplicate CLI identity and updates from stopped, closed or superseded turns. */
    @Synchronized
    fun bindChat(token: SessionRunToken, chatId: String): Boolean {
        if (chatId.isBlank() || !accepts(token)) return false
        if (tabs.any { it.id != token.tabId && it.chatId == chatId && it.transport == tabs.first { tab -> tab.id == token.tabId }.transport }) return false
        val tab = tabs.first { it.id == token.tabId }
        if (tab.chatId != null && tab.chatId != chatId) return false
        return update(token.tabId) { it.copy(chatId = chatId) }
    }

    /** This accepts a verified provider title, never a title guessed from the prompt. */
    @Synchronized
    fun applyAutomaticTitle(token: SessionRunToken, title: String): Boolean {
        val name = cleanTitle(title) ?: return false
        if (!accepts(token) || tabs.first { it.id == token.tabId }.renamedByUser) return false
        return update(token.tabId) { it.copy(title = name) }
    }

    /** Caller has verified that the initial ACP prompt was never dispatched. Metadata alone must not lock transport. */
    @Synchronized
    fun abortUnsentAcpTurn(token: SessionRunToken): Boolean {
        if (!accepts(token)) return false
        val tab = tabs.first { it.id == token.tabId }
        if (tab.transport != AgentTransport.ACP || tab.chatId != null) return false
        return update(tab.id) { it.copy(run = null, transportLocked = false) }
    }

    /** Finish/stop invalidate the token. Repeated terminal/error callbacks are harmless. */
    @Synchronized
    fun finishTurn(token: SessionRunToken): Boolean {
        if (!accepts(token)) return false
        return update(token.tabId) { it.copy(run = null) }
    }

    @Synchronized
    fun stop(id: String): SessionRunToken? {
        val token = tabs.firstOrNull { it.id == id }?.run ?: return null
        finishTurn(token)
        return token
    }

    /** Invalidate all callback routes before the integration destroys project processes. */
    @Synchronized
    fun stopAll(): List<SessionRunToken> {
        val tokens = tabs.mapNotNull { it.run }
        tokens.forEach(::finishTurn)
        return tokens
    }

    private fun update(id: String, transform: (SessionTab) -> SessionTab): Boolean {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false
        tabs[index] = transform(tabs[index])
        return true
    }

    private fun newTab() = SessionTab(
        id = UUID.randomUUID().toString(),
        mode = initialMode,
        modelId = initialModelId,
    )

    private fun cleanTitle(title: String?): String? = title?.trim()?.takeIf { it.isNotEmpty() }
}
