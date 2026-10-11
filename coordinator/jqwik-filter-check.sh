#!/usr/bin/env bash
# Runs the copy tool's unit tests with and without the jqwik notice filter and counts notice lines
# in the test results and console log (counts only; the text itself is never printed).
set -uo pipefail
cd /home/user/wt/uPortal-jqwik
S=/tmp/claude-0/-home-user/edba9fa3-f7ed-56b9-9df5-6e5fb67f1472/scratchpad
G="./gradlew -I $S/central-mirror-g9.init.gradle -Dmaven.repo.local=/home/user/wt/.m2-land/repository --console=plain"
count() { grep -c "AI Agent" "$@" 2>/dev/null | awk -F: '{s+=$NF} END {print s+0}'; }
reg=uPortal-db-migrate/src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener
echo "== with filter"
$G :uPortal-db-migrate:spotlessApply :uPortal-db-migrate:test --rerun-tasks > $S/jqwik-with.log 2>&1; echo "exit=$?"
echo "notice in results: $(count uPortal-db-migrate/build/test-results/test/*.xml) console: $(count $S/jqwik-with.log)"
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' uPortal-db-migrate/build/test-results/test/*.xml | awk -F'"' '{t+=$2;f+=$6;e+=$8} END {print "tests",t,"failures",f,"errors",e}'
echo "== without filter (control)"
mv $reg $reg.off
$G :uPortal-db-migrate:test --rerun-tasks --tests '*Properties' > $S/jqwik-without.log 2>&1; echo "exit=$?"
echo "notice in results: $(count uPortal-db-migrate/build/test-results/test/*.xml) console: $(count $S/jqwik-without.log)"
mv $reg.off $reg
