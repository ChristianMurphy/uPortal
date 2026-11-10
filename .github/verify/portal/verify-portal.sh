#!/usr/bin/env bash
#
# Checks one fix in a portal built from upstream master, before and after the fix.
#
#   verify-portal.sh <fix> <uPortal clone> <uPortal-start clone> <out directory>
#
# <fix> is permission-set-import, skin-elements-copy or logout-session-race. The uPortal clone
# needs the fix branch's commit. Keep this directory outside the uPortal clone, because the script
# checks other commits out. Needs JDK 11 as the default java.
#
# It installs upstream master and the fix branch to two local Maven repositories, runs portalInit
# with the master build, runs the checks, deploys the fix build's uPortal webapp over it, and runs
# the checks again. The database stays the same throughout.
#
set -u
here=$(cd "$(dirname "$0")" && pwd)
fix=$1 uportal=$2 start=$3 out=$4
master=657741e9b365a2aaf075217065bbf5341033f63d
case "$fix" in
  permission-set-import) head=9ea09028862c4d68d59d909bb9e7b1f075fd384e ;;
  skin-elements-copy) head=89f26f8c2c18afba30f60bf258023f0d45b82bc8 ;;
  logout-session-race) head=a478937db0321e27e64e600e34f54e09d61b1c29 ;;
  *) echo "unknown fix $fix"; exit 2 ;;
esac
mkdir -p "$out"
out=$(cd "$out" && pwd)
m2_master=$out/m2-master m2_fix=$out/m2-fix
webapp=$start/.gradle/tomcat/webapps/uPortal
logs=$start/.gradle/tomcat/logs
failed=0

step() { echo; echo "##### $*"; }

install() { # <commit> <local Maven repository> <label>
  step "Install uPortal $3 ($1)"
  (cd "$uportal" && git checkout -q --detach "$1" &&
    ./gradlew --no-daemon -x autoLintGradle "-Dmaven.repo.local=$2" install) > "$out/install-$3.log" 2>&1 ||
    { tail -40 "$out/install-$3.log"; echo "install of $3 failed"; exit 1; }
  tail -3 "$out/install-$3.log"
}

gw() { # <local Maven repository> <uPortal-start tasks...>
  local m2=$1
  shift
  (cd "$start" && ./gradlew --no-daemon "-Dmaven.repo.local=$m2" "$@")
}

# Says which build the deployed uPortal jars come from. Fails if the jar that the fix changes
# isn't the given build's.
deployed() { # <local Maven repository> <label>
  local total=0 same=0 jar name artifact changed
  case "$fix" in
    skin-elements-copy) changed=uPortal-web ;;
    logout-session-race) changed=uPortal-layout-impl ;;
    *) changed=uPortal-io-core ;;
  esac
  for jar in "$webapp"/WEB-INF/lib/uPortal-*-5.17.10-SNAPSHOT.jar; do
    name=$(basename "$jar")
    artifact=${name%-5.17.10-SNAPSHOT.jar}
    total=$((total + 1))
    cmp -s "$jar" "$1/org/jasig/portal/$artifact/5.17.10-SNAPSHOT/$name" && same=$((same + 1))
  done
  echo "Deployed uPortal jars: $total, identical to the $2 build's: $same"
  echo "permissionSet50Importer in the deployed importExportContext.xml: $(grep -c permissionSet50Importer "$webapp/WEB-INF/classes/properties/contexts/importExportContext.xml")"
  name=$changed-5.17.10-SNAPSHOT.jar
  sha256sum "$webapp/WEB-INF/lib/$name" "$m2_master/org/jasig/portal/$changed/5.17.10-SNAPSHOT/$name" \
    "$m2_fix/org/jasig/portal/$changed/5.17.10-SNAPSHOT/$name" | sed "s|$out/||; s|$start/||"
  cmp -s "$webapp/WEB-INF/lib/$name" "$1/org/jasig/portal/$changed/5.17.10-SNAPSHOT/$name"
}

wait_for_portal() {
  for _ in $(seq 1 150); do
    code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/uPortal/ || true)
    case "$code" in 200 | 302) echo "The portal answers ($code)"; return 0 ;; esac
    sleep 2
  done
  echo "The portal didn't answer in 5 minutes"
  return 1
}

