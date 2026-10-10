#!/usr/bin/env python3
"""Condense JakartaLinkScan output: removed container member -> jars (and webapps). usage: scan-matrix.py <scan.txt>"""
import sys, re, collections
cur = None; m = collections.defaultdict(lambda: collections.defaultdict(set)); label = None
for line in open(sys.argv[1]):
    w = re.match(r'# WEBAPP (\S+)', line)
    if w: cur = w.group(1); continue
    h = re.match(r'\s+(REMOVED(?:-CLASS)?) (?:method |field )?(\S+)', line)
    if h: label = h.group(2); continue
    j = re.match(r'\s+in \[(.*?)\]', line)
    if j and label:
        for jar in j.group(1).split(', '):
            jar = re.sub(r'-[0-9][\w.\-]*\.jar$', '', jar)  # strip version
            m[label][jar].add(cur)
        label = None
    elif not line.startswith('      '): label = None
def short(s):
    s = re.sub(r'L?jakarta/servlet/', 'j.s.', s); s = re.sub(r'Ljava/lang/', '', s)
    return s.replace('jakarta.servlet.', 'j.s.').replace('jakarta.el.', 'j.el.')
for member in sorted(m):
    jars = '; '.join(f"{jar} ({len(apps)})" for jar, apps in sorted(m[member].items()))
    print(f"| `{short(member)}` | {jars} |")
