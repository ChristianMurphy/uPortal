#!/usr/bin/env bash
# Local landing gate (prototype machine only, never committed).
# Mirrors uPortal-start's Integration "Playwright on HSQLDB" job for a uPortal worktree:
#   1. install uPortal from <uPortal worktree> into a private Maven repo;
#   2. uPortal-start at $START_REF (default origin/ccr-43ba0785-hzlrj5) with uPortalVersion set to it;
#   3. portalInit, tomcatStart, the full Playwright suite, tomcatStop - through portal-slot.sh.
# Usage: land-gate.sh <uPortal worktree> <name>    Log: scratchpad/land-<name>.log  Exit code: Playwright's.
set -uo pipefail
S=/tmp/claude-0/-home-user/edba9fa3-f7ed-56b9-9df5-6e5fb67f1472/scratchpad
UP=$1; NAME=$2; START_REF=${START_REF:-origin/ccr-43ba0785-hzlrj5}
M2=/home/user/wt/.m2-land/repository
INIT=$S/central-mirror-g9.init.gradle
LOG=$S/land-$NAME.log
START=/home/user/wt/land-start-$NAME
PORTS="-PtomcatHttpPort=8480 -PtomcatShutdownPort=8405 -PhsqlPort=8487"   # coordinator landing ports (agent-rules.md)
: > "$LOG"
log() { echo "[$(date -u +%H:%M:%S)] $*" | tee -a "$LOG"; }

mkdir -p "$M2/com/github/ci-rt"
cp -rn /root/.m2/repository/com/github/ci-rt/* "$M2/com/github/ci-rt/"   # jitpack-only jar (MOD-I51)

cd "$UP" || exit 2
VERSION=$(grep '^version=' gradle.properties | cut -d= -f2)
log "install uPortal $VERSION from $UP ($(git log -1 --format='%h %s'))"
./gradlew -q -I "$INIT" -Dmaven.repo.local="$M2" --console=plain install -x test -x spotbugsMain >> "$LOG" 2>&1 \
  || { log "uPortal install FAILED"; exit 3; }

git -C /home/user/uPortal-start fetch -q origin
rm -rf "$START"; git -C /home/user/uPortal-start worktree prune
git -C /home/user/uPortal-start worktree add -q --detach "$START" "$START_REF" || exit 4
cd "$START" || exit 4
cp gradle.properties.example gradle.properties
sed -i "s/^uPortalVersion=.*/uPortalVersion=$VERSION/" gradle.properties
log "uPortal-start $(git log -1 --format='%h %s') with uPortalVersion=$VERSION"

# As in uPortal-start CI: the Gradle daemon runs on JDK 21 (gradle-daemon-jvm.properties), and the
# portal, its data tools and Tomcat run on the JDK in JAVA_HOME, which is 11 for 5.17.
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 PATH=/usr/lib/jvm/java-11-openjdk-amd64/bin:$PATH
GP="-I $INIT -Dmaven.repo.local=$M2 --console=plain $PORTS"
export PORTAL_BASE_URL=http://localhost:8480/uPortal/
"$S/portal-slot.sh" bash -c "
  ./gradlew $GP portalInit >> '$LOG' 2>&1 || { echo portalInit FAILED >> '$LOG'; exit 5; }
  ls .gradle/tomcat/webapps/uPortal/WEB-INF/lib/ | grep -q 'uPortal-core-$VERSION.jar' || { echo 'wrong uPortal deployed' >> '$LOG'; exit 6; }
  ./gradlew $GP tomcatStart >> '$LOG' 2>&1
  for i in \$(seq 1 90); do curl -s -o /dev/null -w '%{http_code}' \$PORTAL_BASE_URL | grep -qE '200|302' && break; sleep 2; done
  npm ci >> '$LOG' 2>&1
  rev=\$(node -e \"console.log(require('./node_modules/playwright-core/browsers.json').browsers.find(b => b.name === 'chromium').revision)\")
  export PLAYWRIGHT_BROWSERS_PATH=$S/pw-browsers-\$rev-shim
  echo \"browsers: \$PLAYWRIGHT_BROWSERS_PATH\" >> '$LOG'
  if [ -d \"\$PLAYWRIGHT_BROWSERS_PATH\" ]; then
    npx playwright test --config=tests/uportal-pw.config.ts --reporter=list >> '$LOG' 2>&1; rc=\$?
  else
    echo \"no browser shim for chromium \$rev\" >> '$LOG'; rc=7
  fi
  ./gradlew $GP tomcatStop >> '$LOG' 2>&1; ./gradlew $GP hsqlStop >> '$LOG' 2>&1
  exit \$rc"
rc=$?
log "Playwright exit $rc: $(grep -E '^ +[0-9]+ (passed|failed|flaky|skipped)' "$LOG" | tr -s ' ' | tr '\n' ';')"
cd /; git -C /home/user/uPortal-start worktree remove --force "$START" 2>/dev/null
exit $rc
