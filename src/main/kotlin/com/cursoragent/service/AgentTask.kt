package com.cursoragent.service

/** Finite display data only. Prompt, arbitrary raw JSON, and inferred child state are excluded. */
data class AgentTask(
    val name: String? = null,
    val description: String? = null,
    val model: String? = null,
    val requestedAgentId: String? = null,
    val resumeId: String? = null,
    val agentId: String? = null,
    val reportedAgentId: String? = null,
    val durationMs: Long? = null,
    val isBackground: Boolean? = null,
    val resultText: String? = null,
    val errorText: String? = null,
)

/** A duplicate start or parent completion cannot erase a confirmed child failure/completion. */
internal fun taskStatus(previous: String?, incoming: String?): String? = when {
    previous == "failed" || incoming == "failed" -> "failed"
    previous == "completed" -> "completed"
    else -> incoming
}

internal fun taskStatusText(status: String?, background: Boolean? = null): String = when (status) {
    "pending" -> "待機"
    "in_progress" -> "実行中"
    "completed" -> if (background == true) "背景実行（子の終了は未確認）" else "完了"
    "failed" -> "失敗"
    "unconfirmed" -> "終了を確認できません"
    else -> "状態は未取得"
}
