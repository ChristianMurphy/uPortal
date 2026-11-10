#!/usr/bin/env bash
# Coordinator helper: check the commits about to land, then push them to the integration branch:
# the first name in .github/scripts/branches.sh that origin has, so feat/uportal-6 once that branch
# exists and ccr-43ba0785-hzlrj5 until then.
# Run it from a clone or worktree whose HEAD is the landing: the head of the staging branch
# (staging/landing-<n>) whose runs passed, with at most the log entry and other
# modernization notes on top. Land uPortal first: uPortal-start's check uses the tip of uPortal's
# integration branch.
#
# It refuses a commit that isn't the owner's, a commit message that credits a tool, a head whose
# message has [skip ci] (such a push starts no runs), and a tree whose slow jobs haven't passed on
# a staging branch (.github/scripts/staged.sh landing).
set -euo pipefail
# shellcheck source=.github/scripts/branches.sh
. .github/scripts/branches.sh
branch=$(integration_on origin) || { echo "Could not find the integration branch on origin (${INTEGRATION_BRANCHES[*]})."; exit 1; }
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
if ! .github/scripts/staged.sh landing; then
  echo "Not staged. Push this landing to a staging branch (${STAGING_PREFIX}<n>) in both"
  echo "repositories, uPortal first, wait for its runs to pass, and land uPortal before uPortal-start."
  exit 1
fi
git push -q origin "HEAD:$branch"
git log --oneline -1
