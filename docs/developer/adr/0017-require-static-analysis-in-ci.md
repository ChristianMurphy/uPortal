---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Require static analysis in CI, with a SpotBugs baseline and NullAway on null-marked code

## Context and Problem Statement

Before this decision, uPortal's build applied SpotBugs 4.10.4 with FindSecBugs, but it ran only
when a SpotBugs task was named on the command line, with `ignoreFailures = true`, so CI never ran
it. Error Prone 2.50.0 ran on every compile with four ERROR-level checks turned off, among them
the security check `BanJNDI`. The code had no nullness annotations at all.

Null handling had already caused a real failure. Once, a re-login that replaced the session during
a render made `PortletPreferencesFactoryImpl.isGuestUser` throw a `NullPointerException`.

How can the build catch these bugs in new code, when the existing code already has many findings?

## Decision Drivers

* New code must not add findings.
* Existing findings get fixed over time, without blocking unrelated work.
* Spring Framework 7 annotates its APIs with JSpecify and builds with NullAway, so uPortal's
  nullness annotations should match.
* Security findings (FindSecBugs, `BanJNDI`) count as much as correctness ones.

## Considered Options

* SpotBugs with FindSecBugs in CI against a committed per-module baseline, NullAway in JSpecify mode
  for `@NullMarked` packages, and the disabled Error Prone checks turned back on one at a time
* Keep SpotBugs optional, with failures ignored

## Decision Outcome

Chosen option: "SpotBugs with FindSecBugs in CI against a committed per-module baseline, NullAway
in JSpecify mode for `@NullMarked` packages, and the disabled Error Prone checks turned back on one
at a time", because it fails the build on new findings from today, and the 1051 old findings can
be fixed later.

1. **SpotBugs.** A separate CI job runs `./gradlew spotbugsMain` on JDK 21 at effort `max` and
   medium confidence, in the categories CORRECTNESS (including the `NP_*` null dereference
   detectors), MT_CORRECTNESS, SECURITY, BAD_PRACTICE, PERFORMANCE and STYLE. Each module's
   existing findings are in `config/spotbugs/baseline/<module>.xml`, and a finding that the
   baseline doesn't list fails the job. `./gradlew spotbugsBaseline` rewrites the baselines after
   fixes, and a reviewer checks that the diff only removes entries.
2. **NullAway.** NullAway runs as an Error Prone plugin in JSpecify mode with `OnlyNullMarked`, at
   error level, so it checks only packages and classes marked `@NullMarked`. New packages are
   marked, starting with new code (web component controllers and services, the copy tool) and the
   places with known null bugs (portlet rendering, preferences, security). Findings are fixed with
   `@Nullable` or null handling, not suppressed.
3. **Error Prone.** Each disabled check is turned back on in its own pull request, together with
   the fixes for its findings.

`docs/developer/other/STATIC_ANALYSIS.md` describes how to work with each tool.

### Consequences

* Good, because new code can't add SpotBugs findings, and null-marked code can't add null
  dereferences.
* Good, because SpotBugs found real bugs: an `&&` that should have been `||`, a
  `Calendar.equals(Date)` that was always false, a dereference right after logging that the value
  was missing, unsafe publication of lazily loaded properties, and a comparator that threw on a
  null value. Each was fixed with a regression test, and their 7 baseline entries were removed.
* Good, because after the Spring 7 step NullAway also reads Spring's own nullness annotations.
* Bad, because the baseline starts large: 1051 findings in 43 modules (STYLE 421, SECURITY 306,
  BAD_PRACTICE 211, MT_CORRECTNESS 52, PERFORMANCE 43, CORRECTNESS 18).
* Bad, because NullAway only checks null-marked code and assumes unmarked code returns non-null,
  unless its return type is annotated `@Nullable`.
* Bad, because static analysis doesn't find every race. A session replaced in the middle of a
  render also needs a code fix and a regression test.

### Confirmation

The CI workflow's `Run SpotBugs` step (it was the `Static analysis (SpotBugs and FindSecBugs)` job
when this ADR was written, and now runs in the Linux, Temurin 21 build cell) fails on a finding
outside the baseline. A deliberately added null dereference failed it. NullAway fails `compileJava` on a
violation in a null-marked package. CI run 38081288582 passed on all 8 build legs and the static
analysis job.

When this ADR was written, NullAway covered `org.apereo.portal.security.oauth`,
`org.apereo.portal.security.firewall` and `org.apereo.portal.portlet.session`.
`DoubleBraceInitialization` was back on, while `BanJNDI` (5 findings), `SelfAssertion` (3, in tests)
and `BoxedPrimitiveEquality` (2) were still off.

## Pros and Cons of the Options

### SpotBugs with FindSecBugs in CI against a committed per-module baseline, NullAway in JSpecify mode for `@NullMarked` packages, and the disabled Error Prone checks turned back on one at a time

* Good, because each tool can be adopted without a large cleanup first.
* Good, because JSpecify is the annotation set Spring 7 uses.
* Bad, because the baseline files are large and change whenever findings are fixed.

### Keep SpotBugs optional, with failures ignored

* Good, because it costs no CI time.
* Bad, because nobody sees the findings, and new ones keep coming in.

## More Information

* About 40 of the 48 null-on-return-value findings in the baseline are callers of
  `getPerson()` and `getSecurityContext()`, which are now annotated `@Nullable`.
* [SpotBugs](https://spotbugs.github.io/), [FindSecBugs](https://find-sec-bugs.github.io/),
  [NullAway](https://github.com/uber/NullAway), [JSpecify](https://jspecify.dev/),
  [Error Prone](https://errorprone.info/)
* Prototype references: MOD-D27, MOD-I90, MOD-I100 to MOD-I106.
