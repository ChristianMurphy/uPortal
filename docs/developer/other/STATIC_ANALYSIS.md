# Static Analysis

uPortal runs two static analysers besides the compiler:

* [Error Prone](https://errorprone.info/) runs inside `javac` on every
  compile.
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

### When the build finds something new

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
