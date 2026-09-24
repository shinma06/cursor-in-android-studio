# 別sessionへの独立レビュー依頼

writerと別sessionを使います。同じproviderでもよく、Claude専用ではありません。旧ファイル名はリンク互換のため残します。[共通の役割条件](../../development/github-workflow.md#正本と役割)と[Codex実行規約](../../development/codex-execution-policy.md)を守り、既存の担当・許可範囲を維持します。この依頼は子Agent生成の許可ではありません。

依頼者が`<>`を埋め、固定HEAD/baseの差分・関連コード・受入条件・根拠を添えます。説明は人間向け、code blockはAgent専用です。開発コンテキストのレビューでは[共通規則](../../architecture/knowledge.md#開発コンテキストの用途言語形式と読込条件)の該当箇所も添えます。tool無効のreviewerへ自力取得を要求しません。

```text
You are the independent reviewer for Cursor in Android Studio, in a different session from the writer.
Writer session: <session>; reviewer session: <different session>.
Target Issue: #<number>; target HEAD: <SHA>; base: <SHA>; scope: <files>.
Expected UX/acceptance: <criteria>.
Before/after facts and GUI evidence: <records, or explicitly unverified>.
Review concrete defects, regressions and verification gaps using only the supplied material below.
Do not edit source, execute commands, operate a GUI, commit/push or post Issue comments.
Report in Japanese: severity, location, reproduction conditions, impact and proposed fix.
Label unsupported conclusions as hypotheses. Never mark GUI tests you did not perform as passed.
Even if no defect is found, state the verification limits and reviewed HEAD/base.
For development-context reviews/audits, apply the supplied relevant excerpt of docs/architecture/knowledge.md's context policy. Missing evidence remains unverified; do not expand access or scope to obtain it.
<diff, relevant code, evidence and applicable policy excerpt>
```

これは提供資料だけを読む依頼例です。選んだクライアントでtoolを無効にできる場合は無効にし、利用可能な起動方法・権限を確認します。自動coordinator連携の対応範囲は[PR automation](../../development/pr-automation.md)に従い、providerごとのadapterを仮定しません。認証ファイルをコピー・抽出しません。

レビュー役から実装役へ変更する場合は、別途Issueのclaim・専用worktree・対象ファイル・base・必要テスト・PRを指定して引き継ぎます。レビュー依頼を実装権限として扱わず、その変更には別sessionのレビューが必要です。
