fix(web): give each page render its own copy of the skin elements

##### Checklist

-   [x] the [individual contributor license agreement](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#individual-contributor-license-agreement) is signed
-   [x] commit message follows [commit guidelines](https://github.com/uPortal-Project/uPortal/blob/master/docs/CONTRIBUTING.md#commit)
-   [x] tests are included

##### Description of change

**Problem.** Under load, a page render can answer 500 with a `NullPointerException` in XSLTC's `BasisLibrary.nodeList2Iterator`, called from the `skinResources` template. With aggregation on, `ResourcesElementsProviderImpl` (resource-server-utils) builds a skin's head elements once and gives the same `DocumentFragment` to every request. The JDK's DOM isn't safe to read from two threads, so two renders that copy the elements at the same moment can read `null`.

**Fix.** `ResourcesElementsXsltcHelper.getResourcesXmlFragment` returns a copy of the elements, made while it holds the shared list's lock. A skin has about a dozen head elements, so the copy is cheap. The XSLT doesn't change. A fix in resource-server-utils would help its other users too, but this change doesn't depend on one.

**Test.** `ResourcesElementsXsltcHelperTest` checks that each call gets its own nodes, and reads the elements from four threads for three seconds. Both tests fail on `master` and pass with the fix. The threaded test depends on timing. Upstream's CI build command passes on this branch on my fork (JDK 11, Ubuntu).

**Verified.** I ran a portal built from `master` with aggregation on and loaded pages in parallel. The exception showed up in 11 of 14 runs of that check, 15 failed renders in all. With this change, two runs of the same check had none, one of them with 20 minutes of parallel loads, and the head's CSS and JS links were unchanged. The workflows and scripts for these runs are on my fork's branch [`test/verify-fixes-on-databases`](https://github.com/ChristianMurphy/uPortal/tree/test/verify-fixes-on-databases).

**Not verified.** The exception is rare, so two clean runs show that the fix fits the cause. They don't prove it can never happen.

**Where this comes from.** I'm working on a fork that upgrades uPortal's dependencies (Spring 5.3 so far, heading for Spring 7, Hibernate 7 and Jakarta EE 11). That fork's CI runs uPortal-start's Playwright suite against HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server and Oracle, and a two-node cluster test. The exception showed up there in 3 of 148 portal logs, each time as a page that answered 500 once and worked on the next try. The bug isn't caused by the upgrade: the code is the same in `v5.17.9` and `master`, so I ported the fix here on its own.
