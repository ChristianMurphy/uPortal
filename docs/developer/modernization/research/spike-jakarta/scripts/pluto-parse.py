#!/usr/bin/env python3
"""Parse Pluto testsuite result pages saved by pluto-testsuite.sh. usage: pluto-parse.py <dir> <out>"""
import re, glob, os, sys, collections
d, outp = sys.argv[1], sys.argv[2]
rows = []
def key(x):
    b = os.path.basename(x)[:-5]; p, i = b.rsplit('-', 1); return (p, int(i))
for f in sorted(glob.glob(f'{d}/test-portlet-*-*.html'), key=key):
    s = open(f, errors='replace').read()
    t = re.sub(r'\s+', ' ', re.sub(r'<[^>]*>', ' ', s))
    m = re.search(r'TEST (PASSED|FAILED)', t)
    status = 'MANUAL' if 'MANUAL TEST' in t else ('WARNING' if re.search(r'Direct URL WARNING', t) else (m.group(1) if m else '?'))
    name = (re.search(r'TEST (?:PASSED|FAILED) (\w+)', t) or re.search(r'WARNING (\w+)', t) or re.search(r'MANUAL TEST ([\w ]{3,50}?) This', t))
    fails = [x.strip() for x in re.findall(r'images/no\.gif[^>]*>\s*(?:</[^>]+>\s*<[^>]+>\s*)*([^<]{0,100})', s)]
    rows.append((os.path.basename(f), status, name.group(1) if name else '?', s.count('images/yes.gif'), s.count('images/no.gif'), s.count('images/question.gif'), fails))
c = collections.Counter(r[1] for r in rows)
with open(outp, 'w') as o:
    for r in rows:
        o.write(f"{r[0]:24s} {r[1]:8s} {r[2]:34s} yes={r[3]} no={r[4]} question={r[5]} {r[6] if r[4] else ''}\n")
    o.write(f"SUMMARY tests={len(rows)} {dict(c)} checks yes={sum(r[3] for r in rows)} no={sum(r[4] for r in rows)} question={sum(r[5] for r in rows)}\n")
print(open(outp).read().splitlines()[-1])
