package com.cursoragent.ui.composer.command

import com.cursoragent.service.CommandCatalog
import com.cursoragent.settings.PermissionMode
import com.cursoragent.settings.SandboxMode
import com.cursoragent.settings.WorktreeMode

/** Mode/model are configured at each prompt; connection policy/root/executable belong to the unsent draft. */
internal data class AcpCommandKey(
    val root: String?,
    val executable: String,
    val permission: PermissionMode,
    val sandbox: SandboxMode,
    val workspace: WorktreeMode,
)

/** Owned on EDT by one tab. Each replacement invalidates all callbacks from its old connection. */
internal class AcpCommandConnection {
    var imageSupported: Boolean? = null
        private set
    fun updateImageSupport(ticket: Long, value: Boolean?): Boolean {
        if (!isCurrent(ticket)) return false
        imageSupported = value
        return true
    }
    fun isCurrent(ticket: Long): Boolean = ticket == generation && key != null

    val revision: Long get() = generation
    private var generation = 0L
    private var key: AcpCommandKey? = null
    var catalog: CommandCatalog = CommandCatalog.Unavailable
        private set

    fun replace(next: AcpCommandKey, force: Boolean = false): Long? {
        if (!force && key == next) return null
        key = next
        imageSupported = null
        catalog = CommandCatalog.Loading
        return ++generation
    }

    fun update(ticket: Long, value: CommandCatalog): Boolean {
        if (!isCurrent(ticket)) return false
        catalog = value
        return true
    }

    fun clear() { imageSupported = null; ++generation; key = null; catalog = CommandCatalog.Unavailable }
}
