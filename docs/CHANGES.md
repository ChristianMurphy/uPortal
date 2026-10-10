## Next Release

- **Gradle wrapper bumped from 6.9.4 to 8.14.6.** Contributors using a
  system Gradle install (instead of the bundled wrapper) must move to
  Gradle 8.14 or newer; the wrapper remains the recommended entry point
  and now verifies the distribution's SHA-256. The build no longer
  declares `jcenter()`; only `mavenCentral()` is consulted. The
  unmaintained `org.openrepose` JAXB plugin was replaced with an
  Ant-based XJC task in `uPortal-io-jaxb` and `uPortal-api-search`
  (generated sources are unchanged). Plugins bumped alongside the
  wrapper: `nebula.lint` 17.8.0 → 20.6.2, `net.ltgt.errorprone` 4.4.0 →
  5.1.1, `com.github.spotbugs` 4.8.0 → 6.5.12, `net.researchgate.release`
  3.1.0 → 3.2.0.
- **Artifacts are published with `maven-publish`** instead of the
  legacy `maven` plugin (removed in Gradle 7). `./gradlew install` and
  `./gradlew release` work as before, and the `ossrhUsername`,
  `ossrhPassword`, `ossrhUrl` and `ossrhUrlSnapshot` properties keep
  their names. Same coordinates and files (POM, jar or war, sources,
  javadoc, signatures). POM changes: test-scoped dependencies are no
  longer listed; `runtimeOnly` dependencies (for example `jjwt-impl` in
  `uPortal-security-core` and `uPortal-soffit-core`) are now listed with
  `runtime` scope instead of being dropped; `uPortal-security-filters`
  lists `slf4j-api` as `runtime`; the developer organization is written
  correctly.
- **Java formatter switched from the sherter google-java-format plugin
  to Spotless.** The previous plugin (`com.github.sherter.google-java-format`
  0.9) was last released in 2019 and cannot run google-java-format on
  JDK 16+. Spotless takes its place with the same AOSP style and still
  runs as part of `check`. Use `./gradlew spotlessCheck` to verify and
  `./gradlew spotlessApply` to format; the old `./gradlew verGJF` and
  `./gradlew goJF` commands keep working. Seven stray no-op empty
  statements (`;` after a closing brace) were removed so the switch
  does not reformat anything else.
- **Classes are compiled with `--release 11` and `-parameters`.** The
  bytecode level stays Java 11, and the compiler now also rejects Java
  12+ APIs whichever JDK runs the build. `-parameters` records method
  parameter names in the class files (needed by Spring 6.1+); Spring 4.3
  resolves the same names as before.
- **Test libraries:** Mockito 4.11 → 5.24 (loaded as a `-javaagent` by
  the test tasks; `mockito-inline` is no longer needed), and the Groovy
  tests in `uPortal-webapp` now compile and run on Groovy 4.0 with Spock
  2.4 (`groovyTestVersion`). The Groovy shipped in the war and used by
  `uPortal-tools` stays at 3.0 (`groovyVersion`).
- **Java sources were reformatted with google-java-format 1.24.0**
  (javadoc and blank-line layout only; 409 files). Rebase open branches
  with `./gradlew spotlessApply` before resolving conflicts, and run
  `git config blame.ignoreRevsFile .git-blame-ignore-revs` so `git blame`
  skips the reformat.
- **JDK 21 or newer is required to build uPortal; CI builds and tests
  on JDK 21 and 25.** The classes are still compiled for Java 11
  (`--release 11`), so deployments can stay on Java 11. `.sdkmanrc` now
  pins Temurin 25. Error Prone moved to 2.50.0 (four new checks that flag
  existing code are disabled for now: `BanJNDI`, `BoxedPrimitiveEquality`,
  `DoubleBraceInitialization`, `SelfAssertion`) and the formatter to
  google-java-format 1.36.1 via Spotless 8.10.4 (no formatting change).
