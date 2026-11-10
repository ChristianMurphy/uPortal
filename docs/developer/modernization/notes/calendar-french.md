# Handoff: calendar-french (the Calendar's labels for French users)

The branch is `ccr-43ba0785-hzlrj5-calendar-french` in both repositories. uPortal-start has the
change, from `origin/ccr-43ba0785-hzlrj5` at `39ca485901`. uPortal has only this note, on
`7b24794905`. The upstream fix is on the owner's CalendarPortlet fork, on the branch
`fix/calendar-labels-json` (`fd519a4ac0`, from upstream `master` at `961b59fee8`).

## Cause (MOD-I400)

CalendarPortlet 2.7.4 replaced its views with the `<calendar-portlet>` element.
`calendarNarrowView.jsp` and `calendarWideView.jsp` give the element its labels as JSON in a
`<script type="application/json">`, each label written by
`<spring:message javaScriptEscape='true'/>`. Spring's JavaScript escaping writes an apostrophe as
`\'`. JSON has no such escape. `messages_fr.properties` has `view.more.events=Voir plus
d'évènements`, and the narrow view uses that label. For a French user `JSON.parse` fails, the
element logs `calendar-portlet: invalid i18n JSON` and shows its English labels.

The wide view has the same pattern. None of its French labels has an apostrophe today, so it works
until a translation adds one. No other JSP of the portlet uses `javaScriptEscape`. The JSPs are the
same in 2.7.4 and on upstream `master`.

## The overlay in uPortal-start

`overlays/CalendarPortlet/src/main/webapp/WEB-INF/jsp` now holds copies of the two JSPs from 2.7.4.
The war overlay puts them over the ones in the released war. Each copy has one change. It collects
the label block with `<c:set>`, then writes it with `${fn:replace(upcalLabels, "\\'", "'")}`, which
turns each `\'` back into an apostrophe. A backslash right before an apostrophe can only come from
the escaping of that apostrophe, so the replacement is safe for any label. One case stays wrong.
Spring writes a vertical tab as `\013`, which JSON refuses too. No label has one.

The copies are laid out so that the page is the same, byte for byte, for a text without an
apostrophe. `<c:set>` trims its body, and the copy puts back the line break and the indent around
it. The replay against 5.17 passed with the copies (see "Evidence").

### Removing the overlay

When a CalendarPortlet release has the upstream fix:

1. Set `calendarPortletVersion` to that release in `gradle.properties.example`, and delete the
   comment above it.
2. Delete `overlays/CalendarPortlet/src/main/webapp` (the two JSPs are all it holds).
3. Keep the "Calendar in French" test in `tests/ux/portlets/calendar.spec.ts`. It then checks the
   release.

A copy hides the JSP of whatever release is deployed. If the version changes to a release without
the fix, copy the two JSPs again from that release and repeat the change. The first comment of
each copy and the comment in `gradle.properties.example` say so.

## The test

`tests/ux/portlets/calendar.spec.ts` has a new describe, "Calendar in French". It signs in as a user
of its own, `ownUser("calendar")` (`e2e-calendar-<slot>`, Cal), because it changes the user's saved
language. It sets `fr_FR`, opens Welcome, and expects the range button "Semaine", the link text
"Voir plus d'évènements" and no console error from `calendar-portlet`. Afterwards it sets `en_US`
again.

## The upstream fix

On `fix/calendar-labels-json` a new tag, `<upcal:jsonMessage code="..."/>`, takes the place of
`spring:message` in both label blocks. `JsonMessageTag` extends Spring's `MessageTag`, so it
resolves the message the same way, and writes it escaped for a JSON string with Jackson's
`JsonStringEncoder`. It also escapes `<` and `>`, as the old escaping did. The TLD is
`WEB-INF/tag/calendar.tld`, and `include.jsp` declares the prefix. The tag needs the JSP API to
compile. The parent pom manages `tomcat-jsp-api` as a provided dependency, and the portlet's pom
now names it.

The overlay doesn't reuse that fix, because it would need a compiled class and the TLD in the
overlay. Two JSPs are easier to remove.

## Evidence

