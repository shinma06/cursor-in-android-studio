package com.cursoragent.ui.session

import java.util.regex.Pattern

/** Only presentation data crosses the strip boundary; the owner retains session state. */
data class SessionTabPresentation(val id: String, val title: String = "New Agent") {
    val fullTitle: String get() = title.ifBlank { "New Agent" }
    val displayTitle: String get() = abbreviateSessionTitle(fullTitle)
}

private val grapheme = Pattern.compile("\\X")

/** Nine user-perceived characters, including combining marks and emoji sequences. */
internal fun abbreviateSessionTitle(title: String): String {
    val matcher = grapheme.matcher(title)
    var end = 0
    repeat(9) {
        if (!matcher.find()) return title
        end = matcher.end()
    }
    return if (matcher.find()) title.substring(0, end) + "…" else title
}