- **Running the portal on JDK 24 or newer needs
  `-Djdk.xml.enableExtensionFunctions=true`** (for example in
  `CATALINA_OPTS`). Since JDK 24 the JDK's default `jaxp.properties`
  disables XSLT extension functions, which uPortal's theme, security
  helper and import-upgrade stylesheets call; without the flag rendering
  fails with "The first argument to the non-static Java function
  'getUrlString' is not a valid object reference". The test JVMs set it.
  JDK 11–21 need nothing.
- **Gradle wrapper bumped from 8.14.6 to 9.8.1.** Plugins: `nebula.lint`
  → 21.3.0 (its `duplicate-dependency-class` rule is no longer run),
  CodeNarc → 3.7.0-groovy-4.0, JaCoCo pinned to 0.8.15. The unmaintained
  `com.github.kt3k.coveralls` plugin was removed, so there is no
  `./gradlew coveralls` task any more: CI runs
  `./gradlew build jacocoAggregateReport` and uploads the aggregate
  JaCoCo report with the Coveralls GitHub Action. Jars are now built
  reproducibly (fixed entry dates); the war keeps real file dates so
  servlet containers still notice changed JSPs.
- **SpotBugs and FindSecBugs are a CI gate.** A `Static analysis` CI job
  runs `./gradlew spotbugsMain` (effort max, medium threshold) and fails
  on any finding that isn't in the module's baseline under
  `config/spotbugs/baseline/`; `build` doesn't run SpotBugs. See
  [Static Analysis](developer/other/STATIC_ANALYSIS.md).

## v5.17.3

