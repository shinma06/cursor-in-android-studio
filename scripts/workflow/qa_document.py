"""Human-readable QA instructions, published as a linked Markdown Issue comment."""
import re


def render_summary(change, source_url):
    """Keep executable Case text intact; move the common management checklist below it."""
    def cell(text):
        return text.replace('|', r'\|').replace('\n', '<br>')

    preparation = ('PMが指定したIDE・build・設定・試験用データを使います。' if change['cases'] else
                   'GitHubで対象PR・Checks・変更内容を閲覧できれば開始できます。製品GUI操作は不要です。')
    lines = ['## 今回の試験', '', f'対象: [Case・元の受入]({source_url})', '',
             '前提: ' + preparation + '既存のOKと待ち条件はQA本文に従います。', '']
    conditions = {}
    for case in change['cases']:
        conditions.setdefault(case['preconditions'], []).append(case['id'])
    for condition, ids in conditions.items():
        label = '共通の条件' if len(conditions) == 1 else ' / '.join(ids) + ' の条件'
        lines += [f'**{label}:** {condition}', '']
    lines += ['| 項目 | 手順 | 期待値 |', '| --- | --- | --- |']
    for case in change['cases']:
        steps = '<br>'.join(f'{i}. {cell(step)}' for i, step in enumerate(case['steps'], 1))
        route = '（Computer Use必須）' if case.get('required_execution') == 'computer_use' else ''
        lines += [f'| {cell(case["id"] + ": " + case["change"])}{route} | {steps} | {cell(case["expected"])} |']
    if not change['cases']:
        lines += ['| 文書・運用 | 対象PRの変更・Checks・独立レビューを開き、元の受入と照合する | 記録と変更内容が一致する。製品GUI操作は不要 |']
    lines += ['| main反映（PM） | main統合PRと対象変更を照合する | 未反映はpendingを維持 |', '']
    if change['cases']:
        lines += ['**送信・証拠:** 表の指定文・試験用ファイルを使用。画像・ログ・Computer Use指定はその証拠を残し、それ以外の表示/クリック操作は口頭OKです。モデル・process停止・ファイル内容はログ/実体で確認します。文面や準備が未確定ならPMが具体化してから開始します。', '',
                  '**結果:** 「Case ID・実施項目・OK／不一致／未実施」と実際の動作を伝えてください。一部のOKをCase全体のpassにはしません。', '']
    else:
        lines += ['**結果:** 確認したPR/commit・項目・結果・参照URLをこのQAへ記録してください。追加画像は不要です。', '']
    return '\n'.join(lines)


