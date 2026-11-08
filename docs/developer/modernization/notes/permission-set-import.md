<!-- markdownlint-disable -->

# dataImport refused the permission sets that dataExport wrote (MOD-I411)

Branch `ccr-43ba0785-hzlrj5-permission-set-import` in uPortal, from the integration branch at
`b7c7269e0`, with `92f31a6aa` merged in. The branch changes only uPortal. uPortal-start's data files reference
the v3-1 import script, which already has an importer. uPortal-start's jobs import those files
and its equivalence fixture, never a `dataExport`.

## What failed

The jdk21-opens review (uPortal-start, `notes/jdk21-opens.md`, "Review of 2026-11-08") ran
uPortal's `dataImport` on uPortal's own `dataExport` of the quickstart data. 8 of 238 files failed,
all of them permission sets, on JDK 11 and on JDK 21:

```
Provided data <permission-set script="classpath://org/apereo/portal/io/import-permission_set_v5-0.crn"> has no registered importer or upgrader support
```

## Cause

A missing importer registration for the v5-0 key. The exporter's version and the data type are
right.

* `export-permission_sets.crn` writes `script="classpath://org/apereo/portal/io/import-permission_set_v5-0.crn"`
  on each `<permission-set>`.
* `PermissionSetPortalDataType` lists `IMPORT_31_DATA_KEY` and `IMPORT_50_DATA_KEY`. The import
  recognises the file as a permission set.
* `importExportContext.xml` defines one permission set importer, `permissionSet31Importer`, for
  `IMPORT_31_DATA_KEY`, and no upgrader for either key. `JaxbPortalDataHandlerService.importOrUpgradeData`
  finds neither for the v5-0 key and throws the message above.
* The v5-0 script exists and runs for every v3-1 file. `import-permission_set_v3-1.crn` renames
  the `org.jasig` principal types and then calls `import-permission_set_v5-0.crn`. Only the bean
  that sends v5-0 files to it was missing.

History, from the tags (the clone is shallow, so `git log -S 'permission_set'` stops at the graft
`a951c7838d` on both `upstream/master` and the integration branch):

| Ref | Exporter writes | Importers in `importExportContext.xml` |
| --- | --- | --- |
| `uportal-4.3.2` | `org/jasig/portal/io/import-permission_set_v3-1.crn` | v3-1 |
| `v5.0.0-M10` (the oldest 5.0 tag) | `org/apereo/portal/io/import-permission_set_v5-0.crn` | v3-1 |
| `v5.0.0`, `v5.17.9`, `upstream/master` (`657741e9b3`) | v5-0 | v3-1 |
| integration branch `b7c7269e0` | v5-0 | v3-1 |

The 5.0 package rename moved the exporter, the data type and the import script to v5-0 and left
the importer at v3-1. Group membership went through the same rename and got both of its v5-0
importers (`groupMembershipGroup50Importer`, `groupMembershipMembers50Importer`). The Pluto, cache
and Spring 5.3 work didn't touch the permission set files. Between `v5.17.9` and `b7c7269e0`,
`importExportContext.xml` changed only in its schema locations, and the permission set data type,
the export script and both import scripts didn't change.

**Upstream candidate.** The released 5.17.9 war has the same three files, and the new test fails
on it with the same message (below).

## Fix

`importExportContext.xml` registers `permissionSet50Importer` for `IMPORT_50_DATA_KEY`, with a
`PojoTask` that runs `import-permission_set_v5-0.crn` and gets the same `PortalDb` and
`transactionManager` as the v3-1 task. It is `threadSafe=false` like the v3-1 importer. A bulk
import finishes every file of one key before it starts the next key. The v3-1 and v5-0
importers never run together.

A smaller change would add `IMPORT_50_DATA_KEY` to `permissionSet31Importer`'s keys. v5-0 files
would then run through the v3-1 script, which renames nothing for them and calls the v5-0 script.
The branch follows the group membership importers instead, with one importer and one task per
version. Writing v3-1 again in the exporter would leave every file that 5.x already exported
unimportable.

## The other data types

`ImportExportContextTest.testEveryExportedDataKeyHasAnImporterOrUpgrader` lists every exporter in
`importExportContext.xml` with the key it writes. It fails when the list misses an exporter, when
an exporter writes another key than the list says, when no data type lists the key, and when
neither an importer nor an upgrader takes the key (after the data type splits it into steps, as
group membership and PAGS do).

