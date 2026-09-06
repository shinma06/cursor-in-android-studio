#!/usr/bin/env python3
"""Execute only from the trusted base checkout. PR content is git-show JSON data."""
import json
import os
import subprocess
from urllib.request import Request, urlopen
from pr_policy import validate
from verification import verify_pr


def main():
    event = json.load(open(os.environ['GITHUB_EVENT_PATH']))
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
    subprocess.run(['git', 'fetch', '--no-tags', 'origin', current['base']['sha'], current['head']['sha']], check=True)
    if current['base']['ref'] == 'main':
        subprocess.run(['git', 'fetch', '--no-tags', 'origin', 'main'], check=True)
        if 'Integration: promotion' in current['body']:
            subprocess.run(['git', 'fetch', '--no-tags', 'origin', 'develop'], check=True)
    result = verify_pr(current, api)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
