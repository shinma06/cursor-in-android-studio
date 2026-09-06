package com.cursoragent.ui.session

import java.awt.FontMetrics
import java.util.regex.Pattern

/** Only presentation data crosses the strip boundary; the owner retains session state. */
data class SessionTabPresentation(val id: String, val title: String = "New Agent") {
    val fullTitle: String get() = title.ifBlank { "New Agent" }
}

private val grapheme = Pattern.compile("\\X")

/** Nine Japanese glyphs are a width reference, not a character-count limit. */
internal fun sessionTitleWidth(metrics: FontMetrics): Int = metrics.stringWidth("あ".repeat(9))

/** Measure the same font used for painting; never split a combining or emoji sequence. */
internal fun abbreviateSessionTitle(title: String, metrics: FontMetrics, maxWidth: Int): String {
    if (metrics.stringWidth(title) <= maxWidth) return title
    val ellipsis = "…"
    if (metrics.stringWidth(ellipsis) > maxWidth) return ""
    val matcher = grapheme.matcher(title)
    var end = 0
    while (matcher.find()) {
        if (metrics.stringWidth(title.substring(0, matcher.end()) + ellipsis) > maxWidth) break
        end = matcher.end()
    }
    return title.substring(0, end) + ellipsis
}
