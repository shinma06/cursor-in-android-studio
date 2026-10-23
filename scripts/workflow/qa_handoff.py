"""Read-back verified QA transfer. Called only by the single coordinator after merge."""
import json
import re
from issue_schema import validate_issue
from verification import field, metadata, validate_change


def handoff(gh, repo, pr, origin, change):
    number, path, mode = metadata(pr)
    if mode != 'develop' or not pr.get('merged') or not re.fullmatch(r'[0-9a-f]{40}', pr.get('merge_commit_sha', '')):
        raise ValueError('QA transfer requires a confirmed develop merge')
    axes = validate_issue(origin)
    validate_change(change, number, field(pr['body'], 'GUI') == 'required')
    marker = f'<!-- issue-qa-handoff:v1 origin={number} -->'
    qa_prefixes = (f'test: #{number} ', f'[試験] #{number} ')
    # List all Issues (not search indexing) so a lost create response is retryable.
    candidates = [x for x in gh.pages(f'repos/{repo}/issues?state=all&per_page=100')
                  if 'pull_request' not in x and (marker in (x.get('body') or '') or x.get('title', '').startswith(qa_prefixes))]
    if len(candidates) > 1:
        raise ValueError('Multiple QA Issues match origin; reconcile before closing')
    record = f'<!-- issue-qa-record:v1 origin={number} pr={pr["number"]} merge={pr["merge_commit_sha"]} -->'
    source = (record + f'\n元Issue: #{number}\nPR: #{pr["number"]}\nmerge SHA: {pr["merge_commit_sha"]}\n'
              f'Verification: https://github.com/{repo}/blob/{pr["merge_commit_sha"]}/{path}\n')
    if not change['cases']:
        if candidates:
            raise ValueError('Existing QA requires explicit reconciliation before a no-Case transfer')
        receipt = source + ('\nCaseなし。独立した残試験なし。main反映は未確認でPMが保管し、'
                            '次の適用可能な固定候補/promotion PRとmain履歴へ対応付ける。'
                            'QAは作成せず、この元Issue/PRの記録を引き継ぐ。GUI合格を意味しない。')
        for target in (number, pr['number']):
            if not any(c['body'] == receipt for c in gh.comments(target)):
                gh.comment(target, receipt)
            if not any(c['body'] == receipt for c in gh.comments(target)):
                raise ValueError('No-Case main custody readback failed')
        return None
    payload = source + ('\n受入・次操作: 固定mergeの全Case・前提・手順・期待・actor・再確認を正本として'
                        'PMが候補/buildと未達を照合し、必要な残試験とmain反映を割り当てる。'
                        '元IssueのcloseはGUI/main合格ではない。\nCase: ' +
                        ', '.join(case['id'] for case in change['cases']))
    milestone = origin.get('milestone')
    if not candidates:
        summary = re.sub(r'^\[[^]]+\]\s*|^[a-z]+: ', '', origin['title'])
        data = {'title': f'test: #{number} {summary}', 'body': marker + '\n' + payload,
                'labels': ['type:qa', 'priority:' + axes['priority'], 'status:ready']}
        validate_issue(dict(data, state='open'), allow_legacy=False)
        if milestone:
            data['milestone'] = milestone['number']
        qa = gh.api(f'repos/{repo}/issues', 'POST', data)
    else:
        qa = candidates[0]
    qa = gh.issue(qa['number'])
    if validate_issue(qa)['type'] != 'qa' or marker not in (qa.get('body') or '') or not qa['title'].startswith(qa_prefixes):
        raise ValueError('QA identity readback failed')
    if qa['state'] != 'open':
        raise ValueError('Existing QA is closed; reconcile remaining verification before closure')
    if milestone and (qa.get('milestone') or {}).get('number') != milestone['number']:
        raise ValueError('QA milestone differs from origin; reconcile before closure')
    children_path = f'repos/{repo}/issues/{number}/sub_issues'
    if not any(x['id'] == qa['id'] for x in gh.pages(children_path + '?per_page=100')):
        gh.api(children_path, 'POST', {'sub_issue_id': qa['id']})
    if not any(x['id'] == qa['id'] for x in gh.pages(children_path + '?per_page=100')):
        raise ValueError('QA parent relationship readback failed')
    if payload not in qa['body']:
        comments = gh.comments(qa['number'])
        if not any(payload == c['body'] for c in comments):
            gh.comment(qa['number'], payload)
        if not any(payload == c['body'] for c in gh.comments(qa['number'])):
            raise ValueError('QA content readback failed')
    link = f'<!-- issue-qa-link:v1 origin={number} qa={qa["number"]} -->'
    text = link + f'\n実装はPR #{pr["number"]} / {pr["merge_commit_sha"]}でdevelopへ統合。残る試験/main反映は #{qa["number"]}。GUI pass/main反映済みではありません。'
    if not any(c['body'] == text for c in gh.comments(number)):
        gh.comment(number, text)
    if not any(c['body'] == text for c in gh.comments(number)):
        raise ValueError('Origin backlink readback failed')
    return qa['number']
