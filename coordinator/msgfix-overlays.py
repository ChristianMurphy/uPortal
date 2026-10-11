import sys
m = sys.stdin.read()
for a, b in [
    ("a record of what changed per WAR, the gate and Tomcat 11 evidence, the",
     "a record of what changed per WAR, the check results and Tomcat 11 evidence, the"),
    ("scanner rules and allowlist, local gates (Playwright", "scanner rules and allowlist, local checks (Playwright"),
    ("flags abstract words such as\nshim and bridge, which the handoff note used four times.",
     "flags abstract words, which the\nhandoff note used four times."),
    ("the spike's\n  shared/lib shim would bring", "the spike's\n  shared/lib jar would bring"),
    ("FreeMarker's JSP\n  bridge that are never loaded", "FreeMarker's JSP\n  support classes that are never loaded"),
]:
    m = m.replace(a, b)
sys.stdout.write(m)
