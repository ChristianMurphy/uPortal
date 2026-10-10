#!/bin/bash
# Drive the Pluto 2.0 testsuite portlets (test-portlet-1/2) through uPortal: POST each test action, GET the
# render page, and count PASSED/FAILED/WARNING results. usage: pluto-testsuite.sh <base host> <outfile>
H=${1:-http://localhost:9080}; OUT=${2:-spike/logs/pluto-testsuite.txt}
D=spike/logs/pluto; mkdir -p $D; CJ=$D/cj.txt; rm -f $CJ; : > $OUT
curl -s -c $CJ -b $CJ -o /dev/null "$H/uPortal/Login?userName=admin&password=admin"
for P in test-portlet-1 test-portlet-2; do
  curl -s -L -b $CJ -c $CJ -o $D/$P.html "$H/uPortal/p/$P/max/render.uP"
  for U in $(grep -o "href=\"[^\"]*$P[^\"]*action.uP[^\"]*pP_testId=[0-9]*[^\"]*\"" $D/$P.html | sed 's/href="//; s/"$//; s/&amp;/\&/g'); do
    id=$(echo "$U" | grep -o 'pP_testId=[0-9]*' | cut -d= -f2)
    loc=$(curl -s -o /dev/null -w "%{redirect_url}" -X POST -b $CJ -c $CJ "$H$U")
    curl -s -b $CJ -c $CJ -o $D/$P-$id.html "$loc"
    txt=$(sed 's/<[^>]*>/ /g' $D/$P-$id.html | tr -s ' \n' ' ')
    name=$(echo "$txt" | grep -o -E "Test (Name|Suite)?:? ?[A-Za-z0-9 ]{3,60}" | head -1)
    printf "%s test %-3s action=%s passed=%s failed=%s warning=%s  %s\n" $P $id "$(echo $loc | grep -c render)" \
      "$(grep -o -i '>\s*PASSED\s*<' $D/$P-$id.html | wc -l)" "$(grep -o -i '>\s*FAILED\s*<' $D/$P-$id.html | wc -l)" "$(grep -o -i '>\s*WARNING\s*<' $D/$P-$id.html | wc -l)" "$name" >> $OUT
  done
done
awk '{for(i=1;i<=NF;i++){split($i,a,"="); if(a[1]=="passed")p+=a[2]; if(a[1]=="failed")f+=a[2]; if(a[1]=="warning")w+=a[2]}} END {print "tests="NR" checks passed="p" failed="f" warning="w}' $OUT
