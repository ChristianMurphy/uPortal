import sys
m = sys.stdin.read()
for a, b in [
    ("record the gate status in the cache seam note", "record the check status in the cache note"),
    ("in the cache seam note", "in the cache note"),
    ("the Spring Cache seam (MOD-Q10)", "the Spring Cache abstraction (MOD-Q10)"),
    ("add the cache seam handoff note", "add the cache abstraction handoff note"),
    ("- docs/developer/modernization/notes/cache-seam.md,", "- `docs/developer/modernization/notes/cache-seam.md`,"),
    ("Gate results are filled in", "Check results are filled in"),
    ("move the portlet entity id parse cache to Spring's cache API", "move the entity id parse cache to Spring's cache API"),
    ("move the tab name resolution cache to Spring's cache API", "move the tab name cache to Spring's cache API"),
    ("move the dynamic skin failure cache to Spring's cache API", "move the skin failure cache to Spring's cache API"),
]:
    m = m.replace(a, b)
sys.stdout.write(m)
