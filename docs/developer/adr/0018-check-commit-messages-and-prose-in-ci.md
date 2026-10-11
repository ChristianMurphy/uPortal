---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Check commit messages and 6.0 docs in CI, and keep history linear

## Context and Problem Statement

uPortal uses Conventional Commits, and squash-merges pull requests with Conventional titles, but
nothing checks the messages. The 6.0 work is about fifty pull requests (ADR 0002), and each commit
body becomes its pull request description. An audit of the prototype's integration branch found:

* All 181 non-merge commits had valid types and scopes.
* 13 subjects were longer than the 72 characters `AGENTS.md` allows, mostly because issue
  references were in the subject.
* The branch had 21 merge commits, which don't read as a series of upstream pull requests.

Much of the prototype, prose included, was written with AI coding agents under the author's
direction and review. When Vale with the ai-tells rules first ran on the docs written for 6.0, it
reported about 2,000 findings, among them 785 em dashes, 537 semicolons and 146 capitals after a
colon.

How should uPortal keep commit messages and its 6.0 documentation plain and consistent?

## Decision Drivers

* Each commit should read as the description of one upstream pull request.
* Checks should run in CI and locally with the same script.
* Prose should state what changed and the evidence, without the patterns of generated text.
* The tools should be pinned and verified, with no scripts run from downloaded rule sets.

## Considered Options

* A Conventional Commits subject check and Vale with the ai-tells rules, both in CI, with history
  kept linear
* Vale with the smaller krishnasunkam/vale-ai-tells package
* Squash each piece of work into one commit
* Keep merge commits
* No automated checks

## Decision Outcome

Chosen option: "A Conventional Commits subject check and Vale with the ai-tells rules, both in CI,
with history kept linear", because it checks every commit and the 6.0 docs with pinned tools, and
keeps one commit per future pull request.

* `.github/scripts/check-commit-messages.sh <range>` checks each non-merge commit in the range. The
  subject has the form `type(scope)!: description`, with a type from feat, fix, docs, style,
  refactor, perf, test, build, ci, chore and revert, a lowercase description, no trailing period and
  at most 72 characters. Issue references go in a footer, such as `Refs GH-42`.
* `.github/scripts/vale.sh` downloads Vale 3.24.0 and the `ai-tells` and `ai-tells-commits` styles
  1.37.0 from tbhb/vale-ai-tells, and checks their SHA-256 sums. The release zips match the v1.37.0
  tag, and all 151 rules are YAML patterns. Every rule is at error level, and any finding fails the
  check.
  `vale.sh commits <range>` checks commit messages, and `vale.sh docs` checks the docs in scope.
* uPortal's own Vale style, in `.github/vale/styles/uPortal`, adds two rules. One flags abstract
  architecture and planning words in place of the concrete class, check or step. The other flags
  sentences that describe a thing by what it isn't before saying what it is.
* The docs in scope are `docs/UPGRADING-6.0.md`, the ADRs in `docs/developer/adr` and
  `docs/developer/other/STATIC_ANALYSIS.md`. Other docs join when they are rewritten.
* History is linear. Work is merged as rebased commits pushed as a fast-forward, or squash-merged
  as upstream does today, and never through merge commits.
* The Vale commit check skips a pushed commit that can't be rewritten if
  `.github/vale/commit-exceptions.txt` lists it with a reason.

### Consequences

* Good, because reviewers get messages in one format, with issue references in the footer.
* Good, because prose in commits and docs is checked before review, not during it.
* Good, because in a linear history each commit matches one upstream pull request.
* Bad, because the ai-tells rules flag some ordinary phrasing, and authors have to rewrite it or
  add a suppression with a reason.
* Bad, because rebasing work onto a moving branch takes more care than merging it.
* Neutral, because commits older than the scripts aren't checked, and pushed history isn't
  rewritten to fix them.

### Confirmation

The CI workflow's `Commit messages` job runs `check-commit-messages.sh` and `vale.sh commits` on the
commits that a push or pull request adds. A seeded bad message failed with four findings, and 3 of
the 60 commits before the check would have failed. The `Prose (Vale)` job runs `vale.sh docs` on
the docs in scope. A planted em dash and planning word in the upgrade guide failed it with three
annotations.

## Pros and Cons of the Options

### A Conventional Commits subject check and Vale with the ai-tells rules, both in CI, with history kept linear

* Good, because both checks run the same way in CI and on a developer's machine.
* Good, because the tbhb package is the one Vale's package catalog lists as `ai-tells`, and it has
  rules for commit messages too.
* Bad, because 151 rules are a lot to learn at first.

### Vale with the smaller krishnasunkam/vale-ai-tells package

* Good, because 17 rules are easier to learn.
* Bad, because it has no commit message rules.

### Squash each piece of work into one commit

* Good, because history is short.
* Bad, because it loses the split into one commit per future upstream pull request.

### Keep merge commits

* Good, because merging is simpler than rebasing.
* Bad, because the history doesn't read as a series of upstream pull requests.

### No automated checks

* Good, because it doesn't cost CI time.
* Bad, because the audit above found long subjects and merge commits that review didn't catch.

## More Information

* uPortal has no commit linter, so the subject check is new to the project. A check of pull request
  titles would complement it, since squash merges use the title as the subject.
* [Conventional Commits](https://www.conventionalcommits.org/),
  [Vale](https://vale.sh/), [vale-ai-tells](https://github.com/tbhb/vale-ai-tells)
* Prototype references: MOD-D32, MOD-D35, MOD-I171.
