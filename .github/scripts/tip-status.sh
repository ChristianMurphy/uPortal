#!/usr/bin/env bash
# Prints the latest run of every workflow a landing needs on one branch of uPortal and
# uPortal-start (default: the integration branch), and whether the latest finished run of the
# branch's tip passed. Run it at the start and the end of a coordinator session and before every
# landing: nothing lands on a red tip except the fix for it (agent-guide section 11).
#
# Usage: tip-status.sh [<branch>]
# Without <branch> it takes the integration branch: the first name in branches.sh that uPortal has,
# so feat/uportal-6 once that branch exists and ccr-43ba0785-hzlrj5 until then.
# Exit status: 0 when the latest finished run of each workflow on the tip passed, 1 otherwise: a
# red run, or a tip whose run is missing, cancelled, queued or still running. A pass on an older
# commit doesn't count. Workflow lint runs only when .github changes, so when the tip has no run of
# it, the latest finished run on any commit counts, and a branch without a run of it isn't red.
#
# A run whose slow jobs were skipped because they had passed for the same tree on a staging branch
# (.github/scripts/staged.sh) concludes with success and counts as a pass. The line below it gives
# the run that passed. Give a staging branch (staging/landing-<n>) as <branch> to see
# its runs before a landing.
# Needs gh, signed in with read access to both repositories.
set -euo pipefail

owner=${UPORTAL_OWNER:-ChristianMurphy}
# shellcheck source=.github/scripts/branches.sh
. "$(dirname "${BASH_SOURCE[0]}")/branches.sh"
branch=${1:-}
if [ -z "$branch" ]; then
  for candidate in "${INTEGRATION_BRANCHES[@]}"; do
    if gh api "repos/$owner/uPortal/branches/$candidate" --silent 2>/dev/null; then
      branch=$candidate
      break
    fi
  done
  [ -n "$branch" ] || { echo "$owner/uPortal has none of the branches ${INTEGRATION_BRANCHES[*]}" >&2; exit 1; }
fi
required=(
  "uPortal CI.yml" "uPortal db-integration.yml" "uPortal db-migrate.yml" "uPortal workflow-lint.yml"
  "uPortal-start CI.yml" "uPortal-start integration.yml" "uPortal-start replay.yml"
  "uPortal-start migration-equivalence.yml" "uPortal-start g-index.yml"
  "uPortal-start workflow-lint.yml"
)

# staged_note <repo> <run id>: the notice of the run's Staging check job, when it skipped the slow
# jobs. Nothing when the run has no such job, ran them or can't be read.
staged_note() {
  local job
  job=$(gh api "repos/$owner/$1/actions/runs/$2/jobs?per_page=100" \
    --jq '.jobs[] | select(.name == "Staging check") | .id' 2>/dev/null | head -1) || return 0
  [ -n "$job" ] || return 0
  gh api "repos/$owner/$1/check-runs/$job/annotations" \
    --jq '.[] | select(.title == "Staging check" and (.message | startswith("skipped"))) | .message' \
    2>/dev/null | head -1 || true
}

red=0
for entry in "${required[@]}"; do
  read -r repo workflow <<< "$entry"
  tip=$(gh api "repos/$owner/$repo/branches/$branch" --jq .commit.sha)
  # One line per run, newest first: id, status, conclusion, event, head commit.
  runs=$(gh run list -R "$owner/$repo" -w "$workflow" -b "$branch" -L 20 \
    --json databaseId,status,conclusion,event,headSha \
    --jq '.[] | [.databaseId, .status, (if (.conclusion // "") == "" then "-" else .conclusion end),
      .event, .headSha] | @tsv')
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
  # Only a run of the tip counts, except for Workflow lint when the tip has no run of it.
  on=$tip
  if [ "$workflow" = workflow-lint.yml ] &&
    ! awk -F'\t' -v tip="$tip" '$5 == tip {found = 1} END {exit !found}' <<< "$runs"; then
    on=any
  fi
  finished=$(awk -F'\t' -v on="$on" '$2 == "completed" && $3 != "cancelled" && (on == "any" || $5 == on)' \
    <<< "$runs" | head -1)
  if [ -z "$finished" ]; then
    red=1
    if [ "$on" = any ]; then
      printf '%-14s %-26s latest %s; no finished run in the last 20\n' "$repo" "$workflow" "$latest"
    elif awk -F'\t' -v tip="$tip" '$5 == tip {found = 1} END {exit !found}' <<< "$runs"; then
      printf '%-14s %-26s latest %s; no finished run on the tip %s (cancelled, queued or running)\n' \
        "$repo" "$workflow" "$latest" "${tip:0:10}"
    else
      printf '%-14s %-26s latest %s; no run on the tip %s\n' "$repo" "$workflow" "$latest" "${tip:0:10}"
    fi
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
  if [ "$fconclusion" = success ]; then
    note=$(staged_note "$repo" "$fid")
    if [ -n "$note" ]; then printf '%-14s %-26s   %s\n' "" "" "$note"; fi
  fi
done

if [ "$red" -eq 0 ]; then
  echo "The latest finished run of each workflow on the tip of $branch passed."
else
  echo "::error title=Tip status::$branch has a run that is red, missing or not finished on the tip. Land only the fix for it."
fi
exit "$red"
