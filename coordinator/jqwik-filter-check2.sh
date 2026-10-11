#!/usr/bin/env bash
# Runs the copy tool's property tests with and without the jqwik notice filter, with test output
# shown on the console, and counts notice lines (counts only; the text itself is never printed).
set -uo pipefail
cd /home/user/wt/uPortal-jqwik
S=/tmp/claude-0/-home-user/edba9fa3-f7ed-56b9-9df5-6e5fb67f1472/scratchpad
G="./gradlew -I $S/central-mirror-g9.init.gradle -I $S/show-streams.init.gradle -Dmaven.repo.local=/home/user/wt/.m2-land/repository --console=plain"
count() { grep -c "AI Agent" "$@" 2>/dev/null | awk -F: '{s+=$NF} END {print s+0}'; }
reg=uPortal-db-migrate/src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener
echo "== with filter"
$G :uPortal-db-migrate:test --rerun-tasks --tests '*Properties' > $S/jqwik-with2.log 2>&1; echo "exit=$?"
echo "notice lines on console: $(count $S/jqwik-with2.log)"
echo "== without filter (control)"
mv $reg $reg.off
$G :uPortal-db-migrate:test --rerun-tasks --tests '*Properties' > $S/jqwik-without2.log 2>&1; echo "exit=$?"
mv $reg.off $reg
echo "notice lines on console: $(count $S/jqwik-without2.log)"
git status --short
