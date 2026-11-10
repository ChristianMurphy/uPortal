fix(io): import the permission sets that dataExport writes

##### Checklist

-   [x] the [individual contributor license agreement](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#individual-contributor-license-agreement) is signed
-   [x] commit message follows [commit guidelines](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#commit)
-   [x] tests are included

##### Description of change

**Problem.** `dataImport` can't read the permission sets that `dataExport` writes. Each file fails with "has no registered importer or upgrader support". The exporter marks each file with the script `import-permission_set_v5-0.crn`, but `importExportContext.xml` only registers an importer for the v3-1 script. It has been this way since 5.0.0. `dataInit` works because uPortal-start's data files still use the v3-1 script.

**Fix.** Register `permissionSet50Importer` for the v5-0 key. It runs the v5-0 script, which already exists and which the v3-1 script calls. No Java code changes.

**Test.** `ImportExportContextTest` loads `importExportContext.xml` and checks that an importer or upgrader accepts the key each exporter writes. It fails on `master` and passes with the fix. Permission sets were the only gap. Upstream's CI build command passes on this branch on my fork (JDK 11, Ubuntu).

**Verified.** On uPortal-start `master` with HSQLDB, I exported the permission sets, deleted two of them and imported the exported files. On `master` all 8 files fail with "has no registered importer or upgrader support" and the rows stay deleted. With this change all 8 import and the rows are back. The workflows and scripts for these runs are on my fork's branch [`test/verify-fixes-on-databases`](https://github.com/ChristianMurphy/uPortal/tree/test/verify-fixes-on-databases).

If you try this by hand: uPortal-start's `dataImport` task currently fails before it runs (`Task with path ':overlays:uPortal:prepareSkinResources' not found`). Gradle's short name `dataImp` gets past that.

**Where this comes from.** I'm working on a fork that upgrades uPortal's dependencies (Spring 5.3 so far, heading for Spring 7, Hibernate 7 and Jakarta EE 11). That fork's CI runs uPortal-start's Playwright suite against HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and a two-node cluster test. I found this there when a re-import of the portal's own export failed on 8 of 238 files. The bug isn't caused by the upgrade: the code is the same in `v5.17.9` and `master`, so I ported the fix here on its own.
