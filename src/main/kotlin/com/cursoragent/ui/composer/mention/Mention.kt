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

/** Typed attachments preserve paths containing spaces; legacy text mentions remain best effort. */
internal fun contextMentions(prompt: String, explicit: List<Mention>): List<Mention> {
    val legacy = MentionTokenExtractor.extractTokens(prompt).map { token ->
        val kind = when (token) {
            "git-diff" -> MentionKind.GIT_DIFF
            "branch" -> MentionKind.BRANCH
            "terminal" -> MentionKind.TERMINAL
            "docs" -> MentionKind.DOCS
            "web" -> MentionKind.WEB
            else -> if (token.endsWith('/')) MentionKind.FOLDER else MentionKind.FILE
        }
        Mention(kind, token, token)
    }
    return (explicit + legacy).distinctBy { it.kind to it.insertToken.removePrefix("./") }
}
