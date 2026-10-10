#!/usr/bin/env python3
"""Summarise JakartaLinkScan census output: per webapp, class counts per javax/jakarta namespace
before (src) and after (transformed). Usage: census-diff.py <logs dir> <webapp...>"""
import sys, re, ast, collections
d = sys.argv[1]
def load(path):
    tot = collections.Counter(); per = {}
    for line in open(path):
        m = re.match(r'\s+(\S+)\t\{(.*)\}$', line)
        if not m: continue
        c = collections.Counter({k.strip(): int(v) for k, v in (kv.split('=') for kv in m.group(2).split(', ') if kv)})
        per[m.group(1)] = c; tot.update(c)
    return tot, per
for w in sys.argv[2:]:
    a, pa = load(f'{d}/census-src-{w}.txt'); b, pb = load(f'{d}/census-tx-{w}.txt')
    renamed = sorted(k for k in a if k.startswith('javax.') and k not in b)
    partly = sorted(k for k in a if k.startswith('javax.') and k in b and b[k] != a[k])
    kept = sorted(k for k in a if k.startswith('javax.') and k in b and b[k] == a[k])
    newj = sorted(k for k in b if k.startswith('jakarta.'))
    print(f'### {w}')
    print('  renamed javax->jakarta :', ', '.join(f'{k}({a[k]})' for k in renamed) or '-')
    print('  partly renamed         :', ', '.join(f'{k}({a[k]}->{b[k]})' for k in partly) or '-')
    print('  kept javax             :', ', '.join(f'{k}({a[k]})' for k in kept) or '-')
    print('  jakarta after          :', ', '.join(f'{k}({b[k]})' for k in newj) or '-')
    # leftovers of renamed-family namespaces, per origin
    fam = re.compile(r'javax\.(servlet|el|websocket|persistence|mail|ejb|transaction$|annotation\.(?!processing|concurrent|meta))')
    lo = {o: {k: v for k, v in c.items() if fam.match(k)} for o, c in pb.items()}
    lo = {o: c for o, c in lo.items() if c}
    if lo: print('  LEFT-BEHIND (renamable javax still referenced):', lo)
