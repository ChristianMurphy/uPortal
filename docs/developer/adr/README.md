# Architecture decision records

An architecture decision record (ADR) describes one decision that shapes uPortal: the problem,
the options that were compared, the option chosen, and what follows from it. uPortal writes ADRs
in the [MADR](https://adr.github.io/madr/) 4.0.0 format, one Markdown file per decision, so the
reasons for a change are kept in the repository next to the code that makes it.

An ADR goes upstream in the pull request with the code for its decision, or in a pull request of
its own when the decision comes first. Reviewers can then accept or reject each decision
separately.

## Adding an ADR

1. Copy [`adr-template.md`](adr-template.md) to `NNNN-title-with-dashes.md`, where `NNNN` is the
   next free number, padded to four digits. Numbers are never reused.
2. Fill in the sections. Delete the optional sections you don't need, and keep the YAML front
   matter at the top: `status`, `date`, `decision-makers`, `consulted` and `informed`.
3. Set `status: proposed` and `date` to the day of the decision.
4. Add a row for it to the index below.
5. Run `.github/scripts/vale.sh docs docs/developer/adr` and `npx remark -f docs/developer/adr`.
   Neither should report a finding.

Keep the ADR self-contained. Summarize the evidence (versions, measurements, test results) in the
ADR itself, and link to upstream documentation for the rest.

The front matter ends with a blank line before the closing `---`. This repository's remark
configuration doesn't parse front matter, and without the blank line remark reads the last
metadata line as a heading.

## Status values

| Status | Meaning |
| --- | --- |
| `proposed` | Written and open for review. Nothing is final yet. |
| `accepted` | The uPortal community agreed to it. Code follows it. |
| `rejected` | Reviewed and turned down. The file stays as a record. |
| `deprecated` | No longer applies, and nothing replaces it. |
| `superseded by ADR-NNNN` | A later ADR replaces it and links back to this one. |

Change an accepted ADR only to fix mistakes or update its status. A new decision gets a new ADR
that supersedes the old one.

## ADRs from the 6.0 prototype

ADRs 0002 onwards record the decisions of the uPortal 6.0 modernization prototype. They start as
`proposed` because the uPortal community hasn't reviewed them. Their "More Information" sections
cite the prototype's log entries as `MOD-D` (decision), `MOD-I` (issue) and `MOD-Q` (question)
numbers. That log stays with the prototype branch and isn't part of uPortal.

## Index

| Number | Title | Status | Date |
| --- | --- | --- | --- |
| [0001](0001-record-architecture-decisions-with-madr.md) | Record architecture decisions with MADR | proposed | 2026-10-11 |
| [0002](0002-target-java-25-and-jakarta-ee-11-in-steps.md) | Target Java 25 and Jakarta EE 11 in 6.0, reached in tested steps | proposed | 2026-10-10 |
| [0003](0003-build-with-gradle-9-before-raising-the-bytecode-level.md) | Build with Gradle 9 before raising the bytecode level | proposed | 2026-10-10 |
| [0004](0004-deploy-uportal-as-a-war-on-an-external-tomcat-11.md) | Deploy uPortal as a WAR on an external Tomcat 11 | proposed | 2026-10-10 |
| [0005](0005-prefer-spring-approaches-and-spring-cache-api.md) | Prefer the Spring approach for every migration, and cache through Spring's Cache API | proposed | 2026-10-10 |
| [0006](0006-upgrade-hibernate-in-steps-with-the-schema-frozen.md) | Upgrade Hibernate in steps with the 5.17 schema frozen | proposed | 2026-10-10 |
| [0007](0007-replace-joda-time-with-java-time.md) | Replace Joda-Time with java.time | proposed | 2026-10-10 |
