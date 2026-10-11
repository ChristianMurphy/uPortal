# Resolves add/add conflicts by keeping ours, a blank line, then theirs. Usage: keep-both.py FILE
import sys
p = sys.argv[1]
out = []
for l in open(p).read().split('\n'):
    if l.startswith('<<<<<<< ') or l.startswith('>>>>>>> '):
        continue
    if l == '=======':
        if out and out[-1] != '':
            out.append('')
        continue
    out.append(l)
open(p, 'w').write('\n'.join(out))
