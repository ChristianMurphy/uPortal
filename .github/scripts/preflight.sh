#!/usr/bin/env bash
# Checks the local machine before a coordinator writes a brief or an agent starts a portal. Quote
# its output in the brief, so the environment facts there come from this run.
#
# Usage: .github/scripts/preflight.sh <uPortal-start checkout>    (from a uPortal checkout)
#
# Checks, each on one line:
#   dispatch  whether the gh token may start workflow_dispatch runs. Reported, never a failure.
#   chromium  the Chromium and headless-shell revisions that uPortal-start's pinned Playwright
#             wants are installed (PLAYWRIGHT_BROWSERS_PATH, else ~/.cache/ms-playwright).
#   jdk       the JDK in each repository's .sdkmanrc is installed under SDKMAN.
#   port      the portal's ports are free (PORTS, default 8080 8005 8009 8887).
#   memory    at least 4 GB of memory is available. A local Playwright run crashed its browser at
#             2.8 GB available.
# Exit status: 1 when any check other than dispatch fails.
set -uo pipefail

start=${1:?usage: preflight.sh <uPortal-start checkout>}
owner=${UPORTAL_OWNER:-ChristianMurphy}
failed=0
ok() { printf 'ok    %-9s %s\n' "$1" "$2"; }
bad() { printf 'FAIL  %-9s %s\n' "$1" "$2"; failed=1; }

# A dispatch of a workflow that doesn't exist starts nothing. GitHub answers 404 when the token
# may dispatch and 403 when it may not.
for repo in uPortal uPortal-start; do
  answer=$(gh api -X POST "repos/$owner/$repo/actions/workflows/preflight-no-such-workflow.yml/dispatches" \
    -f ref=master 2>&1)
  case "$answer" in
    *"HTTP 404"*) printf 'info  %-9s %s: the token may start workflow_dispatch runs\n' dispatch "$repo" ;;
    *"HTTP 403"*) printf 'info  %-9s %s: the token may not start workflow_dispatch runs (403); use push runs\n' dispatch "$repo" ;;
    *) printf 'info  %-9s %s: unknown (%s)\n' dispatch "$repo" "$(head -1 <<< "$answer")" ;;
  esac
done

browsers_json=$start/node_modules/playwright-core/browsers.json
browsers=${PLAYWRIGHT_BROWSERS_PATH:-$HOME/.cache/ms-playwright}
if [ -f "$browsers_json" ]; then
  version=$(sed -n 's/^ *"version": "\(.*\)",$/\1/p' "$start/node_modules/playwright-core/package.json")
  while read -r name revision; do
    dir=$browsers/${name//-/_}-$revision
    if [ -d "$dir" ]; then
      ok chromium "Playwright $version wants $name $revision: $dir"
    else
      bad chromium "Playwright $version wants $name $revision, and $dir is missing"
    fi
  done < <(python3 -c 'import json, sys
for b in json.load(open(sys.argv[1]))["browsers"]:
    if b["name"] in ("chromium", "chromium-headless-shell"):
        print(b["name"], b["revision"])' "$browsers_json")
else
  bad chromium "$browsers_json is missing; run npm ci in $start"
fi

sdkman=${SDKMAN_DIR:-$HOME/.sdkman}
for repo in "$(git rev-parse --show-toplevel)" "$start"; do
  jdk=$(sed -n 's/^java=//p' "$repo/.sdkmanrc")
  if [ -n "$jdk" ] && [ -d "$sdkman/candidates/java/$jdk" ]; then
    ok jdk "$(basename "$repo") .sdkmanrc: $jdk"
  else
    installed=$(cd "$sdkman/candidates/java" 2> /dev/null && printf '%s ' [0-9]*)
    bad jdk "$(basename "$repo") .sdkmanrc wants '$jdk'. SDKMAN has: ${installed:-none}"
  fi
done

for port in ${PORTS:-8080 8005 8009 8887}; do
  if ! user=$(ss -Hltnp "sport = :$port" 2>&1); then
    bad port "$port: ss failed: $user"
  elif [ -z "$user" ]; then
    ok port "$port is free"
  else
    bad port "$port is in use: $(head -1 <<< "$user" | tr -s ' ')"
  fi
done

available=$(awk '/^MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo)
if [ "${available:-0}" -ge 4096 ]; then
  ok memory "${available} MB available"
else
  bad memory "${available} MB available, less than 4096 MB. Wait for other portals to stop"
fi

exit "$failed"
