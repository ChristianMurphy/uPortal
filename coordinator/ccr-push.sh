#!/usr/bin/env bash
# Coordinator helper (not committed): run both commit-message checks on the commits about to be
# pushed and push to the integration branch only if both pass. Run from a repo or worktree root.
set -euo pipefail
branch=ccr-43ba0785-hzlrj5
git fetch -q origin "$branch"
git rebase -q "origin/$branch"
range="origin/$branch..HEAD"
[ -n "$(git rev-list "$range")" ] || { echo "Nothing to push."; exit 0; }
.github/scripts/check-commit-messages.sh "$range"
.github/scripts/vale.sh commits "$range"
git push -q origin "HEAD:$branch"
git log --oneline -1