* Upstream fix: `JsonMessageTagTest` renders `view.more.events` from the portlet's French bundle
  through the tag and parses the result with Jackson. It also checks a text with quotes, a
  backslash, `</script>`, a tab, a line break and a vertical tab. Both tests pass. The same render
  through `spring:message` with `javaScriptEscape` fails with `JsonParseException: Unrecognized
  character escape ''' (code 39)` (a temporary test, not committed). `mvn -B package`, upstream's
  CI command, passes on JDK 11 with 33 tests.
* The element: I loaded `calendar-portlet-element.js` of 2.7.4 in Chromium (headless shell,
  revision 1243, about 11 GB of memory available) with the label JSON as 2.7.4 writes it for French
  and as the repaired JSP writes it. With `\'` the buttons read Day, Week and Month, the link reads
  "View more events" and the console has `calendar-portlet: invalid i18n JSON SyntaxError: Bad
  escaped character in JSON`. With the apostrophe the buttons read Jour, Semaine and Mois, the link
  reads "Voir plus d'évènements" and the console has no error. The page was built manually, with
  no portal.
* `npm run lint` in uPortal-start passes with no errors and the same 41 warnings as the tip.
* CI, the push runs of uPortal-start `559f9f954f`. They built uPortal `d177dae90e`, which is the
  integration tip plus this note.
  * Integration 42824923180 passed. In "Playwright: hsqldb" (job 129863065796) 515 tests passed,
    and the report lists "Calendar in French, shows its labels in French" as passed on its first
    attempt. "Playwright: hsqldb, CAS" (129863065942) and "Playwright: postgres-18"
    (129863066052) passed too.
  * HTTP replay 42824923034 passed, and its job "Replay against 5.17" (129862924127) ran. The
    fixtures have Calendar pages in English, so the copies don't change those.
  * CI 42824923024 and Migration equivalence 42824923031 passed. G-index 42824923057 was still
    running when this note was written.

## Not verified

* **No local portal.** After the container was replaced, Gradle's cache lacked many artifacts, and
  Maven Central answered 429 to the uPortal build twice (`logback-classic` 1.5.38, then
  `netty-handler` 4.1.82.Final). An offline build of uPortal-start stopped at `buildSrc` for the
  same reason. So the new test never ran here, with or without the overlay. The CI runs show that
  it passes with the overlay. I couldn't show the test failing without it. The MOD-I400 job
  (116563567542) and the element check above show the two symptoms it looks for.
* **French pages in the replay.** The fixtures have no French request. The replay says nothing
  about the French page, and the Playwright test covers it.
* **The upstream JSPs in a servlet container.** The tag is tested with Spring's mock page context.
  No Tomcat has rendered the two JSPs with it.

## Proposed LOG rows

| ID | Area | Description | Status |
| --- | --- | --- | --- |
| MOD-I? | Portlets | **The Calendar's labels work for French users again (MOD-I400).** uPortal-start overlays `calendarNarrowView.jsp` and `calendarWideView.jsp` of CalendarPortlet 2.7.4 with copies that turn each `\'` in the label JSON back into an apostrophe. `calendar.spec.ts` has a test, "Calendar in French", that opens Welcome as a French user of its own (`e2e-calendar-<slot>`) and expects the French labels and no console error. | **Fixed** (uPortal-start, `ccr-43ba0785-hzlrj5-calendar-french`), until a CalendarPortlet release has the upstream fix. Then remove the overlay as `notes/calendar-french.md` describes. |
| MOD-I? | Portlets | **An upstream fix for CalendarPortlet is ready for the owner.** The branch `fix/calendar-labels-json` on the owner's fork (`fd519a4ac0`) adds a tag, `upcal:jsonMessage`, that writes a message escaped for JSON, and both views use it. `JsonMessageTagTest` covers the French label. The owner opens the pull request. | **Open**, for the owner. |
| MOD-I? | Environment | **No local portal after a container replacement.** Gradle's cache had lost many artifacts and Maven Central answered 429, so neither uPortal nor uPortal-start could be built on this machine on 2026-11-10. The topic's Playwright test ran only in CI. | **Open**. A Gradle cache that is kept across a replacement would avoid it. |
