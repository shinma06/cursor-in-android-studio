#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# Restore the repository guards when migrating the retired ZIP download hooks.
# Inspect both scopes before changing anything; unrelated custom hooks stay intact.
python3 - <<'PY'
from pathlib import Path
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


root = Path(git('rev-parse', '--show-toplevel'))
common = Path(git('rev-parse', '--path-format=absolute', '--git-common-dir'))
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
subprocess.run(['python3', 'scripts/workflow/git_guard.py', 'commit'], check=True)
subprocess.run(['git', 'config', '--local', 'core.hooksPath', '.githooks'], check=True)
# Older ZIP setup enabled per-worktree configuration. Retain it for other settings.
worktree_config = subprocess.run(['git', 'config', '--bool', '--get', 'extensions.worktreeConfig'], capture_output=True, text=True)
if worktree_config.stdout.strip() == 'true':
    subprocess.run(['git', 'config', '--worktree', 'core.hooksPath', '.githooks'], check=True)
PY
printf '%s\n' 'Repository protection hooks installed. ZIP downloads are available on GitHub Releases.'
