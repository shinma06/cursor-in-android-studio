"""GitHub CLI transport shared by current candidate and delivery consumers."""
import json
import os
import subprocess


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def api(path, method='GET', data=None):
    endpoint = f'repos/{os.environ["GITHUB_REPOSITORY"]}' + (f'/{path}' if path else '')
    args = ['gh', 'api', endpoint, '--method', method]
    if data is not None:
        args += ['--input', '-']
    return json.loads(subprocess.check_output(args, input=json.dumps(data) if data is not None else None, text=True) or 'null')


def pages(path):
    result = []
    page = 1
    while True:
        rows = api(f'{path}?per_page=100&page={page}')
        result.extend(rows)
        if len(rows) < 100:
            return result
        page += 1