playwright() { # <label>: upstream's own sign-in and smoke tests, in Chromium
  step "uPortal-start's Playwright tests (tests/ux/auth, tests/ux/smoke): $1"
  (cd "$start" && npx playwright test --config=tests/uportal-pw.config.ts tests/ux/auth tests/ux/smoke) \
    > "$out/playwright-$1.log" 2>&1
  echo "exit code $?"
  grep -E '^\s+[0-9]+ (passed|failed|flaky|skipped|did not run)' "$out/playwright-$1.log"
  grep -E '^\s+\[.*›' "$out/playwright-$1.log" | sort -u | head -20
}

checks() { # <label>
  if [ "$fix" = permission-set-import ]; then
    step "Permission sets: $1"
    python3 "$here/perm.py" "$1" "$start" "$out" "$2" || failed=1
  else
    step "Start the portal: $1"
    gw "$2" tomcatStart > "$out/tomcatStart-$1.log" 2>&1
    wait_for_portal || { failed=1; return; }
    # The first page loads compile JSPs and fill caches
    curl -s -o /dev/null 'http://localhost:8080/uPortal/Login?userName=student&password=student'
    # Playwright first: its tests expect the layouts as dataInit made them
    playwright "$1"
    python3 "$here/portal_check.py" "$1" "$out" "$logs" || failed=1
    step "Stop the portal: $1"
    gw "$2" tomcatStop > "$out/tomcatStop-$1.log" 2>&1
    mkdir -p "$out/tomcat-logs-$1" && cp -r "$logs"/. "$out/tomcat-logs-$1/"
    echo "nodeList2Iterator lines in the Tomcat logs of this phase: $(grep -r nodeList2Iterator "$out/tomcat-logs-$1" | wc -l)"
    gw "$2" tomcatClearLogs > /dev/null 2>&1
  fi
}

install "$master" "$m2_master" master
install "$head" "$m2_fix" fix

step "uPortal-start $(git -C "$start" rev-parse --short HEAD): portalInit with the master build"
cp "$start/gradle.properties.example" "$start/gradle.properties"
sed -i 's/^uPortalVersion=.*/uPortalVersion=5.17.10-SNAPSHOT/' "$start/gradle.properties"
gw "$m2_master" portalInit > "$out/portalInit.log" 2>&1 || { tail -60 "$out/portalInit.log"; echo "portalInit failed"; exit 1; }
tail -3 "$out/portalInit.log"
deployed "$m2_master" master || { echo "The deployed jars aren't the master build's"; exit 1; }
if [ "$fix" != permission-set-import ]; then
  (cd "$start" && npm ci > "$out/npm-ci.log" 2>&1 && npx playwright install --with-deps chromium > "$out/playwright-install.log" 2>&1) ||
    echo "Playwright isn't installed; its tests will fail to start"
fi

checks before "$m2_master"

step "Deploy the uPortal webapp of the fix build ($head)"
gw "$m2_fix" :overlays:uPortal:tomcatDeploy > "$out/deploy-fix.log" 2>&1 || { tail -60 "$out/deploy-fix.log"; echo "deploy failed"; exit 1; }
deployed "$m2_fix" fix || { echo "The deployed jars aren't the fix build's"; exit 1; }

checks after "$m2_fix"

step "Stop the database"
gw "$m2_fix" hsqlStop > "$out/hsqlStop.log" 2>&1
rm -rf "$m2_master" "$m2_fix"

if [ "$fix" != permission-set-import ]; then
  # Each fix is judged on its own symptom. The other one's is in the output above.
  python3 - "$out" "$fix" <<'PY' || failed=1
import json, sys
after = json.load(open(sys.argv[1] + '/portal-check-after.json'))
problems = [name for name in ('smoke', 'head') if not after.get(name, {}).get('ok')]
problems += [name for name in after if 'error' in after[name]]
if sys.argv[2] == 'logout-session-race' and after.get('race', {}).get('sessions that showed the logged-out user'):
    problems.append('race')
if sys.argv[2] == 'skin-elements-copy' and after.get('load', {}).get('nodeList2Iterator log lines gained'):
    problems.append('load')
print('Problems with the fix deployed:', problems or 'none')
sys.exit(1 if problems else 0)
PY
fi
exit $failed
