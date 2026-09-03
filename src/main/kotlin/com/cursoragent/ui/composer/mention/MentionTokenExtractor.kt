package com.cursoragent.ui.composer.mention

/**
 * Matches the space-free `@token` text [MentionPopupController] inserts (e.g.
 * `@src/Foo.kt`, `@docs/`, `@git-diff`) — deliberately not the arbitrary text a user
 * could type by hand after a literal `@`, since that's ambiguous (could be an email,
 * a decorator, etc.) and the doc treats manual `@` typing as best-effort only.
 */
private val MENTION_TOKEN_PATTERN = Regex("""(?<![\w/.-])@([\w./-]+)""")

object MentionTokenExtractor {
    fun extractTokens(promptText: String): List<String> =
        MENTION_TOKEN_PATTERN.findAll(promptText).map { it.groupValues[1] }.distinct().toList()
}
