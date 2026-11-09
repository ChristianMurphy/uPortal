#!/usr/bin/env bash
# Coordinator helper: check the commits about to land, then push them to the integration branch.
# Run it from a clone or worktree whose HEAD is the replayed landing.
#
# It refuses a commit that isn't the owner's, a commit message that credits a tool, and a head
# whose message has [skip ci] (such a push starts no runs).
set -euo pipefail
branch=ccr-43ba0785-hzlrj5
owner_email=${OWNER_EMAIL:-christian.murphy.42@gmail.com}
git fetch -q origin "$branch"
git rebase -q "origin/$branch"
range="origin/$branch..HEAD"
[ -n "$(git rev-list "$range")" ] || { echo "Nothing to push."; exit 0; }
others=$(git log "$range" --format='%h %ae %ce' | awk -v o="$owner_email" '$3 != o || ($2 != o && $2 !~ /github-actions/)')
[ -z "$others" ] || { echo "Not the owner's commits:"; echo "$others"; exit 1; }
if git log "$range" --format=%B | grep -qiE '^(Co-Authored-By|Claude-Session):'; then
  echo "A commit message credits a tool."; exit 1
fi
if git log -1 --format=%B | grep -qF '[skip ci]'; then
  echo "The head has [skip ci], so the push would start no runs."; exit 1
fi
.github/scripts/check-commit-messages.sh "$range"
.github/scripts/vale.sh commits "$range"
git push -q origin "HEAD:$branch"
git log --oneline -1
