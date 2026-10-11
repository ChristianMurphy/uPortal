#!/usr/bin/env bash
# Runs a command while holding one of N machine-wide "portal slots" (default 2), so at most N
# Tomcat portals / Playwright runs / multi-container DB tests run at once on this 16 GB box.
# Usage: portal-slot.sh <command...>     e.g. portal-slot.sh bash -c './gradlew tomcatStart && npx playwright test; ./gradlew tomcatStop'
# First come, first served: waiters queue in $dir/queue and only the oldest live waiter may take a
# free slot. Prints the holders and the queue every 60 s while waiting. Slots are released when the
# command exits.
n=${PORTAL_SLOTS:-2}; dir=/tmp/portal-slots; mkdir -p "$dir"; q="$dir/queue"; touch "$q"
me="$$ $(date -u +%H:%M) $PWD :: ${*:0:200}"
with_queue_lock() { ( flock 9; "$@" ) 9>"$dir/queue.lock"; }
enqueue() { echo "$me" >> "$q"; }
prune()   { local tmp; tmp=$(mktemp); while read -r pid rest; do [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && echo "$pid $rest"; done < "$q" > "$tmp"; cat "$tmp" > "$q"; rm -f "$tmp"; }
dequeue() { local tmp; tmp=$(mktemp); grep -v "^$$ " "$q" > "$tmp"; cat "$tmp" > "$q"; rm -f "$tmp"; }
head_pid() { prune; head -1 "$q" | cut -d' ' -f1; }
with_queue_lock enqueue
trap 'with_queue_lock dequeue' EXIT
waited=0
while :; do
  if [ "$(with_queue_lock head_pid)" = "$$" ]; then
    for i in $(seq 1 "$n"); do
      exec {fd}>"$dir/slot-$i.lock"
      if flock -n "$fd"; then
        with_queue_lock dequeue
        echo "$me" > "$dir/slot-$i.owner"
        "$@" {fd}>&-; rc=$?   # the command (and anything it backgrounds) does not inherit the lock
        : > "$dir/slot-$i.owner"; flock -u "$fd"; exit $rc
      fi
      exec {fd}>&-
    done
  fi
  if [ $((waited % 60)) -eq 0 ]; then
    echo "portal-slot: waiting; holders:"; cat "$dir"/slot-*.owner 2>/dev/null | cut -c1-120
    echo "queue:"; cut -c1-120 "$q"
  fi
  sleep 15; waited=$((waited + 15))
done
