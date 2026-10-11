import sys
m = sys.stdin.read()
for a, b in [
    # subjects
    ("build: build uportal against the in-tree pluto and portlet api",
     "build: build uportal against its copy of pluto and the portlet api"),
    ("build: build the in-tree apache pluto modules",
     "build: build the copied apache pluto modules"),
    # bodies
    ("so the in-tree copies\nthat P5-5", "so the copies in uPortal-pluto\nthat P5-5"),
    ("so the in-tree build\nshipped them", "so the uPortal-pluto build\nshipped them"),
    ("with the in-tree ones", "with the ones built from uPortal-pluto"),
    ("diffs them against the tree;", "diffs them against uPortal-pluto;"),
    ("Changes: notes/pluto-in-tree.md (inventory", "Changes: the P3-2 handoff note (inventory"),
    ("decision, gates, findings", "decision, required checks, findings"),
    ("and\nnotes/pluto-in-tree/compare-classes.py, which", "and a\nscript, compare-classes.py, which"),
    ("The CI gate\nresults follow", "The CI\nresults follow"),
    ("1,148 run, 1,069 passed, 0 failed, 79 skipped, identical per module.",
     "the results are identical per module."),
    ("flags \"gate\" as jargon, and", "flags an abstract word for a required check, and"),
    ("classes \"glue\", which the stricter", "classes with an abstract word, which the stricter"),
]:
    m = m.replace(a, b)
sys.stdout.write(m)
