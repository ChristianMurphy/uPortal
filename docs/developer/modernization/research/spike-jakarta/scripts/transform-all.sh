#!/bin/bash
# Transform every uPortal-start webapp (exploded dir) + shared/lib jar with the
# Apache Tomcat jakartaee-migration tool (TOMCAT profile = Tomcat's legacyAppBase default).
# Usage: transform-all.sh [PROFILE] [OUTDIR] [webapp...]
set -u
SPIKE=spike
J=${JAVA:-/usr/lib/jvm/java-21-openjdk-amd64/bin/java}
TOOL=$SPIKE/dl/jakartaee-migration-1.0.13-shaded.jar
PROFILE=${1:-TOMCAT}
OUT=${2:-$SPIKE/transformed}
shift 2 2>/dev/null || true
APPS=${*:-"uPortal resource-server ResourceServingWebapp Announcements BookmarksPortlet CalendarPortlet FeedbackPortlet NewsReaderPortlet NotificationPortlet SimpleContentPortlet WebProxyPortlet basiclti-portlet cas cas-proxy-test-portlet esup-filemanager pluto-testsuite"}
mkdir -p $OUT/webapps $OUT/shared-lib $SPIKE/logs/transform
for j in $SPIKE/src/shared/lib/*.jar; do
  b=$(basename $j)
  $J -jar $TOOL -profile=$PROFILE -logLevel=FINE $j $OUT/shared-lib/$b > $SPIKE/logs/transform/$PROFILE-shared-$b.log 2>&1
done
run() {
  w=$1; log=$SPIKE/logs/transform/$PROFILE-$w.log
  rm -rf "$OUT/webapps/$w"
  t0=$(date +%s)
  $J -Xmx1g -jar $TOOL -profile=$PROFILE ${EXTRA_OPTS:-} -logLevel=FINE $SPIKE/src/webapps/$w $OUT/webapps/$w > $log 2>&1
  rc=$?
  echo "$w rc=$rc secs=$(( $(date +%s)-t0 )) migratedClasses=$(grep -c 'Migrated class' $log) archives=$(grep -c 'Migration finished for archive' $log) skippedArchives=$(grep -c 'Migration skipped for archive' $log) warn=$(grep -c -E 'WARNING|SEVERE' $log)"
}
export -f run; export J TOOL PROFILE OUT SPIKE
printf '%s\n' $APPS | xargs -P 2 -I{} bash -c 'run {}'
