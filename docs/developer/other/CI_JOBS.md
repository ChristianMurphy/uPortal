# Which CI jobs run

This page lists what the `CI` workflow runs for each kind of push and which cell runs the steps
that no other cell repeats. The rules for workstream branches (`ccr-43ba0785-hzlrj5-<topic>`) are
prototype only (MOD-D30, MOD-D55) and go before upstreaming.

## Cells of the build job

| Run | Cells |
| --- | --- |
| Push to a workstream branch | Linux with Temurin 21 |
| Push to `master` or the integration branch, pull request, manual run on any branch | Linux with Temurin 21 and 25, Windows and macOS with Temurin 25 |
| Nightly: a manual run with `nightly` ticked | The cells above, plus Linux with Zulu and Corretto on 21 and 25 |

Every cell runs `./gradlew build jacocoAggregateReport`, which compiles and tests all the Java
modules.

## Work done in one cell

Linux with Temurin 21 is in every set. It alone also runs:

* **The web component tests.** They run in Chromium, so this cell installs it. The other cells pass
  `-PskipWebComponentTests`, which skips the `vpTest` task, so they don't need a browser.
* **The JavaScript lint** (`:npm_run_lint-js`).
* **SpotBugs and FindSecBugs**, after Build and Test, on the classes that step compiled. It fails
  on any finding that the module's `config/spotbugs/baseline` doesn't list. It runs even if Build
  and Test failed. A failed run uploads the reports as the `spotbugs-reports` artifact.
  `docs/developer/other/STATIC_ANALYSIS.md` has the details.
* **The Coveralls upload.**

## Jobs that run on every push

`Commit messages` and `Prose (Vale)` each run once on Linux.

## On your machine

```bash
./gradlew build -PskipWebComponentTests   # what the cells other than the one above run
./gradlew spotbugsMain                    # SpotBugs alone, after a build
```
