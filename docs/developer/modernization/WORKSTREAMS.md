# uPortal 6.0 modernization: workstreams

This is a draft plan for splitting the modernization between parallel
contributors, whether human or sub-agent. It will be revised as research and
prototype results come in; see [LOG.md](LOG.md) for the issues and decisions
behind it.

## Coordination rules

* **Integrator:** one owner manages the integration branch. It merges green
  workstream branches, keeps LOG.md current, and runs the full gate:
  uPortal `build`, uPortal-start `portalInit`, and every Playwright spec.
* **Worktrees:** each workstream gets its own git worktree and branch. A
  workstream rebases onto the integration branch before handing work back.
* **Local Maven repositories:** each workstream publishes uPortal snapshots
  into its own repository (`-Dmaven.repo.local=...`). Gradle's `mavenLocal()`
  honours that property, so parallel `./gradlew install` runs don't overwrite
  each other's snapshots.
* **Ports and memory:** a running portal needs ports 8080, 8005, 8009 and
  8887, plus about 3 GB of RAM. Until uPortal-start can take ports as
  parameters (WS2), only one workstream at a time can run a portal or
  Playwright. Builds and unit tests can run in parallel.
* **Hot files:** `gradle.properties` and the root `build.gradle` are shared,
  so put each version bump in its own small commit. Small commits make
  conflicts cheap to resolve.
* **Linear history (MOD-D32):** work goes onto the integration branch
  without merge commits. Its non-merge commits are rebased onto the
  current integration branch and pushed as a fast-forward, like GitHub's
  "rebase and merge". Each commit is a Conventional Commit shaped
  like a future upstream PR. Merging the integration branch *into* a
  workstream branch while working is fine. The integration branch is
  never force-pushed.
* **Workstream CI branches (MOD-D30):** each workstream pushes its branch
  to the forks as `ccr-43ba0785-hzlrj5-<topic>`. The `ccr-*` trigger runs
  the full CI there, so work is proven green before the integrator merges
  it into `ccr-43ba0785-hzlrj5`. The integrator deletes the branch after
  the merge. Workstreams don't push to the integration branch themselves.
* **Task size:** one future upstream PR per task, about 1 to 3 hours. Each
  task ends with a handoff note in
  `docs/developer/modernization/notes/<topic>.md`: what changed, gate
  results with run IDs, findings, and what's left.
* **Log entries:** workstreams propose LOG.md entries in their handoff
  note, using placeholders (`MOD-I?`, `MOD-D?`). The integrator assigns
  the numbers when merging, so parallel branches don't collide. Local agents that started earlier
  keep reserved blocks: DB matrix MOD-I107 to I119, Hibernate 5.2
  I120 to I129, WS14 I130 to I139, persistence goldens I140 to I149, e2e
  tests I150 to I159, copy-tool tests I160 to I169. Integrator-numbered entries start
  at MOD-I170.
* **Heavy work:** portal, Playwright and multi-database runs need about
  3 GB each. On a shared machine, at most two run at once. Heavier
  workstreams run as separate cloud sessions on their own machines, at
  most three at a time, and validate multi-database work on GitHub CI.

## Workstreams

| WS | Scope | Depends on | Can start |
| --- | --- | --- | --- |
| WS1 | uPortal build toolchain (Gradle 9, JDK 21/25 build, Spotless, error-prone, CI matrix) | nothing | started |
| WS2 | uPortal-start toolchain (Gradle 9, buildSrc, Java 25 runtime, Tomcat 9 → 10.1 → 11 tasks, parameterised ports, Docker images) | nothing | now |
| WS3 | Safety-net tests. Playwright for the admin Web Flow flows (Portlet Manager, Groups, Permissions, Users), CAS login and data import/export. A DB in-place upgrade harness (5.17 schema + data → 6.0) on HSQLDB, then Postgres/MySQL/MSSQL/Oracle via Testcontainers in CI | WS2 helps | now |
| WS4 | Persistence. Hibernate 4.3 → 5.2 (still on Spring 4.3), new schema tooling, naming strategy and ID-generator compatibility, Jadira/Joda → java.time. Then 5.6 → 6.x → 7.x | WS1; WS3 for gating | after WS1 |
| WS5 | Caching. Ehcache 2 → JCache (Ehcache 3 or Caffeine) for Spring cache, Hibernate L2 and the direct API uses (58 files). Clustered invalidation | WS1 | after WS1 |
| WS6 | Spring core/web (unversioned XSDs, split packages, Spring 4.3 → 5.3 → 6 → 7, springfox → springdoc, multipart, Joda in MVC, `-parameters`) | WS1, WS4 (Hibernate 5.2+ first) | after WS1 |
| WS7 | Security (Spring Security 4.2 → 5.8 → 7, CAS client 4.x on jakarta, password-encoder migration) | WS6 | with WS6 |
| WS8 | Portlet container: fork Spring Portlet MVC / Web Flow portlet integration to match Spring versions; Pluto + portlet API on jakarta (fork or transform) | WS6 | spike now |
| WS9 | Portlets/content: transform the external portlet WARs for Jakarta; per-portlet port to web components / Soffit; Vue 2 → Vue 3 | WS8 spike | spike now |
| WS10 | Local SSO in uPortal-start: replace the CAS 3.6 overlay | WS2 | after WS2 |
| WS11 | Upstream packaging (PR slicing, migration guide, release notes, adopter upgrade docs) | everything | continuous |

## Atomic steps that can't be split further

* **The javax → jakarta switch.** It has to happen inside uPortal in one step,
  with Spring 6, Hibernate 6, Servlet 6, Pluto/portlet API on jakarta, and
  Tomcat 10.1+ all together. Everything that can be done beforehand (WS4 to
  WS7 stepping stones, XSD cleanup, Ehcache, Joda) should be merged first so
  this PR is as mechanical as possible. OpenRewrite / Eclipse Transformer can then
  do most of it.
* **Spring 5 and the Portlet MVC replacement.** Moving to Spring 5 removes
  Portlet MVC, so the fork or replacement has to be part of the same step.
