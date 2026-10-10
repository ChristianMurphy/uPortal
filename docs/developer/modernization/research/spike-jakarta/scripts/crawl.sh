#!/bin/bash
# Render every registered portlet maximized (as admin) and report HTTP status + portlet error cards.
# usage: crawl.sh <base> <outfile>
B=${1:-http://localhost:9080/uPortal/}; OUT=${2:-spike/logs/crawl.txt}
D=spike/logs/crawl; mkdir -p $D; CJ=$D/cj.txt; rm -f $CJ
curl -s -c $CJ -b $CJ -o /dev/null "${B}Login?userName=admin&password=admin"
: > $OUT
for f in $(cat spike/logs/fnames.txt); do
  r=$(curl -s -L -m 60 -b $CJ -c $CJ -o $D/$f.html -w "%{http_code}" "${B}p/$f/max/render.uP")
  e=$(grep -c 'class="card portlet error' $D/$f.html); t=$(grep -c "up-portlet-wrapper .*$f" $D/$f.html)
  echo "$f $r errorCards=$e targetWrapper=$t" >> $OUT
done
echo "total=$(wc -l < $OUT) non200=$(grep -vc ' 200 ' $OUT) withErrorCards=$(grep -vc 'errorCards=0' $OUT)"
