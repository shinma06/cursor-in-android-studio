#!/usr/bin/env python3
"""Execute only from the trusted base checkout. PR content is git-show JSON data."""
import json
import os
import subprocess
from urllib.request import Request, urlopen
from pr_policy import validate
from verification import verify_pr


def main():
    with open(os.environ['GITHUB_EVENT_PATH']) as stream:
        event = json.load(stream)
    pr = event['pull_request']
    repo = os.environ['GITHUB_REPOSITORY']
    def api(path):
        request = Request(f'https://api.github.com/repos/{repo}/{path}', headers={
            'Authorization': 'Bearer ' + os.environ['GITHUB_TOKEN'],
            'Accept': 'application/vnd.github+json'})
        with urlopen(request, timeout=30) as response:
            return json.load(response)
    current = api(f'pulls/{pr["number"]}')
    if current['head']['sha'] != pr['head']['sha'] or current['base']['ref'] != pr['base']['ref']:
        raise ValueError('PR changed after this workflow was queued')
    validate(current)
    current['base']['sha'] = api('git/ref/heads/' + current['base']['ref'])['object']['sha']
    trusted_head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if trusted_head != current['base']['sha']:
        raise ValueError('Trusted validator checkout is stale; rerun with the current base branch')
    subprocess.run(['git', 'fetch', '--no-tags', 'origin', current['base']['sha'], current['head']['sha']], check=True)
    if current['base']['ref'] == 'main':
        subprocess.run(['git', 'fetch', '--no-tags', 'origin', 'main'], check=True)
        if 'Integration: promotion' in current['body']:
            subprocess.run(['git', 'fetch', '--no-tags', 'origin', 'develop'], check=True)
    result = verify_pr(current, api)
    latest = api(f'pulls/{pr["number"]}')
    latest_base = api('git/ref/heads/' + current['base']['ref'])['object']['sha']
    if (latest['head']['sha'] != current['head']['sha'] or latest['base']['ref'] != current['base']['ref'] or latest_base != trusted_head or latest.get('body') != current.get('body')):
        raise ValueError('PR/base changed during acceptance validation; rerun')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
