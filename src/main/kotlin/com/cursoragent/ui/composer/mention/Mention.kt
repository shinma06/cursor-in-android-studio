package com.cursoragent.ui.composer.mention

enum class MentionKind { FILE, FOLDER, GIT_DIFF, BRANCH, TERMINAL, DOCS, WEB }

/**
 * [displayLabel] is shown in the popup list; [insertToken] is the space-free text
 * inserted into the composer (`@<insertToken> `) so [MentionResolver] can find it
 * again with a simple `@token` regex once the user hits send.
 */
data class Mention(
    val kind: MentionKind,
    val displayLabel: String,
    val insertToken: String,
)
