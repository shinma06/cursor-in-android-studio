package com.cursoragent.ui.composer.mention

enum class MentionKind { FILE, FOLDER, GIT_DIFF, BRANCH, TERMINAL, DOCS, WEB }

/**
 * [displayLabel] is shown in the popup; [insertToken] is the reference identity.
 * Typed attachments preserve the whole identity, including whitespace. Legacy
 * manually typed @tokens remain a best-effort compatibility path.
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

fun Mention.contextDescription(): String = when (kind) {
    MentionKind.FILE -> "ファイル: $insertToken\n送信開始時のエディター内容（未保存の変更を含む）を添付します。"
    MentionKind.FOLDER -> "フォルダ: $insertToken\n直下の名前一覧を添付します。配下の全ファイル本文は含みません。"
    MentionKind.GIT_DIFF -> "送信開始時のstaged/unstaged差分を添付します。"
    MentionKind.BRANCH -> "送信開始時の現在branchと基準branchの差分を添付します。"
    MentionKind.TERMINAL -> "Terminalの直近出力を添付します。Terminalが無効・未起動なら取得できない旨を送ります。"
    MentionKind.DOCS -> "Docsツール利用のhintのみ。文書の検索・取得はまだ実行していません。"
    MentionKind.WEB -> "Webツール利用のhintのみ。検索・ページ取得はまだ実行していません。"
}
