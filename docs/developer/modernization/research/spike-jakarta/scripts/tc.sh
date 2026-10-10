#!/bin/bash
# Start/stop a spike Tomcat. Only ever kills processes whose command line contains spike.
# usage: tc.sh start|stop|kill|status [catalina_base]   (env SPIKE_JAVA_HOME selects the JDK)
CB=${2:-spike/tomcat11}
export CATALINA_HOME=$CB CATALINA_BASE=$CB
spikepids() { pgrep -f "catalina.base=$CB " ; pgrep -f "catalina.base=$CB$"; }
case "$1" in
 start) mkdir -p $CB/logs; : > $CB/logs/catalina.out; $CB/bin/catalina.sh start ;;
 stop)  $CB/bin/catalina.sh stop 45 -force ;;
 kill)  for p in $(pgrep -f "Dspike.marker=spike"); do grep -q "spike" /proc/$p/cmdline && grep -q "catalina.base=$CB" /proc/$p/cmdline && { echo "kill $p"; kill $p; }; done ;;
 status) for p in $(pgrep -f "Dspike.marker=spike"); do echo "$p $(tr '\0' ' ' < /proc/$p/cmdline | grep -o 'catalina.base=[^ ]*')"; done ;;
esac
