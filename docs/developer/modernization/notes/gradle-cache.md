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

The limit is per repository, and GitHub evicts the entries used least recently when it is exceeded.
The sizes are the bytes the saving job sent.

| Entry | Size | Run and job |
| --- | --- | --- |
| uPortal, Linux, saved by the DB copy tool (first design) | 415,964,467 bytes | 38150884890, 114503920254 |
| uPortal, Linux, saved by the `CI` build cell | 549,854,370 bytes | 38157439312, 114523221602 |
| uPortal-start, the uPortal key, saved by `Build uPortal` of `Integration` | 486,872,376 bytes | 38157305156, 114522826645 |
| uPortal-start, the uPortal-start key, saved by `Playwright: postgres-18` | 1,208,282,953 bytes | 38160995495, 114553883760 |

One topic branch takes about 0.55 GB of the limit in uPortal and 1.7 GB in uPortal-start (two keys),
so about 18 and 6 branches fit in 10 GB. The Windows and macOS entries of `CI` add 2 entries to the
integration branch's runs. `ci-speed.md` ("Cache size") lists what the uPortal-start entry holds and the options.

## Measurement

Times are the job's `started_at` to `completed_at`, and the step times, from `list_workflow_jobs`.

| Job | Run and job | Cache | Job | Step | Setup Gradle |
| --- | --- | --- | --- | --- | --- |
| uPortal `DB copy tool`, before | 38143056181, 114480677246 | none (log-diet) | 312 s | 304 s | 2 s |
| uPortal `DB copy tool`, first push | 38150884890, 114503920254 | none found, saved 416 MB | 353 s | 334 s | 4 s, then 5 s to save |
| uPortal `DB copy tool`, second push | 38154644977, 114514996485 | restored the 416 MB entry | 278 s | 259 s | 6 s |
| uPortal-start `Build uPortal`, before | 38143061751, 114480693369 | none (log-diet) | 223 s | 206 s | 3 s |
| uPortal-start `Build uPortal`, later push | 38154646593, 114515004580 | restored, "entry with key already exists" | 168 s | n/a | 8 s |
| uPortal-start `CI` build job, before | 38143061745, 114480693351 | none (log-diet) | 398 s | `Build` 139 s | 1 s |
| uPortal-start `CI` build job, roles push | 38154646614, 114515055949 | none yet | 430 s | `Build` 167 s | 2 s |

* The step is `Copy between postgres,mariadb` for the copy tool, `Build and install uPortal` for
  `Build uPortal` (the job time is all that was read for the second build), and `Build` for `CI`.
* **Copy tool:** restoring the entry that the previous push saved cut the step by 45 s (15%) and the
  job by 34 s against the run without a cache. The first push, which didn't find an entry and saved one,
  took 41 s longer than the run without a cache, 5 s of it for the save.
* **`Build uPortal`:** the entry came from an earlier run on the branch. The job took 55 s less
  (25%). Most of the 168 s is compiling uPortal, which a cache doesn't change.
* **`CI` build job:** no gain, and none was possible. The job started while the saver of its key,
  the `Playwright: postgres-18` job of `Integration`, was still running, so there was no entry to
  restore. Its `Build` step differs by 28 s in the other direction, which is the run-to-run spread
  of downloads from Maven Central. The first run of a key is always cold.
* The `Setup Gradle` times include the restore. Restoring 416 MB took about 6 s.

More runs are in `ci-speed.md`. On the final head of `-ci-speed`, `DB integration` on postgres-18
went from 306 s to 146 s in the test step after it restored the entry that the `CI` build cell saved,
and `Build uPortal` in `Integration` went from 223 s to 114 s.
