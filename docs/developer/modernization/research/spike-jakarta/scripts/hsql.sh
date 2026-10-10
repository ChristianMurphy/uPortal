#!/bin/bash
# Spike HSQLDB (copy of uPortal-start .gradle/hsqldb) on port 9887.
# usage: hsql.sh start|stop|reset
SPIKE=spike; H=$SPIKE/hsqldb
J=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
CP=$H/lib/hsqldb-2.5.1.jar:$H/lib/sqltool-2.5.1.jar
case "$1" in
 start)
  cd $H && nohup $J -Xmx512m -cp $CP org.hsqldb.server.Server --database.0 "file:$H/db/uPortal;hsqldb.tx=mvcc" --dbname.0 uPortal --address localhost --port 9887 > $SPIKE/logs/hsqldb.log 2>&1 &
  for i in $(seq 1 60); do (echo > /dev/tcp/127.0.0.1/9887) 2>/dev/null && { echo "hsqldb up on 9887"; exit 0; }; sleep 1; done; echo "hsqldb failed"; exit 1;;
 stop)
  $J -cp $CP org.hsqldb.cmdline.SqlTool --inlineRc="url=jdbc:hsqldb:hsql://localhost:9887/uPortal,user=sa,password=" --sql "shutdown compact;" ;;
 reset)
  rm -f $H/db/uPortal.*; cp -a $SPIKE/src/hsqldb/uPortal.* $H/db/ ; echo reset;;
esac
