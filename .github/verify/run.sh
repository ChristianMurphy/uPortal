#!/usr/bin/env bash
#
# Runs the tests of each fix branch on one database, first without the fix and then with it.
#
#   run.sh <results directory>
#
# Run it from a clone of uPortal that has the fix branches' commits, with a copy of this directory
# kept outside the clone, because the script checks other commits out. VERIFY_DRIVER, VERIFY_URL,
# VERIFY_USER and VERIFY_PASSWORD name the database. Without VERIFY_URL the tests use the HSQLDB
# database they build themselves.
#
# "Without the fix" is the fix branch with its main sources as on upstream master, so the branch's
# own test runs against the old code.
#
set -u
here=$(cd "$(dirname "$0")" && pwd)
results=$1
master=657741e9b365a2aaf075217065bbf5341033f63d

props=()
if [ -n "${VERIFY_URL:-}" ]; then
  props=(
    "-Dverify.jdbc.driver=$VERIFY_DRIVER" "-Dverify.jdbc.url=$VERIFY_URL"
    "-Dverify.jdbc.user=$VERIFY_USER" "-Dverify.jdbc.password=$VERIFY_PASSWORD"
    # Keep the isolation level the database gives its connections, and leave the JPA tables out
    -Dverify.jdbc.isolation=-1 -Dverify.jpa.generateDdl=false
  )
fi

# run_state <branch> <state> <commit whose main sources to use> <main source paths> -- <gradle args>
run_state() {
  local branch=$1 state=$2 main_from=$3
  shift 3
  local paths=()
  while [ "$1" != "--" ]; do paths+=("$1"); shift; done
  shift
  local dir="$results/$branch/$state"
  mkdir -p "$dir"
  git checkout -q "$main_from" -- "${paths[@]}" || { echo "checkout failed" > "$dir/harness-error"; return; }
  find . -path '*/build/test-results/test/*.xml' -delete
  echo "::group::$branch, $state (main sources of ${main_from:0:10})"
  ./gradlew --no-daemon --no-parallel --continue -I "$here/verify.init.gradle" -x autoLintGradle \
    "$@" "${props[@]}" 2>&1 | tee "$dir/gradle.log" | grep -v '^Download\|^> Task .*\(UP-TO-DATE\|NO-SOURCE\)$'
  echo "::endgroup::"
  find . -path '*/build/test-results/test/*.xml' -exec cp {} "$dir/" \;
}

# verify <branch> <head> <test patch> <main source paths> -- <gradle args>
#   Optional environment: MID_COMMIT, a commit between master and the head to run as well.
verify() {
  local branch=$1 head=$2 patch=$3
  shift 3
  git checkout -q --detach "$head" || exit 1
  git apply "$here/patches/$patch" || exit 1
  local paths=()
  while [ "$1" != "--" ]; do paths+=("$1"); shift; done
  run_state "$branch" before "$master" "${paths[@]}" "$@"
  if [ -n "${MID_COMMIT:-}" ]; then
    run_state "$branch" first-commit-only "$MID_COMMIT" "${paths[@]}" "$@"
  fi
  run_state "$branch" after "$head" "${paths[@]}" "$@"
  git reset -q --hard
}

verify structure-id-race e64627991211aba81026bebdf07ee024ef2b402e structure-id-race.patch \
  uPortal-layout/uPortal-layout-impl/src/main -- \
  :uPortal-webapp:test --tests org.apereo.portal.layout.simple.RDBMUserLayoutStoreStructIdTest

verify counter-store-read-committed 31b0bd3735 counter-store-read-committed.patch \
  uPortal-webapp/src/main -- \
  :uPortal-webapp:test --tests org.apereo.portal.utils.CounterStoreTest

MID_COMMIT=afe61ca1d7 verify layout-save-overlap 4d5bb86f4e layout-save-overlap.patch \
  uPortal-layout/uPortal-layout-core/src/main uPortal-layout/uPortal-layout-impl/src/main -- \
  :uPortal-webapp:test --tests org.apereo.portal.layout.simple.RDBMUserLayoutStoreConcurrencyTest \
  :uPortal-layout:uPortal-layout-core:test --tests org.apereo.portal.layout.UserLayoutHelperImplTest
