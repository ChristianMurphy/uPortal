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
"""Fails when a commit range adds to the ArchUnit freeze store (MOD-D43).

Usage: check-freeze-store.py <base> [<head>]   (head defaults to HEAD)

The store records the uses of the APIs the migration retires (MOD-D29). A change must not make
that worse: for each rule, the number of recorded uses in <head> may not be larger than in <base>.
A change may still move a use, for example when it takes a retired type out of a service and
leaves one call in the adapter that calls it; each added entry is listed as a warning so that
reviewers see it. Entries are compared the way ArchUnit matches them: line numbers and the numbers
of anonymous classes and lambdas are ignored, so moving code within a file is not a change. A rule
that is new in <head> is listed but not counted: it records existing uses of an API that a later
change starts retiring, and reviewers judge it with the rest of that change."""
import re
import subprocess
import sys
from collections import Counter

STORE = 'uPortal-webapp/src/test/resources/archunit_store'


def git(*args):
    return subprocess.run(['git', *args], check=True, capture_output=True, text=True).stdout


def rules(rev):
    """Maps each rule's file name in the store at rev to the rule's description."""
    try:
        text = git('show', f'{rev}:{STORE}/stored.rules')
    except subprocess.CalledProcessError:
        return {}
    result = {}
    for line in text.splitlines():
        if not line or line.startswith('#'):
            continue
        # A java.util.Properties file: the key is the escaped description, the value the file's
        # name (a UUID, so the last '=' separates them).
        key, value = line.rsplit('=', 1)
        result[value.strip()] = re.sub(r'\\(.)', r'\1', key)
    return result


def entries(rev, name):
    """The rule's recorded violations at rev, normalized the way ArchUnit's default matcher is."""
    try:
        text = git('show', f'{rev}:{STORE}/{name}')
    except subprocess.CalledProcessError:
        return Counter()
    normalized = Counter()
    for line in text.splitlines():
        if line.strip():
            line = re.sub(r'\.java:\d+\)', '.java)', line)
            normalized[re.sub(r'\$\d+', '$', line)] += 1
    return normalized


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__.splitlines()[2])
    base, head = sys.argv[1], sys.argv[2] if len(sys.argv) == 3 else 'HEAD'
    before, after = rules(base), rules(head)
    grew = []
    for name, description in sorted(after.items(), key=lambda item: item[1]):
        now = entries(head, name)
        if name not in before:
            print(f'New rule, {sum(now.values())} recorded uses (not counted): {description}')
            continue
        then = entries(base, name)
        for line, count in sorted((now - then).items()):
            for _ in range(count):
                print(f'::warning title=Freeze store entry added (MOD-D43)::{description}: {line}')
        old_total, new_total = sum(then.values()), sum(now.values())
        print(f'{old_total} -> {new_total} recorded uses: {description}')
        if new_total > old_total:
            grew.append(f'{description}: {old_total} -> {new_total}')
    for line in grew:
        print(f'::error title=Freeze store grew (MOD-D43)::{line}')
    if grew:
        print(f'{len(grew)} rule(s) in {STORE} record more uses of retired APIs than before. Use '
              'the modern API instead (MOD-D43); the store may only shrink.')
        sys.exit(1)
    print(f'Freeze store: no rule grew between {base} and {head}.')


if __name__ == '__main__':
    main()
