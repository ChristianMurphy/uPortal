#!/usr/bin/env bash
# Prints the latest run of every workflow a landing needs on one branch of uPortal and
# uPortal-start (default: the integration branch), and whether the latest finished run passed.
# Run it at the start and the end of a coordinator session and before every landing: nothing
# lands on a red tip except the fix for it (agent-guide section 1).
#
# Usage: tip-status.sh [<branch>]
# Exit status: 0 when every latest finished run passed, 1 otherwise (a red run, or no run at all).
# Workflow lint runs only when .github changes, so a branch without a run of it isn't red.
# Needs gh, signed in with read access to both repositories.
set -euo pipefail

branch=${1:-ccr-43ba0785-hzlrj5}
owner=${UPORTAL_OWNER:-ChristianMurphy}
required=(
  "uPortal CI.yml" "uPortal db-integration.yml" "uPortal db-migrate.yml" "uPortal workflow-lint.yml"
  "uPortal-start CI.yml" "uPortal-start integration.yml" "uPortal-start replay.yml"
  "uPortal-start migration-equivalence.yml" "uPortal-start g-index.yml"
  "uPortal-start workflow-lint.yml"
)

red=0
for entry in "${required[@]}"; do
  read -r repo workflow <<< "$entry"
  tip=$(gh api "repos/$owner/$repo/branches/$branch" --jq .commit.sha)
  # One line per run, newest first: id, status, conclusion, event, head commit.
  runs=$(gh run list -R "$owner/$repo" -w "$workflow" -b "$branch" -L 20 \
    --json databaseId,status,conclusion,event,headSha \
    --jq '.[] | [.databaseId, .status, (.conclusion // "-"), .event, .headSha] | @tsv')
  if [ -z "$runs" ]; then
    if [ "$workflow" = workflow-lint.yml ]; then
      printf '%-14s %-26s no run on %s (it runs when .github changes)\n' "$repo" "$workflow" "$branch"
    else
      red=1
      printf '%-14s %-26s no run on %s\n' "$repo" "$workflow" "$branch"
    fi
    continue
  fi
  IFS=$'\t' read -r id status conclusion event sha <<< "$(head -1 <<< "$runs")"
  at_tip=$([ "$sha" = "$tip" ] && echo "tip" || echo "older commit ${sha:0:10}")
  latest="$id $event $status/$conclusion on $at_tip"
  # A cancelled run says nothing about the code, so the last finished run is the last other one.
  finished=$(awk -F'\t' '$2 == "completed" && $3 != "cancelled"' <<< "$runs" | head -1)
  if [ -z "$finished" ]; then
    red=1
    printf '%-14s %-26s latest %s; no finished run in the last 20\n' "$repo" "$workflow" "$latest"
    continue
  fi
  IFS=$'\t' read -r fid _ fconclusion _ fsha <<< "$finished"
  [ "$fconclusion" = success ] || red=1
  if [ "$fid" = "$id" ]; then
    printf '%-14s %-26s %s\n' "$repo" "$workflow" "$latest"
  else
    printf '%-14s %-26s latest %s; last finished %s %s on %s\n' "$repo" "$workflow" "$latest" \
      "$fid" "$fconclusion" "${fsha:0:10}"
  fi
done

if [ "$red" -eq 0 ]; then
  echo "Every latest finished run on $branch passed."
else
  echo "::error title=Tip status::$branch has a red or missing run. Land only the fix for it."
fi
exit "$red"