def render_document(change, source_url):
    lines = ['## 人間向け試験内容', '', f'試験内容の根拠: [対象Case・元の受入]({source_url})', '',
             '### 開始前の準備', '',
             '1. このQAの本文と最新コメントで、担当・未解決の条件・過去の結果を確認する。未解放の担当を引き継ぐ場合はPMが引継ぎを確認する。',
             '2. PMが用意した試験開始メモ（候補SHA・対象PR・配布ZIPへのリンク・IDE/CLI版・初期設定・試験用ファイル）を開く。不足はPMに準備を依頼する。GUIではPMがZIPのSHA-256とロードJARの一致を記録してから開始する。古いbuildの合格は引き継がない。',
             '3. GUI試験では指定担当または人間への引継ぎとhost共通leaseを確認する。書き換えてよい使い捨てプロジェクトを使い、開始時の設定・ファイル内容を控える。CLI送信が必要なCaseではPro/Teamsで認証済みか確認する。',
             '4. 前提が揃わなければ、そのCaseをblocked（環境・準備不足）として理由と次の担当/操作を記録する。実施できたCaseと分ける。', '']
    for case in change['cases']:
        lines += [f'### {case["id"]}: {case["change"]}', '', '**前提条件**', '', case['preconditions'], '', '**試験手順**', '']
        lines += [f'{i}. {step}' for i, step in enumerate(case['steps'], 1)]
        lines += ['', '**期待結果**', '', case['expected'], '']
        if case.get('required_execution') == 'computer_use':
            lines += ['実施経路: Computer Useの操作証拠が必要。人間の手操作だけではこのCaseをpassにしない。', '']
        lines += ['**進められない場合・再試験**', '', case['next_action'], case['recheck'], '']
    if not change['cases']:
        lines += ['### 文書・運用変更の確認', '', '**前提条件**', '',
                  'この変更には製品GUIの試験はありません。GitHubで対象PR・Checks・変更ファイルを閲覧できること。',
                  'GUI不要の理由: ' + change['reason'], '', '**試験手順**', '',
                  '1. 上の根拠リンクを開き、元PRの対象変更と受入条件を読む。',
                  '2. 元PRのChecksとレビュー記録を開き、次の検証が成功しているか確認する。コマンドの再実行が必要な場合は専用worktreeの担当へ依頼する。']
        lines += ['   - ' + check for check in change['cli_checks']]
        lines += ['3. 変更後の文書・設定と元Issueの受入を照合し、不足があれば内容と参照箇所を記録する。', '',
                  '**期待結果**', '', '必要な検証の成功記録があり、変更内容が元Issueの受入条件と一致する。製品GUI不要とmain反映済みを混同しない。', '']
    lines += ['### main反映の確認', '', '**前提条件**', '',
              '元変更を含む固定候補の試験とmain統合の確認記録があること。main反映前ならpendingのまま残す。既にmainへ入った旧変更はそのmergeを照合し、再promotionを要求しない。', '',
              '**試験手順**', '', '1. main統合PR（developからの昇格はpromotion PR）の対象候補・元PR・全必要Caseの証拠を照合する。',
              '2. 統合PRのmergeとmainの履歴で元変更が含まれることを確認し、PRのURLを結果へ記録する。コード変更を伴わない接続試験等は理由を記録してmain反映を対象外とする。', '',
              '**期待結果**', '', '確認済み候補とmainに反映された変更が一致し、未確認Caseが残っていない。', '',
              '### 結果の記録・後片付け', '',
              'Caseごとに下の書式でこのQAへコメントする。固定候補の結果は担当PMがpromotion.jsonへ反映し、既存Case JSONの履歴を書き換えない。',
              'pass = 実施して期待結果どおり、fail = 実施したが不一致、blocked = 環境/前提不足、pending = 未実施。', '',
              '```text', 'Case ID:', '候補SHA / 対象PR:', 'ZIP SHA-256 / ロード照合（GUIの場合）:',
              'IDE / CLI版（該当する場合）:', '実施者 / 日時 / 実施経路（manual・computer_use等）:',
              '結果（pass / fail / blocked / pending）:', '実際の表示・動作 / 証拠URL:',
              '修正Issue・PR / 次の担当と操作:', 'main反映のPR / 確認状況:', '```', '',
              '失敗は再現した操作と期待との差を記録し、製品不具合は専用修正Issue/PRへ渡す。修正後は新候補で再確認する。',
              '終了時は試験用入力・設定を戻し、この試験で起動したprocessを停止してleaseを解放する。秘密情報・host名・ローカル絶対パスを公開証拠に含めない。', '']
    return (render_summary(change, source_url) + '\n<details>\n<summary>詳細な前提・正式手順・記録と後片付け</summary>\n\n'
            + '\n'.join(lines) + '\n</details>\n')


def ensure_document(gh, repo, number, document):
    """Read back comment and backlink before accepting a QA handoff; safe on retry."""
    marker = '<!-- qa-human-document:v1 -->'
    payload = marker + '\n' + document
    comments = gh.comments(number)
    body = gh.issue(number).get('body') or ''
    pattern = r'<!-- qa-human-link:v1 -->\n.*?\n<!-- /qa-human-link -->'
    links = re.findall(pattern, body, re.S)
    if links:
        current = re.search(re.escape(f'https://github.com/{repo}/issues/{number}#issuecomment-') + r'(\d+)\)', links[0])
        linked = next((c for c in comments if current and c['id'] == int(current[1])), None)
        if len(links) != 1 or not linked or linked['body'] != payload:
            raise ValueError('Current QA human document changed; reconcile before retry')
    if not any(c['body'] == payload for c in comments):
        gh.comment(number, payload)
    matches = [c for c in gh.comments(number) if c['body'] == payload]
    if not matches:
        raise ValueError('QA human document readback failed')
    comment_id = min(c['id'] for c in matches)
    url = f'https://github.com/{repo}/issues/{number}#issuecomment-{comment_id}'
    link = f'<!-- qa-human-link:v1 -->\n**試験内容ドキュメント:** [前提条件・試験手順・期待結果・結果記録]({url})\n<!-- /qa-human-link -->'
    body = gh.issue(number).get('body') or ''
    updated = re.sub(pattern, lambda _: link, body, flags=re.S) if re.search(pattern, body, re.S) else link + '\n\n' + body
    if updated != body:
        gh.api(f'repos/{repo}/issues/{number}', 'PATCH', {'body': updated})
    if link not in (gh.issue(number).get('body') or ''):
        raise ValueError('QA human document link readback failed')
    return url
