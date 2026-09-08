#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# Do not overwrite a custom hook path before setup can inspect it.
python3 - <<'PYTHON'
from pathlib import Path
import subprocess

root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip())
common = Path(subprocess.check_output(['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'], text=True).strip())
allowed = {(root / '.githooks').resolve(), (common / 'plugin-zip/runtime/hooks').resolve()}
for scope in ([], ['--local']):
    value = subprocess.run(['git', 'config', *scope, '--get', 'core.hooksPath'], capture_output=True, text=True)
    if value.returncode == 1:
        continue
    if value.returncode != 0:
        raise SystemExit('Cannot inspect hook configuration; bootstrap stopped without changing it.')
    configured = Path(value.stdout.strip())
    configured = configured if configured.is_absolute() else root / configured
    if configured.resolve() not in allowed:
        raise SystemExit('Custom hook configuration found; preserve it and coordinate migration before bootstrap.')
PYTHON
# Relative path resolves within each worktree; never point other worktrees at this checkout.
git config --local core.hooksPath .githooks
python3 scripts/workflow/git_guard.py commit
# An installed ZIP runtime can be inherited by worktree add without its local delegate.
# Only execute the already trusted installed runtime, never source from a new branch.
python3 - <<'PYTHON'
from pathlib import Path
import subprocess
import sys

def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()

common = Path(git('rev-parse', '--path-format=absolute', '--git-common-dir'))
runtime = common / 'plugin-zip/runtime'
effective = Path(git('config', '--get', 'core.hooksPath'))
if effective.resolve() == (runtime / 'hooks').resolve():
    subprocess.run([sys.executable, str(runtime / 'plugin_zip.py'), 'setup'], check=True)
    metadata = Path(git('rev-parse', '--absolute-git-dir')) / 'plugin-zip-hooks.json'
    if not metadata.exists():
        raise SystemExit('Installed ZIP runtime needs an update from trusted main before this worktree can write.')
PYTHON
printf '%s\n' 'Hooks installed. Read docs/development/github-workflow.md and claim the Issue before editing.'
