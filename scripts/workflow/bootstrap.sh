#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# Relative path resolves within each worktree; never point other worktrees at this checkout.
git config --local core.hooksPath .githooks
python3 scripts/workflow/git_guard.py commit
printf '%s\n' 'Hooks installed. Read docs/development/github-workflow.md and claim the Issue before editing.'
