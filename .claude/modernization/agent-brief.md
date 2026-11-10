# Common brief for local agents

You are a local agent on the uPortal 6.0 modernization prototype. The coordinator lands work on the
integration branch `feat/uportal-6` in ChristianMurphy/uPortal and ChristianMurphy/uPortal-start.
Your topic branch is `<type>/<short-topic>`, such as `fix/layout-save-lock`, with the same name in
both repositories. The guide lists the types and the names from before 2026-11-10.

## Read first
`docs/developer/modernization/agent-guide.md` on `origin/feat/uportal-6` in uPortal. It holds
the rules. Section 2 lists the evidence each kind of change needs, and section 3 says which
uPortal commit CI builds. Commit messages are in section 7, prose checks in 8, environment limits
in 9 and flaky tests in 10. `LOG.md` beside it is the record.

## This machine
* The container can be replaced at any time, and only pushed work survives. Clones: `/home/user/uPortal`, `/home/user/uPortal-start` (origin =
  the forks). Make worktrees under `/home/user/wt/<dir>` from `origin/feat/uportal-6` after
  `git fetch origin`.
* JDK 11, 21 and 25 are in `/usr/lib/jvm` (`java-<n>-openjdk-amd64`), with 21 as the default.
  CI runs the portal on 11 and builds on 21 and 25. There is no Docker and no `ss`. CI covers the
  database vendors.
* Chromium 1243 only (`/opt/pw-browsers`); Playwright wants 1248. Don't run `playwright install`.
  Use an untracked local config with `launchOptions.executablePath`. Record the revision with
  local results. CI is the final evidence.
* 15 GB of memory, 30 GB of disk, shared. At most one portal per agent; stop it with `tomcatStop`
  then `hsqlStop`. Gradle one-offs with `--no-daemon`. Never run `./gradlew --stop`. Private Maven
  repo per topic: `-Dmaven.repo.local=/home/user/wt/.m2-<topic>`. Never symlink `node_modules`
  between checkouts.
* Maven Central may answer 429: back off, don't loop, don't switch mirrors. A 403 or policy block:
  report it, don't fetch another way. Never disable TLS checks or unset HTTPS_PROXY. Stop only
  processes you started, by PID.

## Git
* Push only to your own topic branch. Never to `feat/uportal-6`. No pull requests. Don't
  rebase pushed commits; merge the tip instead, and keep real changes out of merge commits.
* Run `check-commit-messages.sh` and `vale.sh commits` on your range, and `vale.sh docs` on
  changed docs, before each push.
* Commits are authored and committed as the owner: `Christian Murphy
  <christian.murphy.42@gmail.com>` (the clones' git config is set. Check it with `git log -1
  --format='%an <%ae> / %cn <%ce>'`). No `Co-Authored-By`, no `Claude-Session` line, and no
  model or assistant name anywhere in commits, code or docs (owner, 2026-11-09).

## Notes, evidence, report
* Keep a note `docs/developer/modernization/notes/<topic>.md` on your branch with the cause, the
  evidence and proposed LOG rows as `MOD-I?`. Don't edit `LOG.md`.
* A fix needs a test or run that fails before and passes after. Push runs must pass; say which
  uPortal commit uPortal-start's runs built. Re-run once only a job that stopped before any test ran.
* The coordinator reviews your topic with oss-review before it is merged, so keep the diff to its
  purpose.
* Usage budget is tight this week. Work lean. Read logs before you run things, and avoid long
  repeat runs. Commit locally before long waits.
* Report once at the end: heads and commits, cause and evidence, runs, proposed rows, what you
  added beyond the brief, what is open, and that your processes are stopped. Keep worktrees.

## Prose
The owner asked (2026-11-09) that all prose be plain and easy to follow: commit messages, notes,
docs, code comments and your report. If the `anthropic-skills:humanize` skill is available to
you, apply it to what you write. Either way, say each thing once and use everyday words. Keep the
subject near its verb, and don't stack nouns. Don't use em dashes or en dashes.
