import sys
m = sys.stdin.read()
for a, b in [
    ("refactor(utils): use java.time durations for cluster locks and scheduling",
     "refactor(utils): use java.time durations for locks and scheduling"),
    ("docs: record the full local test results for the java.time slice",
     "docs: record the full local test results for the java.time changes"),
    ("docs: record the java.time slice and its upgrade notes",
     "docs: record the java.time changes and their upgrade notes"),
    ('Problem: the uPortal Vale style (MOD-D35) flags "gate" and "seam" as\njargon, and the handoff note used both.',
     "Problem: the uPortal Vale style (MOD-D35) flags two planning terms\nthat the handoff note used."),
    ('Changes: rename the "Gate results" section to "Check results", call the\nremaining JodaTimeBridge uses "bridge call sites", and call the golden\ntests "required checks".',
     'Changes: rename the results section to "Check results", call the\nremaining JodaTimeBridge uses "conversion call sites", and call the\ngolden tests "required checks".'),
    ("java.time, and the bridge it relies on is tested in uPortal-utils-core.",
     "java.time, and the conversion helper it relies on is tested in\n  uPortal-utils-core."),
    ("did. It is temporary and goes with the JPA slice.", "did. It is temporary and goes with the JPA step."),
    ("- Tests for the bridge (zones, Z offset, DST days)", "- Tests for the conversion helper (zones, Z offset, DST days)"),
    ("decisions, gate results, findings,", "decisions, check results, findings,"),
    ("every bridge call site, the plan for the JPA slice,", "every Joda conversion call site, the plan for the JPA step,"),
]:
    m = m.replace(a, b)
sys.stdout.write(m)
