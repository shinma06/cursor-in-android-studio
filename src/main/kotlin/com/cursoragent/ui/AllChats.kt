package com.cursoragent.ui

import com.intellij.util.text.matching.MatchedFragment
import com.intellij.util.text.matching.MatchingMode
import com.intellij.psi.codeStyle.NameUtil

internal data class AllChatHit(val entry: RecentChatEntry, val highlights: List<MatchedFragment> = emptyList())

/** Quick access searches every candidate before its 200-result cap; it does not use MRU order. */
internal fun searchAllChats(entries: List<RecentChatEntry>, query: String, limit: Int = 200, rankMatches: Boolean = true): List<AllChatHit> {
    val text = query.trim()
    if (text.isEmpty()) return entries.sortedByDescending { it.updatedMs }.take(limit).map(::AllChatHit)
    val matcher = NameUtil.buildMatcher("*$text", MatchingMode.IGNORE_CASE)
    val hits = entries.mapNotNull { entry ->
        if (Thread.currentThread().isInterrupted) return emptyList()
        val fragments = matcher.match(entry.title)
        val description = matcher.match(entry.description)
        if (fragments == null && description == null) return@mapNotNull null
        val score = maxOf(
            if (fragments == null) Int.MIN_VALUE else matcher.matchingDegree(entry.title),
            if (description == null) Int.MIN_VALUE else matcher.matchingDegree(entry.description),
        )
        AllChatHit(entry, fragments?.toList().orEmpty()) to score
    }
    return (if (rankMatches) hits.sortedByDescending { it.second } else hits.sortedByDescending { it.first.entry.updatedMs })
        .take(limit).map { it.first }
}
