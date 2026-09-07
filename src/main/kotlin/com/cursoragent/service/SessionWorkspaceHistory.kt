package com.cursoragent.service

import java.util.concurrent.ConcurrentHashMap

/** Session identity never borrows the selected tab's root; conflicting provenance stays unknown. */
class SessionWorkspaceHistory {
    private val targets = ConcurrentHashMap<String, RestoreTarget>()

    fun find(chatId: String): RestoreTarget? = targets[chatId]

    fun record(chatId: String, target: RestoreTarget) {
        targets.compute(chatId) { _, previous ->
            when {
                previous == null -> target
                previous == target -> previous
                else -> RestoreTarget.UNKNOWN
            }
        }
    }
}
