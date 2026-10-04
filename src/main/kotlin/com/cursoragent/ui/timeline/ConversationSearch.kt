package com.cursoragent.ui.timeline

import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException
import java.util.concurrent.CancellationException

internal data class ConversationFindOptions(val matchCase: Boolean = false, val wholeWord: Boolean = false, val regex: Boolean = false)
internal data class ConversationFindHit(val document: Int, val start: Int, val end: Int)
internal data class ConversationFindResult(val hits: List<ConversationFindHit>, val notice: String? = null)

/** Search displayed text, never HTML source. Each message remains an independent regex input. */
internal fun findConversationMatches(
    documents: List<String>,
    query: String,
    options: ConversationFindOptions,
    maxMatches: Int = 10_000,
    budgetNanos: Long = 250_000_000,
): ConversationFindResult {
    if (query.isEmpty()) return ConversationFindResult(emptyList())
    if (query.length > 4_096) return ConversationFindResult(emptyList(), "検索文字列は4,096文字以内にしてください。")
    if (options.regex && exceedsRegexExpansion(query)) return ConversationFindResult(emptyList(), "正規表現の繰り返しが多すぎます。条件を簡単にしてください。")
    val hits = mutableListOf<ConversationFindHit>()
    val deadline = System.nanoTime() + budgetNanos
    fun checkBudget() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
        if (System.nanoTime() - deadline >= 0) throw SearchBudgetExceeded()
    }
    try {
        checkBudget()
        val flags = if (options.matchCase) 0 else Pattern.CASE_INSENSITIVE
        val pattern = Pattern.compile(if (options.regex) query else Pattern.quote(query), flags)
        documents.forEachIndexed { index, text ->
            checkBudget()
            val matcher = pattern.matcher(CheckedSearchText(text, ::checkBudget))
            while (matcher.find()) {
                checkBudget()
                val start = matcher.start()
                val end = matcher.end()
                if (options.wholeWord && (wordBefore(text, start) || wordAfter(text, end))) continue
                if (hits.size == maxMatches) return ConversationFindResult(hits, "一致が多いため先頭 $maxMatches 件を表示しています。条件を絞ってください。")
                hits.add(ConversationFindHit(index, start, end))
            }
        }
    } catch (_: SearchBudgetExceeded) {
        return ConversationFindResult(hits, "検索を時間上限で中断しました。表示は途中までの結果です。条件を絞ってください。")
    } catch (_: PatternSyntaxException) {
        return ConversationFindResult(emptyList(), "正規表現が不正または非対応です。先読み・後読み・後方参照などは使えません。")
    } catch (_: StackOverflowError) {
        return ConversationFindResult(emptyList(), "正規表現が複雑すぎます。条件を簡単にしてください。")
    }
    return ConversationFindResult(hits)
}

private fun wordBefore(text: String, offset: Int) = offset > 0 && wordCodePoint(text.codePointBefore(offset))
private fun wordAfter(text: String, offset: Int) = offset < text.length && wordCodePoint(text.codePointAt(offset))
private fun wordCodePoint(value: Int) = Character.isLetterOrDigit(value) || value == '_'.code ||
    when (Character.getType(value)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt(), Character.CONNECTOR_PUNCTUATION.toInt() -> true
        else -> false
    }

private class SearchBudgetExceeded : RuntimeException(null, null, false, false)

/** RE2/J bounds matching complexity; input reads also enforce our deadline and cancellation. */
private class CheckedSearchText(
    private val text: String,
    private val check: () -> Unit,
    private val start: Int = 0,
    private val end: Int = text.length,
) : CharSequence {
    override val length: Int get() { check(); return end - start }
    override fun get(index: Int): Char {
        check()
        require(index in 0 until length)
        return text[start + index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        check()
        require(startIndex in 0..endIndex && endIndex <= length)
        return CheckedSearchText(text, check, start + startIndex, start + endIndex)
    }
    override fun toString(): String { check(); return text.substring(start, end) }
}

// RE2/J 1.8 bounds each count but expands nested counted repetitions during compilation.
// Conservatively multiply every numeric repeat, including disjoint ones and those in a class.
// Quoted/escaped literals are skipped. Keep compilation bounded before starting the matcher.
private val regexRepeatTokens = Regex("""\\Q.*?(?:\\E|$)|\\.|\{([0-9]+)(?:,([0-9]*))?}""", RegexOption.DOT_MATCHES_ALL)
private fun exceedsRegexExpansion(query: String): Boolean {
    var estimate = query.length.coerceAtLeast(1)
    for (token in regexRepeatTokens.findAll(query)) {
        val lower = token.groups[1]?.value ?: continue
        val upper = token.groups[2]?.value
        val count = (if (upper.isNullOrEmpty()) lower else upper).toIntOrNull() ?: return true
        val factor = count.coerceAtLeast(1)
        if (factor > 1_000 || estimate > 65_536 / factor) return true
        estimate *= factor
    }
    return false
}
