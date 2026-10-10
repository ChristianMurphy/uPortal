#!/bin/bash
# Deploy transformed shared/lib + webapps into a spike Tomcat and patch ports 8080->9080, 8887->9887.
# usage: deploy.sh <catalina_base> <transformed dir> [webapp...]
set -e
CB=${1:-spike/tomcat11}; TX=${2:-spike/transformed}; shift 2 || true
SPIKE=spike
APPS=${*:-$(ls $TX/webapps)}
mkdir -p $CB/shared/lib $CB/portal
cp -f $TX/shared-lib/*.jar $CB/shared/lib/
cp -f $SPIKE/src/portal/* $CB/portal/
for a in $APPS; do rm -rf "$CB/webapps/$a"; cp -a "$TX/webapps/$a" "$CB/webapps/$a"; done
FILES=$(cd $CB && grep -rIl -E "localhost:(8887|8080)" portal webapps --include='*.properties' --include='*.xml' 2>/dev/null || true)
for f in $FILES; do sed -i 's/localhost:8887/localhost:9887/g; s/localhost:8080/localhost:9080/g' "$CB/$f"; echo "patched ports: $f"; done
