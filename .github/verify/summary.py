#!/usr/bin/env python3
"""Prints what each test did in each state, from the JUnit XML files run.sh collected.

    summary.py <results directory> <database name>

Exits 1 if a state ran no test, or if a test failed with the fix. A test that fails without the
fix is the expected result on the databases that show the bug, and one that passes there is
reported as it is.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

results, database = sys.argv[1], sys.argv[2]
ORDER = ['before', 'first-commit-only', 'after']
rows = []
bad = []
for branch in sorted(os.listdir(results)):
    states = sorted(os.listdir(os.path.join(results, branch)), key=ORDER.index)
    for state in states:
        cases = []
        for path in sorted(glob.glob(os.path.join(results, branch, state, '*.xml'))):
            for case in ET.parse(path).getroot().iter('testcase'):
                name = case.get('classname').rsplit('.', 1)[-1] + '.' + case.get('name')
                problems = case.findall('failure') + case.findall('error')
                if case.find('skipped') is not None:
                    cases.append((name, 'skipped', ''))
                elif problems:
                    text = ((problems[0].get('message') or '') + '\n' + (problems[0].text or ''))
                    lines = [line.strip() for line in text.splitlines() if line.strip()]
                    causes = [line for line in lines if line.startswith('Caused by:')]
                    key = lines[0] if lines else ''
                    if causes:
                        key += ' / ' + causes[-1]
                    cases.append((name, 'FAILED', key[:600]))
                else:
                    cases.append((name, 'passed', ''))
        if not cases:
            bad.append(f'{branch} {state}: no test ran')
            rows.append((branch, state, '(no test ran)', 'ERROR', ''))
        for name, result, key in cases:
            rows.append((branch, state, name, result, key))
            if state == 'after' and result != 'passed':
                bad.append(f'{branch} after: {name} {result}')

lines = [f'### {database}', '', '| Branch | State | Test | Result | Key line |', '| --- | --- | --- | --- | --- |']
for row in rows:
    lines.append('| ' + ' | '.join(cell.replace('|', '\\|') for cell in row) + ' |')
lines.append('')
lines.append('Problems: ' + ('; '.join(bad) if bad else 'none (every state ran, and every test passes with the fix)'))
text = '\n'.join(lines)
print(text)
summary = os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
    with open(summary, 'a') as out:
        out.write(text + '\n')
sys.exit(1 if bad else 0)
