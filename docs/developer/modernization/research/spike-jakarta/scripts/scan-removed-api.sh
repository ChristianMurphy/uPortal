#!/bin/bash
# Removed-Servlet-API / container linkage scanner over transformed webapps (prototype of the P5-5 scanner).
# Container layer = every jar in the target Tomcat's lib/ (spec APIs + Tomcat internals visible to webapps).
# Reference layer = Tomcat 8.5's lib/ (javax spec packages renamed to jakarta) to label hits as REMOVED.
# usage: scan-removed-api.sh <target tomcat lib> <shared-lib dir> <webapps dir> [webapp...]
T=${1:-spike/tomcat11/lib}; S=${2:-spike/transformed/shared-lib}; W=${3:-spike/transformed/webapps}; shift 3 2>/dev/null
O=${OLD_TOMCAT_LIB:-uPortal-start/.gradle/tomcat/lib}
APPS=${*:-$(ls $W)}
args=(); for a in $APPS; do args+=(--webapp $W/$a); done
exec /usr/lib/jvm/java-25-openjdk-amd64/bin/java -Xmx2g spike/scripts/JakartaLinkScan.java scan \
  --api $T/*.jar --old-api $O/*.jar --shared $S/*.jar "${args[@]}"
