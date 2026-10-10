#!/bin/bash
# Quick curl smoke over the portlet pages used by the Playwright specs. Counts portlet-error cards.
# usage: smoke.sh [base]   e.g. http://localhost:9080/uPortal/
B=${1:-http://localhost:9080/uPortal/}; D=spike/logs/smoke; mkdir -p $D; CJ=$D/cj.txt; rm -f $CJ
get() { # name url
  local f=$D/$1.html
  local r=$(curl -s -L -b $CJ -c $CJ -o $f -w "%{http_code} %{size_download}" "$2")
  local wr=$(grep -c 'up-portlet-wrapper ' $f); local err=$(grep -c 'class="card portlet error' $f)
  local title=$(grep -o '<title>[^<]*' $f | head -1 | cut -c8-60)
  printf "%-28s %s wrappers=%-3s errorCards=%-3s %s\n" "$1" "$r" "$wr" "$err" "$title"
}
get guest-welcome "${B}f/welcome/normal/render.uP"
curl -s -b $CJ -c $CJ -o /dev/null -w "login(admin): %{http_code} -> %{redirect_url}\n" "${B}Login?userName=admin&password=admin"
get admin-home "${B}"
for p in announcements bookmarks calendar feedback campus-news notification-list what-is-uportal web-proxy-example portlet-admin pluto-testsuite; do
  get "p-$p" "${B}p/$p/max/render.uP"
done
get api-portlet-list "${B}api/portlet-list/"