| Exporter | Writes | Imported by | How the test reads the written key |
| --- | --- | --- | --- |
| `portletDefinitionImporterExporter` | portlet-definition 5.0 | the same bean | JAXB schema version, not read |
| `portletTypeImporterExporter` | portlet-type 4.0 | the same bean | not read |
| `stylesheetDescriptorImporterExporter` | stylesheet-descriptor 4.0 | the same bean | not read |
| `userImporterExporter` | user 4.0 | the same bean | not read |
| `permissionOwnerImporterExporter` | permission-owner 4.0 | the same bean | not read |
| `eventAggregationConfigurationImporterExporter` | event-aggregation 4.0 | the same bean | not read |
| `entityTypeExporter` | `import-entity-type_v3-2.crn` | `entity32Importer` | from `export-entity-type.crn` |
| `groupMembershipExporter` | `import-group_membership_v5-0.crn` | `groupMembershipGroup50Importer`, `groupMembershipMembers50Importer` | from `export-group_membership.crn` |
| `personAttributesGroupStoreExporter` | `import-pags-group_v4-1.crn` | `pagsGroupMembershipGroup41Importer`, `pagsGroupMembershipMembers41Importer` | runs the exporter with a stub DAO |
| `permissionSetExporter` | `import-permission_set_v5-0.crn` | none on the tip, `permissionSet50Importer` on this branch | from `export-permission_sets.crn` |
| `profileExporter` | `import-profile_v3-2.crn` | `profile32Importer` | from `export-profile.crn` |
| `fragmentLayoutExporter` | `import-layout_v3-2.crn` | `layout32Importer` | `RDBMDistributedLayoutStore`, not read |
| `layoutExporter` | `import-layout_v3-2.crn` | `layout32Importer` | not read |
| `fragmentDefinitionExporter` | `import-fragment-definition_v3-1.crn` | `fragmentDefinition31Importer` | runs the exporter with a stub DAO |

So the permission set exporter was the only exporter without an importer. The JAXB beans import
and export the same version, and `IdentityImportExportTest` round-trips the stylesheet
descriptor, user and permission owner files.

One gap remains outside what the exporters write. `StylesheetDescriptorPortalDataType` lists
`IMPORT_26_THEME_DATA_KEY` and `IMPORT_26_STRUCTURE_DATA_KEY`, and nothing imports or upgrades
them. A uPortal 2.6 stylesheet file gets the same message. It was so in `uportal-4.3.2` too, and
no exporter writes those keys. The branch leaves it (proposed row below).

## Evidence

