#!/usr/bin/env python3
#
# Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information regarding copyright ownership. Apereo
# licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the License at the
# following location:
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied. See the License for the specific language governing permissions and
# limitations under the License.
#
"""Shows JUnit XML results on a GitHub Actions run, for when artifacts cannot be downloaded:
the counts and the jqwik property tests' tries and seeds in the job summary, and every failed
test as an annotation (its message, the top of the stack, and each cause)."""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

results = sys.argv[1] if len(sys.argv) > 1 else '.'
tests = failed = skipped = 0
failures = []
properties = []
for path in sorted(glob.glob(os.path.join(results, '*.xml'))):
    root = ET.parse(path).getroot()
    tests += int(root.get('tests', 0))
    skipped += int(root.get('skipped', 0))
    out = root.findtext('system-out') or ''
    # jqwik's report: "timestamp = ..., Class:property = " then "tries = N", "seed = S"
    for block in re.split(r'\ntimestamp = ', '\n' + out):
        name = re.search(r'^\S+, (\S+) =', block)
        tries = re.search(r'\ntries = (\d+)', block)
        seed = re.search(r'\nseed = (-?\d+)', block)
        if name and tries and seed:
            properties.append((name.group(1), tries.group(1), seed.group(1)))
    for case in root.iter('testcase'):
        for problem in case.findall('failure') + case.findall('error'):
            failed += 1
            text = ((problem.get('message') or '') + '\n' + (problem.text or '')).strip()
            failures.append((case.get('classname') + '.' + case.get('name'), text))

lines = [f'### {tests} tests, {failed} failed, {skipped} skipped', '']
if properties:
    lines += ['| Property | Tries | Seed |', '| --- | --- | --- |']
    lines += [f'| {n} | {t} | {s} |' for n, t, s in properties]
    lines += ['', 'Repeat a run with `-PdbMigrateSeed=<seed>`.']
summary = os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
    with open(summary, 'a', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
print('\n'.join(lines))
for title, text in failures[:10]:
    rows = text.splitlines()
    keep = rows[:12]
    for i, row in enumerate(rows[12:], 12):
        if row.startswith('Caused by:'):
            keep += ['...'] + rows[i:i + 4]
    text = '\n'.join(keep[:80]).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error title={title}::{text}')
