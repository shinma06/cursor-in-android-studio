package com.cursoragent.service

import com.cursoragent.settings.WorktreeMode

/** Capture only values on the EDT; resolve filesystem identity during background preparation. */
class TurnWorkspace(
    private val requestedRoot: String?,
    val mode: WorktreeMode,
    val resumeId: String?,
    private val resumedTarget: RestoreTarget? = null,
) {
    val commandTarget: RestoreTarget by lazy { RestoreTarget.capture(requestedRoot, mode) }
    val restoreTarget: RestoreTarget by lazy {
        val target = commandTarget
        when {
            mode == WorktreeMode.ISOLATED -> target
            resumeId == null -> target
            resumedTarget == target -> target
            else -> RestoreTarget.UNKNOWN
        }
    }

    /** Uses the same immutable workspace/mode that the checkpoint and cards refer to. */
    fun arguments(): List<String> {
        val root = requireNotNull(commandTarget.rootPath) { "プロジェクトルートが取得できません" }
        return buildList {
            addAll(listOf("--workspace", root))
            if (mode.useIsolatedWorktree) add("-w")
            resumeId?.let { addAll(listOf("--resume", it)) }
        }
    }
}

class PreparedAgentTurn(
    val run: AgentRun,
    val workspace: TurnWorkspace,
    val preparation: WorkspaceOperationGate.Preparation,
)
