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
"""Shows a PIT mutations.xml on a GitHub Actions run: the mutation score per class and every
surviving mutant, in the job summary."""
import collections
import os
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
if not os.path.exists(path):
    print(f'No PIT report at {path}')
    sys.exit(0)
per_class = collections.defaultdict(collections.Counter)
survivors = []
for m in ET.parse(path).getroot().iter('mutation'):
    cls = m.findtext('mutatedClass')
    status = m.get('status')
    per_class[cls][status] += 1
    if status in ('SURVIVED', 'NO_COVERAGE'):
        survivors.append((cls, m.findtext('lineNumber'), m.findtext('mutatedMethod'),
                          status, m.findtext('description')))
detected = ('KILLED', 'TIMED_OUT', 'MEMORY_ERROR', 'RUN_ERROR')
total = sum(sum(c.values()) for c in per_class.values())
killed = sum(c[s] for c in per_class.values() for s in detected)
lines = [f'### Mutation score {killed}/{total} = {round(100 * killed / max(total, 1))} %', '',
         '| Class | Mutants | Detected | Survived | No coverage |', '| --- | --- | --- | --- | --- |']
for cls in sorted(per_class):
    c = per_class[cls]
    lines.append(f'| {cls.rsplit(".", 1)[-1]} | {sum(c.values())} | '
                 f'{sum(c[s] for s in detected)} | {c["SURVIVED"]} | {c["NO_COVERAGE"]} |')
if survivors:
    lines += ['', '| Surviving mutant | Line | Method | Status | Mutation |',
              '| --- | --- | --- | --- | --- |']
    lines += [f'| {c.rsplit(".", 1)[-1]} | {l} | {m} | {s} | {d} |' for c, l, m, s, d in survivors]
summary = os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
    with open(summary, 'a', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
print('\n'.join(lines))
