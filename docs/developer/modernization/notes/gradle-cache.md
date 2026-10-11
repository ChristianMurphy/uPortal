# Gradle cache for topic branches: handoff note

Branch `ccr-43ba0785-hzlrj5-gradle-cache` in uPortal and uPortal-start, cut from `-log-diet`
(MOD-D53). The owner's job summary showed `Gradle State Caching: Basic (read-only)` with
`Entries: 0 restored, 0 saved`. `setup-gradle` writes its cache only on the default branch, and none
of our branches is the default branch, so every Gradle job downloaded all its dependencies from
Maven Central. That costs time and fails when Central answers 403 or 429.

## What changed

| Commit | Repository | Change |
| --- | --- | --- |
| `ci: let topic branches save and restore their Gradle cache` | both | `GRADLE_CACHE_READ_ONLY` is false on `ccr-*` branches, and every `setup-gradle` step passes it as `cache-read-only`. On the default branch and in pull requests the action's default stays. |
| `ci: let one job per cache key save the Gradle cache` | both | Only one job per key saves. Every other job of the key reads only (`GRADLE_CACHE_READ_ONLY_SHARED`). |

## The rule: one saver per key

The basic provider keys an entry by the runner OS and a hash of the build files, with no restore
keys and no job name in the key. So every Linux job of a repository has the same key. GitHub never
replaces an entry, and the first job to finish saves it. The saver is then whichever job is fastest,
not the one that downloads the most.

The first push run showed it. In uPortal run 38150884890, the DB copy tool job was the first Linux
job to finish and saved an entry of 415,964,467 bytes (416 MB) under
`setup-java-Linux-x64-gradle-ce7de829863bc76c420c9ec44e2aa8a9815370208cd816825b4ee444c1177cbb`.
The CI build job was still queued. Every later job on that key would have restored this small
entry and downloaded the rest, so the cache would have saved little and nothing could improve it
until a `*.gradle*` file changed the key.

The rule that fixes it is **one saver per key**. The saver is the job with the widest Gradle work.
The other jobs of the key only read.

| Repository | Saves | Reads only |
| --- | --- | --- |
| uPortal | the Linux Temurin 21 cell of `CI`; the Windows and macOS cells, each for its own OS key | the other Linux cells, `Static analysis`, the Spring 5.3 probe, `DB integration`, `DB copy tool`, mutation testing |
| uPortal-start | `Playwright: <vendor>` of `Integration` (deploys every overlay) for the uPortal-start key; `Build uPortal` of `Integration` for the uPortal key; the Windows and macOS cells of `CI` | every other Gradle job (15 jobs share the uPortal-start key, four build uPortal) |

The `cache-read-only` expressions are in each workflow. A new Gradle job should read, unless it does
wider work than the saver of its key.

## Limits of the basic provider

* **Cache cleanup:** the request asked for it. Only the enhanced provider has `cache-cleanup`.
  It is not available with the basic provider, which was kept for its open-source license (MOD-I75).
  Switching is the owner's decision.
* **Replacing an entry:** a key never changes until a `*.gradle*` or `gradle-wrapper.properties`
  file changes. A version bump in `gradle.properties` doesn't change it. A stale entry stays until
  it expires after 7 days without a restore.
* **Sharing between branches:** a branch reads only its own caches and the default branch's. Each
  topic branch builds its own.

## Size against the 10 GB limit

Measured sizes are in the table below. The limit is per repository, and GitHub evicts the entries
used least recently when it is exceeded.

| Entry | Size | Run |
| --- | --- | --- |
| uPortal, Linux, saved by the DB copy tool (first design) | 415,964,467 bytes | 38150884890 |

## Measurement

Pending: the runs of the second design had not finished when this note was written.
