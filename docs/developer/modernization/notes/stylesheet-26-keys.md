<!-- markdownlint-disable -->

# The uPortal 2.6 stylesheet keys are removed (MOD-I412)

Branch `ccr-43ba0785-hzlrj5-stylesheet-26-keys` in uPortal, from the integration branch at
`7b24794905`. The uPortal-start branch of the same name points at its integration tip
`39ca485901` and has no commit of its own. It exists so that uPortal-start's runs build this
uPortal branch.

## What was wrong

`StylesheetDescriptorPortalDataType` listed three data keys: the 4.0 `stylesheet-descriptor`
key, and the keys of uPortal 2.6 `<theme>` and `<structure>` files
(`import-theme_v2-6.crn` and `import-structure_v2-6.crn`). `importExportContext.xml` has an
importer for the 4.0 key only, and no upgrader. The import recognised a 2.6 file as a
stylesheet descriptor and then failed on it with "has no registered importer or upgrader
support". Neither script is in the repository. MOD-D56, item 6, is the owner's decision: remove
the keys.

## The change

* `StylesheetDescriptorPortalDataType` lists the 4.0 key only. The constants
  `IMPORT_26_THEME_DATA_KEY`, `IMPORT_26_STRUCTURE_DATA_KEY`, `LEGACY_THEME_QNAME` and
  `LEGACY_STRUCTURE_QNAME` are gone. Nothing else in uPortal or uPortal-start used them.
* `ImportExportContextTest.testEveryListedDataKeyHasAnImporterOrUpgrader` checks every key of
  every data type against the importers and upgraders that `importExportContext.xml` defines.
  The existing test checked only the keys that the exporters write.
* `docs/UPGRADING-6.0.md` has the section "uPortal 2.6 theme and structure files are not
  imported".

A directory import now skips a 2.6 theme or structure file with the warning "No IPortalDataType
configured" and imports the rest, where 5.x stopped at the file. An import of the one file still
fails, with "No IPortalDataType configured". `PortalDataKeyFileProcessorTest` already covers
that warning for a file with an unlisted key.

## Evidence

The new test on the tip's `StylesheetDescriptorPortalDataType` (JDK 21, HSQLDB), before the keys
were removed:

```
ImportExportContextTest > testEveryListedDataKeyHasAnImporterOrUpgrader FAILED
StylesheetDescriptorPortalDataType lists <theme script="classpath://org/jasig/portal/io/import-theme_v2-6.crn">, and importExportContext.xml has no importer or upgrader for <theme script="classpath://org/jasig/portal/io/import-theme_v2-6.crn">
StylesheetDescriptorPortalDataType lists <structure script="classpath://org/jasig/portal/io/import-structure_v2-6.crn">, and importExportContext.xml has no importer or upgrader for <structure script="classpath://org/jasig/portal/io/import-structure_v2-6.crn">
```

Those two keys were the only ones without an importer or upgrader. With the keys removed, the
three tests of `ImportExportContextTest` and the tests of `uPortal-io-types` pass on JDK 21 and
on JDK 25. `spotlessCheck` and `:uPortal-io:uPortal-io-types:spotbugsMain` pass.

## Runs

The push of uPortal `48a09f9f35`, and of the uPortal-start branch at `39ca485901`:

| Run | Result |
| --- | --- |
| uPortal CI 42824311892 | passed (build and tests on Java 21 and Java 25, Prose, Commit messages) |
| uPortal DB integration 42824311907 | passed |
| uPortal DB copy tool 42824311915 | passed |
| uPortal-start Integration 42824339664 | passed. Build uPortal built uPortal `48a09f9f35` from this branch (notice of job 129858586454) |
| uPortal-start CI 42824339223 | passed |
| uPortal-start HTTP replay 42824339229 | passed |
| uPortal-start Migration equivalence 42824339227 | passed |
| uPortal-start G-index 42824339088 | passed |

## Proposed LOG rows

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-I412 (update) | Import/export | `StylesheetDescriptorPortalDataType` listed the 2.6 theme and structure keys, and no importer or upgrader took them. | **Fixed** (uPortal `fix(io): stop listing the 2.6 stylesheet keys that nothing imports`, MOD-D56 item 6): the data type lists the 4.0 key only, and the four constants for the 2.6 keys are gone. `ImportExportContextTest` now checks every key of every data type for an importer or an upgrader, and failed on the two keys before the change. A directory import skips a 2.6 theme or structure file with a warning, where 5.x stopped at it (UPGRADING-6.0, "uPortal 2.6 theme and structure files are not imported"). Upstream candidate (`notes/stylesheet-26-keys.md`). |
