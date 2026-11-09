#!/bin/bash
# Run the uPortal-start Playwright suite (copy in spike/pw, base URL 9080) and write a JSON report.
# usage: pw-run.sh <runname> [extra playwright args]
SP=${SP:?set SP to the scratch directory that holds the browser shim}
N=$1; shift
cd ${PWDIR:-spike/pw-base} || exit 1
export PLAYWRIGHT_BROWSERS_PATH=$SP/pw-browsers-1208-shim
export PLAYWRIGHT_JSON_OUTPUT_NAME=spike/logs/pw-$N.json
./node_modules/.bin/playwright test --config=tests/uportal-pw.config.ts --reporter=list,json --output=$SP/spike-pw-$N-results "$@" > spike/logs/pw-$N.txt 2>&1
echo "exit=$?"; tail -8 spike/logs/pw-$N.txt
python3 -I $SP/pwsum.py spike/logs/pw-$N.json > spike/logs/pw-$N-summary.txt 2>&1; head -25 spike/logs/pw-$N-summary.txt
