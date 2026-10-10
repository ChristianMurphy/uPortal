#!/usr/bin/env python3
"""Summarise ERROR entries of a logback-style log: groups by (logger, message head) and shows the
deepest 'Caused by' / first exception line. usage: logsum.py <log> [min-level: ERROR|WARN]"""
import sys, re, collections
lvl = sys.argv[2] if len(sys.argv) > 2 else 'ERROR'
levels = ['ERROR'] if lvl == 'ERROR' else ['ERROR', 'WARN']
start = re.compile(r'^(ERROR|WARN|INFO|DEBUG|TRACE)\s+\[([^\]]*)\]\s+(\S+)\s+\S+ \S+ - (.*)$')
groups = collections.OrderedDict(); cur = None
def flush():
    if cur and cur['lvl'] in levels:
        key = (cur['lvl'], cur['logger'], re.sub(r'[0-9a-f]{8,}|@[0-9a-f]+|\d+', '#', cur['msg'][:140]), cur['cause'])
        g = groups.setdefault(key, {'n': 0, 'thread': cur['thread']}); g['n'] += 1
for line in open(sys.argv[1], errors='replace'):
    m = start.match(line)
    if m:
        flush(); cur = {'lvl': m.group(1), 'thread': m.group(2), 'logger': m.group(3), 'msg': m.group(4).strip(), 'cause': ''}
    elif cur is not None:
        s = line.strip()
        if s.startswith('Caused by:') or (not cur['cause'] and re.match(r'^[a-zA-Z_$][\w.$]*(Exception|Error)\b', s)):
            cur['cause'] = s[:220]
flush()
for (l, lg, msg, cause), g in sorted(groups.items(), key=lambda kv: -kv[1]['n']):
    print(f"{g['n']:5d} {l} {lg} [{g['thread'][:40]}] {msg}\n        -> {cause}")