- substantial frontend modernization landed in [#2915](https://github.com/uPortal-Project/uPortal/pull/2915):
  Bootstrap 3 → 5, jQuery upgraded, LESS → SCSS, and Fluid Infusion
  removed from the `respondr` skin. **Customized skins will need
  updating** — review your overrides under
  `uPortal-webapp/src/main/webapp/media/skins/` for `.less` files
  (now `.scss`), Bootstrap 3/4 class names, and any direct uses of the
  Fluid library.

## v5.17.2

- **Java 11 is now the minimum required version.** `sourceCompatibility`
  was bumped from `1.8` to `11` in `build.gradle`. Deployers running
  Java 8 must upgrade their JVM before deploying this release. The
  bump was driven by a CVE fix in a transitive dependency that
  required Java 11; uPortal-start's Tomcat runtime should already be
  on Java 11.

## v5.15.2

- new external storage option (Redis) for sessions; see `README.md` under new uPortal-session submodule

## v5.15.0

- new cache, org.apereo.portal.i18n.RDBMLocaleStore.userLocales in `ehcache.xml`/`ehcache-no-jgroups.xml`
    ```xml
         <!--
         | Caches Window State Collection -
         | - Many-to-One join of StyleSheet and Window State
         | - not replicated - represents local file system data
         +-->
        <cache name="org.apereo.portal.portlet.registry.PersistentPortletEntityWrapper.windowStates"
            eternal="false" maxElementsInMemory="50" overflowToDisk="false" diskPersistent="false"
            timeToIdleSeconds="0" timeToLiveSeconds="1800" memoryStoreEvictionPolicy="LRU" statistics="true" />

        <!--
         | Caches search results from findParentGroups() in RDBMEntityGroupStore.
         | - 1 x search criteria
         +-->
        <cache name="org.apereo.portal.groups.RDBMEntityGroupStore.parentGroupEntity"
            eternal="false" maxElementsInMemory="500" overflowToDisk="false" diskPersistent="false"
            timeToIdleSeconds="0" timeToLiveSeconds="300" memoryStoreEvictionPolicy="LRU" statistics="true" >
            <cacheEventListenerFactory class="org.apereo.portal.utils.cache.SpringCacheEventListenerFactory" properties="beanName=insufficientSizeCacheEventListener" listenFor="local" />
        </cache>

        <!--
         | Caches search results from findParentGroups() in RDBMEntityGroupStore.
         | - 1 x search criteria
         +-->
        <cache name="org.apereo.portal.groups.RDBMEntityGroupStore.parentGroupEntityGroup"
            eternal="false" maxElementsInMemory="500" overflowToDisk="false" diskPersistent="false"
            timeToIdleSeconds="0" timeToLiveSeconds="300" memoryStoreEvictionPolicy="LRU" statistics="true" >
            <cacheEventListenerFactory class="org.apereo.portal.utils.cache.SpringCacheEventListenerFactory" properties="beanName=insufficientSizeCacheEventListener" listenFor="local" />
        </cache>

        <!--
         | Caches person locale
         | - 1 per person
         +-->
        <cache name="org.apereo.portal.i18n.RDBMLocaleStore.userLocales"
               eternal="false" maxElementsInMemory="2000" overflowToDisk="false" diskPersistent="false"
               timeToIdleSeconds="0" timeToLiveSeconds="600" memoryStoreEvictionPolicy="LRU" statistics="true"/>
    ```

- new property, org.apereo.portal.rest.search.PortletsSearchStrategy.displayFavoriteFlag in `portal.properties`
    ```properties
    ##
    ## Flag to enable or disable the display of the favorite flag in the results
    ##
    org.apereo.portal.rest.search.PortletsSearchStrategy.displayFavoriteFlag:true
    ```

## v5.14.0

- new properties, HTTP Security Headers in `security.properties`
    ```properties
    ##
    ## Tomcat HTTP Security Headers
    ##

    # antiClickJackingEnabled:  X-Frame-Options header
    sec.anti.click.jacking.enabled=false
    # X-Frame-Options: deny, sameorigin, allow-from
    sec.anti.click.jacking.options=sameorigin
    # If allow-from is selected above, add URI
    sec.anti.click.jacking.uri=

    # Content-Security-Policy: default-src, script-src, style-src, img-src
    # See more details at: https://content-security-policy.com/
    sec.content.sec.policy.enabled=false
    sec.content.sec.policy=default-src 'self'

    # Strict-Transport-Security: max-age=###; includeSubDomains; preload
    sec.hsts.enabled=false
    sec.hsts.maxage.seconds=31536000
    sec.hsts.include.subdomains=true
    sec.hsts.preload=false

    # X-Content-Type-Options: "nosniff" will be used if enabled is set to true
    sec.x.content.type.enabled=false

    # Referrer-Policy available directives to pass include:
    # See more details at: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy
    sec.referrer.policy.enabled=false
    sec.referrer.policy=no-referrer
    ```

v5.13.1
- N/A

v5.13.0 - Portlet List, Analytics Event
- new table, UP_PORTLET_LIST for PortletList.java in uPortal-api-rest module
- new table, UP_PORTLET_LIST_ITEM for PortletListItem.java in uPortal-api-rest module
- new property, events.analytics.log.level in `portal.properties`
    ```properties
    ##
    ## Configure which analytics events (e.g. link clicks) are saved based on the the following:
    ## - NONE - no events are recorded
    ## - AUTHENTICATED - only record events from known users
    ## - ALL - record events from all users, including guest/anonymous users
    #events.analytics.log.level:NONE
    ```
- new property, org.apereo.portal.events.LoginEvent.captureUserIpAddresses in `portal.properties`
    ```properties
    ##
    ## Configure Login Events (raw) to capture user IP addresses. This is not included in aggregation.
    ## N.B. This raises privacy concerns and should only be enabled after very careful consideration.
    org.apereo.portal.events.LoginEvent.captureUserIpAddresses=false
    ```

v5.12.0
- N/A

v5.11.1
- new property, org.apereo.portal.ehcache.filename in `portal.properties`
    ```properties
    ##
    ## Configure which file in the properties/ directory in the classpath for ehcache.
    ## This is often used to selected between the default ehcache.xml file with JGroups
    ## vs. the ehache-no-jgroups.xml file that has JGroups removed.
    #
    #org.apereo.portal.ehcache.filename:ehcache.xml
    ```

v5.11.0
- fixed table reference, UP_PORTLET_MDATA -> UP_PORTLET_DEF_MDATA for PortletLocalizationData.java in uPortal-content-portlet module

v5.10.0
- new properties, PersonalizationFilter in `portal.properties`
    ```properties
    # Personalization
    # ---------------
    # Disabled by default
    #
    # org.apereo.portal.utils.web.PersonalizationFilter.enable=false

    # `prefix` and `pattern` are combined to define how the placement tokens
    # appear in the portlet-definitions, such as:
    #   `<desc>Bookmarks for @up@apereo.displayName@up@</desc>`
    #
    # org.apereo.portal.utils.personalize.PersonalizerImpl.prefix=apereo.
    # org.apereo.portal.utils.personalize.PersonalizerImpl.pattern=@up@(.*?)@up@
    ```

v5.9.0
- new property, org.apereo.portal.portlet.worker.threadPool.queueSize in `portal.properties`
    ```properties
    ##
    ## Portlet worker thread pool queue max size
    ##
    #org.apereo.portal.portlet.worker.threadPool.queueSize=0
    ```

v5.8.2
- replace property, cas.authenticationFilter.service with portal.allServerNames in `security.properties`
    ```properties
    # All server names values for multi server name management, separator is a space
    # This property should be set/overridden in PORTAL_HOME/uPortal.properties
    # Example: portal1.univ.edu portal2.univ.edu
    portal.allServerNames=${portal.server}
    ```
- new property, org.apereo.portal.index.relativePath in `portal.properties`
    ```properties
    # Search Indexing
    # ---------------
    # Search indexing was introduced in v5.5.0. To disable indexing, uncomment
    # and set the value of this property to "#{null}" (without double quotes)
    #
    #org.apereo.portal.index.relativePath=/WEB-INF/index
    ```

v5.8.1
- new cache, org.apereo.portal.groups.RDBMEntityGroupStore.search in `ehcache.xml`/`ehcache-no-jgroups.xml`
    ```xml
        <!--
         | Caches search results from searchForGroups() in RDBMEntityGroupStore.
         | - 1 x search criteria
         +-->
        <cache name="org.apereo.portal.groups.RDBMEntityGroupStore.search"
            eternal="false" maxElementsInMemory="500" overflowToDisk="false" diskPersistent="false"
            timeToIdleSeconds="0" timeToLiveSeconds="300" memoryStoreEvictionPolicy="LRU" statistics="true" >
            <cacheEventListenerFactory class="org.apereo.portal.utils.cache.SpringCacheEventListenerFactory" properties="beanName=insufficientSizeCacheEventListener" listenFor="local" />
        </cache>
    ```

v5.8.0
- new property, cas.ticketValidationFilter.encodeServiceUrl in `security.properties`
    ```properties
    ## Some CAS servers, like the CAS server in uPortal-start can not handle encoded service URLs.
    ## Set the following property to false to disable encoding of service URLs.
    ## See https://groups.google.com/a/apereo.org/d/msg/uportal-user/44Uw1YP8_Mg/hLaTlEVZFAAJ
    ## for the discussion regarding this property
    #
    #cas.ticketValidationFilter.encodeServiceUrl=true
    ```
- new property, org.apereo.portal.rest.search.PortletsSearchStrategy.displayScore in `portal.properties`
    ```properties
    ##
    ## Flag to enable or disable the display the the search strategy score of the results
    ##
    org.apereo.portal.rest.search.PortletsSearchStrategy.displayScore:true
    ```

v5.7.1
- N/A

v5.7.0
- N/A

v5.6.1
- N/A