All local runs used JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`) for Gradle, `--offline` and the
Maven Central mirror init script.

### The tests

`ImportExportContextTest` (uPortal-webapp) loads the bean definitions of
`importExportContext.xml` into a bean factory, with the data types found by the same component
scan the portal uses, an in-memory HSQLDB as `PortalDb`, and stubs for the DAOs that only other
scripts use. The importers, upgraders and exporters are the context's own beans.

* `testPermissionSetExportImportsAgain` stores two permission sets, a group principal with a
  literal target and one with a group target. It runs `exportAllDataOfType("permission-set")`
  (the `dataExport` code) with the context's `permissionSetExporter` and the real
  `export-permission_sets.crn`, deletes the rows, runs `importDataDirectory` (the `dataImport` code)
  on the export, and compares the rows. `GroupService` is a static mock, and the import and export
  tasks run on the test thread.
* `testEveryExportedDataKeyHasAnImporterOrUpgrader`, described above.

`./gradlew :uPortal-webapp:test --tests org.apereo.portal.io.xml.ImportExportContextTest`:

| Code | Result |
| --- | --- |
| integration tip `b7c7269e0`, test only | 2 of 2 fail. The round trip: "2 ... entities failed to import", each file with `IllegalArgumentException: Provided data <permission-set script="classpath://org/apereo/portal/io/import-permission_set_v5-0.crn"> has no registered importer or upgrader support`. The list: one problem, `permissionSetExporter writes <permission-set script=".../import-permission_set_v5-0.crn">, and importExportContext.xml has no importer or upgrader for` that key |
| `453cedf0e` (fix and test) | 2 of 2 pass |

### Released 5.17.9

The test, compiled with JDK 11 against the `WEB-INF/classes` and `WEB-INF/lib` of
`uPortal-webapp-5.17.9.war` from the Gradle cache (sha1 `0d1de33d5a6b2621ddb7ecb1ae5257a42e2b860c`),
with JUnit 4.13.2, Mockito 5.24.0 and HSQLDB 2.7.4, run with JUnitCore on JDK 11:

| Classpath | Result |
| --- | --- |
| the war as released | 2 of 2 fail, with the same messages as on the tip |
| the war, with `properties/contexts/importExportContext.xml` patched with this branch's diff ahead of it | 2 of 2 pass |

So the fix applies to 5.17 unchanged.

### Seeded faults

Each fault was put in the working tree, the test class run, and the fault removed with
`git checkout`. None was committed.

| Fault | `testEveryExportedDataKeyHasAnImporterOrUpgrader` reports |
| --- | --- |
| `export-group_membership.crn` writes `import-group_membership_v6-0.crn`, and `groupMembershipMembers50Importer` removed | `groupMembershipExporter writes <group script=".../import-group_membership_v6-0.crn">, the table says <group script=".../import-group_membership_v5-0.crn">` and `... has no importer or upgrader for <group script=".../import-group_membership_v5-0.crn" version="MEMBERS">` |
| `PersonAttributesGroupStoreExporter` writes `import-pags-group_v4-2.crn` | `personAttributesGroupStoreExporter writes <pags-group script=".../import-pags-group_v4-2.crn">, the table says <pags-group script=".../import-pags-group_v4-1.crn">` |
| `profileExporter` renamed `profileExporterSeeded` | `EXPORTED_DATA_KEYS lists every exporter in importExportContext.xml`, with both lists |

The round trip failing on the tip is its own seeded fault: the real defect.

### Checks before the push

On the merged head `11be9f6ce`:

| Check | Result |
| --- | --- |
| `./gradlew --continue :uPortal-webapp:test :uPortal-io:uPortal-io-core:test :uPortal-io:uPortal-io-types:test spotlessCheck spotbugsMain` | passes in 11 min. uPortal-webapp 486 tests (76 skipped), uPortal-io-core 7, uPortal-io-types 2, 0 failures |
| `check-freeze-store.py <merge base 92f31a6aa> HEAD` | passes, no rule grew |
| `check-commit-messages.sh` and `vale.sh commits` on `origin/ccr-43ba0785-hzlrj5..HEAD` | pass |
| `vale.sh docs` and `npx remark -f --no-stdout` on this note | pass |

### CI runs

Push runs of `4c4c82804` (the fix `453cedf0e`, the merge of `92f31a6aa` and this note), all
green:

| Run | Workflow | Result |
| --- | --- | --- |
| 42506212219 | CI | success. Job 128809298836 (ubuntu, Temurin 21): uPortal-webapp 486 tests, 410 passed, 0 failed, 76 skipped, the same counts as the local run. Commit messages and Prose (Vale) pass |
| 42506212035 | DB integration | success (PostgreSQL 18 job 128809298580, MariaDB 12.3 job 128809298779) |
| 42506212107 | DB copy tool | success |

uPortal-start isn't changed, so the branch has no uPortal-start runs.

## Left open

* **An end-to-end run in a portal.** The original measurement was a `dataExport` and `dataImport`
  of 238 files in uPortal-start. Repeating it needs this branch installed as a uPortal build,
  and the tip build the brief names (`/home/user/wt/.m2-tip`) wasn't on disk. The test runs the
  same export and import code with the real context file and scripts.
* **No CI job imports an export.** uPortal-start's equivalence harness imports its fixture, whose
  permission sets reference the v3-1 script, and compares exports without importing them. A step that
  imports the 5.17 export again would have found MOD-I411.
* **Upstream issue.** `gh search issues --repo uPortal-Project/uPortal` answered 403 (GraphQL
  isn't available from these sessions), so the branch didn't check for an existing upstream issue.

## Proposed LOG rows

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-I411 | Import/export | uPortal's `dataImport` of its own `dataExport` fails on every permission set: the exporter writes `import-permission_set_v5-0.crn` as the script, and `importExportContext.xml` defines an importer bean for `import-permission_set_v3-1` only ("has no registered importer or upgrader support"). 8 of 238 exported files on the quickstart data, on JDK 11 and 21 alike. Found by the jdk21-opens review measurements. Since 5.0.0-M10, when the exporter, the data type and the import script moved to v5-0 and the importer stayed at v3-1. Released 5.17.9 and upstream master have it too. **Upstream candidate** | **Fixed** (`permission-set-import` `453cedf0e`): `permissionSet50Importer` runs `import-permission_set_v5-0.crn` for the v5-0 key. `ImportExportContextTest` exports two permission sets and imports them again, and checks that an importer or upgrader takes the key each exporter writes. Both fail on the tip and on the released 5.17.9 war, and pass with the fix, which applies to 5.17.9 unchanged |
| MOD-I? | Import/export | `StylesheetDescriptorPortalDataType` lists the 2.6 theme and structure keys, and no importer or upgrader takes them, so a uPortal 2.6 stylesheet file fails with "has no registered importer or upgrader support". The same in 4.3.2. The exporters write 4.0 only. Found by the `permission-set-import` check of every data type | **Open**, for the owner: remove the two keys, or document that 2.6 stylesheet files can't be imported. Upstream candidate |

## Status and next step

Ready for review. The push runs passed. The next step is the review's findings, if it sends any.
