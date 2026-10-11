import sys
m = sys.stdin.read()
for a, b in [
    ("the gate runs, the seeded-fault evidence", "the CI runs, the seeded-fault evidence"),
    ("  5 removed (1 error). Its 4 tests pass.\n", "  5 removed (1 error).\n"),
    ("and its size was last measured by hand\n(37 errors in 6 files, MOD-D26).",
     "and its size was last measured once,\nmanually (37 errors in 6 files, MOD-D26)."),
]:
    m = m.replace(a, b)
sys.stdout.write(m)
