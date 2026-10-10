# Static Analysis

uPortal runs two static analysers besides the compiler:

* [Error Prone](https://errorprone.info/) runs inside `javac` on every
  compile, with [NullAway](#null-safety) for null-marked code.
* [SpotBugs](https://spotbugs.github.io/) with the
  [FindSecBugs](https://find-sec-bugs.github.io/) plugin analyses the
  compiled classes. CI runs it in the `Static analysis` job.

## SpotBugs ratchet

SpotBugs runs at effort `max` and reports findings of medium or high
confidence in the categories listed in `config/spotbugs/include.xml`:
`CORRECTNESS` (including the `NP_*` null-dereference detectors),
`MT_CORRECTNESS`, `SECURITY` (FindSecBugs), `BAD_PRACTICE`, `PERFORMANCE`
and `STYLE`.

The code base had findings when SpotBugs became a CI gate. They are
recorded in one baseline per module, `config/spotbugs/baseline/<module>.xml`,
a SpotBugs exclude filter with one entry per class, method (or field) and
bug pattern. `spotbugsMain` fails on any finding that its module's baseline
doesn't list, so new code can't add findings, while old ones are fixed over
time.

`build` and `check` don't run SpotBugs. Run it by name:

```sh
./gradlew spotbugsMain                       # every module, as CI does
./gradlew :uPortal-rendering:spotbugsMain    # one module
```

Each module's reports are in `build/reports/spotbugs/`: `main.html` to
browse, `main.txt` lists the findings that failed the build, and
`main.xml` is the input for the baseline.

### New findings in the build

Fix the code. If the finding is a false positive, suppress that one
finding where it occurs, rather than adding it to the baseline: annotate
the method or field with `@SuppressFBWarnings(value = "<pattern>",
justification = "...")` from
`com.github.spotbugs:spotbugs-annotations:${spotbugsVersion}`, which the
module adds as a `compileOnly` dependency.

### Shrinking the baseline

1. Fix one or more findings that the baseline lists.
2. Rewrite that module's baseline:

   ```sh
   ./gradlew :uPortal-rendering:spotbugsBaseline
   ```

   This reruns SpotBugs without the baseline and writes every current
   finding to `config/spotbugs/baseline/uPortal-rendering.xml` (it deletes
   the file when nothing is left). `./gradlew spotbugsBaseline` rewrites
   every module's baseline.
3. Check that the diff only removes entries, and commit it.

Deleting the entries of the fixed findings by hand works too. Don't add
entries by hand.

## Null safety

uPortal adopts [JSpecify](https://jspecify.dev/) nullness annotations one
package at a time, the way Spring Framework 7 annotates its APIs, and
[NullAway](https://github.com/uber/NullAway) (an Error Prone plugin) checks
them on every compile of production code.

* `@NullMarked` on a package (in its `package-info.java`) or on a class
  says that types in it are non-null unless annotated otherwise.
* `@Nullable` marks a parameter, return type or field that may be null.
  It is a type-use annotation, so it goes right before the type:
  `public @Nullable String getBearerToken(...)`,
  `@Nullable Set<String> claimsToInclude`.
* Code that isn't null-marked is not checked, and NullAway assumes it
  returns non-null values, unless a return type there is explicitly
  annotated `@Nullable`. So when null-marked code calls a method that can
  return null in a class that isn't null-marked yet, annotate that
  method's return type.

NullAway runs at error level in JSpecify mode with `OnlyNullMarked`, so a
null-marked package fails the build when it dereferences something that
may be null, returns or passes null where a non-null value is expected,
or leaves a non-null field uninitialized.

To adopt it in a package:

1. Add `@NullMarked` to the package's `package-info.java` (create it, with
   the license header, if there is none). Pick a package that only one
   module contains: a package split across modules shares one
   `package-info`.
2. Compile, and fix what NullAway reports: add `@Nullable` where null is a
   legitimate value, and handle null (or make sure it can't happen) where
   it isn't. Don't suppress the findings.
3. Fields injected with `@Autowired` or `@Value`, or set in a
   `@PostConstruct` method or a servlet filter's `init`, count as
   initialized. A non-null field that only a setter assigns doesn't, so
   prefer constructor injection there.

Null-marked so far: `org.apereo.portal.security.oauth`,
`org.apereo.portal.security.firewall` and
`org.apereo.portal.portlet.session`.
