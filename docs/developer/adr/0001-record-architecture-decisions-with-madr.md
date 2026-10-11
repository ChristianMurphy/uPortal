---
status: proposed
date: 2026-10-11
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Record architecture decisions with MADR

## Context and Problem Statement

uPortal 6.0 changes the Java level, the servlet namespace, the Spring and Hibernate versions, the
portlet container and the user interface stack. Each change needs a reason that reviewers can
read before they merge it, and that adopters and later maintainers can find afterwards.

uPortal's code repositories have no written proposal process. The `uPortal-Project/community`
README names that repository's Discussions as the place for design proposals and requests for
comments (RFCs). Neither uPortal nor uPortal-start had an ADR folder. The closest documents are
[`docs/developer/other/ARCHITECTURE.md`](../other/ARCHITECTURE.md), which describes the Gradle
build and module coupling, and `docs/architecture/subsystem-analysis.md`, which analyses the
subsystems. The 6.0 prototype recorded its decisions in a running log and drafted an RFC for the
whole release. The log mixes decisions that matter to uPortal with notes about how the prototype
itself was run, and it stays with the prototype.

Where should uPortal record its architecture decisions, and in what format?

## Decision Drivers

* Reviewers should see a decision in the same pull request as the code for it.
* The community should be able to accept or reject one decision without settling all of them.
* A record should be complete without links into prototype notes.
* The format should be plain Markdown that GitHub renders, with a known structure.

## Considered Options

* ADRs in the MADR 4.0.0 format under `docs/developer/adr`
* One RFC for the whole 6.0 release
* The prototype's decision log, moved into uPortal
* New sections in `ARCHITECTURE.md`

## Decision Outcome

Chosen option: "ADRs in the MADR 4.0.0 format under `docs/developer/adr`", because it is the only
option that gives each decision its own status and puts it in the pull request with the code
for it.

Each ADR is a file named `NNNN-title-with-dashes.md`. It starts with YAML front matter (`status`,
`date`, `decision-makers`, `consulted`, `informed`) and has the MADR sections:

* Context and Problem Statement
* Decision Drivers
* Considered Options
* Decision Outcome, with Consequences and Confirmation
* Pros and Cons of the Options
* More Information
[`README.md`](README.md) explains how to add one and keeps the index.

### Consequences

* Good, because each upstream pull request contains the ADR for its decision.
* Good, because the status field records which decisions the community has accepted.
* Good, because MADR's "Considered Options" section keeps the rejected options and the reasons.
* Bad, because evidence from the prototype has to be summarized in each ADR, and some detail
  is left in the prototype's notes.
* Bad, because a changed decision needs a new ADR that supersedes the old one.

### Confirmation

The CI workflow's `Prose (Vale)` job runs `.github/scripts/vale.sh docs`, which checks every file
in `docs/developer/adr` with Vale, the ai-tells rules and the uPortal style. `npx remark -f docs/developer/adr` checks the Markdown. Reviewers
check that a pull request with the code for a decision includes or links its ADR, and that the
index in `README.md` lists every ADR.

## Pros and Cons of the Options

### ADRs in the MADR 4.0.0 format under `docs/developer/adr`

* Good, because one file per decision fits one pull request per decision.
* Good, because MADR asks for the options and the consequences, not only the outcome.
* Good, because MADR is a published template with its own documentation.
* Bad, because 6.0 alone needs seventeen ADRs, and they repeat some context.

### One RFC for the whole 6.0 release

The prototype drafted one for the `uPortal-Project/community` discussions.

* Good, because one document shows the whole plan.
* Bad, because the community README names Discussions as the place for RFCs, but describes no
  way to accept or reject one.
* Bad, because the community could only take or leave the whole plan.
* Bad, because a discussion post is kept outside the repository and its history.

### The prototype's decision log, moved into uPortal

* Good, because it already exists and cites its evidence.
* Bad, because it mixes uPortal decisions with how the prototype was run, such as its branches,
  machines and agents.
* Bad, because entries change as the prototype moves on, so there is no stable status per
  decision.

### New sections in `ARCHITECTURE.md`

* Good, because the file already exists.
* Bad, because it describes the build structure and has no place for options or status.

## More Information

* MADR: <https://adr.github.io/madr/>
* The template in this folder is the MADR 4.0.0 template with lightly edited placeholder text.
* Prototype references: MOD-D36 (this decision), MOD-D14 (hold upstream work, so the RFC was never
  posted) and MOD-Q6 (the earlier plan to post an RFC).
