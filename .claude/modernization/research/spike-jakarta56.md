# Spike: can the Jakarta switch keep Hibernate 5.6?

Date: 2026-11-10. Base: uPortal `7b24794905` on `ccr-43ba0785-hzlrj5`. Branch
`ccr-43ba0785-hzlrj5-spike-jakarta56`, one local commit `b95f8ca7cc`, not pushed. Worktree:
`/home/user/wt/uPortal-spike-jakarta56`. The proof project is in `spike/jakarta56` there.

## Answer

Yes, with conditions. Spring 6.0, 6.1 and 6.2 all still build `spring-orm` against
`org.hibernate:hibernate-core-jakarta:5.6.15.Final`, and a small project outside the portal boots
and passes on Spring 6.2.19 with that artifact on JDK 21: a JPA unit through
`LocalContainerEntityManagerFactoryBean`, HSQLDB, an entity with `jakarta.persistence`
annotations, an entity region, a natural-id region and a query region in the second-level cache,
and `@Transactional` DAO calls. So P6 can be split into the namespace switch on Spring 6.2 with
Hibernate 5.6 Jakarta, and Hibernate 6.6 afterwards as its own step. The conditions:

1. The cache provider switch (S6b) comes first. That was already the plan. The reason is Spring 6
   and `ehcache-web`, not Hibernate: Hibernate 5.6 Jakarta does run on Ehcache 2 with one extra
   jar (see the evidence).
2. The second-level cache then uses `hibernate-jcache` 5.6.15, the ordinary `javax` build with
   its `hibernate-core` dependency excluded. There is no Jakarta build of it, and it does not
   need one: the jar has no reference to a renamed package.
3. This is proven outside the portal only. I did not build or boot uPortal on Jakarta. Section
   "What was not tested" lists what the first portal run has to show.

## Evidence

### 1. Sources

**Spring 6.0, 6.1 and 6.2 support Hibernate 5.6 Jakarta. Spring 7.0 does not.**

* Spring 6.0's release notes: "make sure to upgrade to Hibernate ORM 5.6.x with the
  `hibernate-core-jakarta` artifact", and "JPA support for Hibernate ORM 6.1 (retaining
  compatibility with Hibernate ORM 5.6)"
  ([6.0 notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes)).
  The 6.1 and 6.2 notes do not withdraw this. The only Hibernate line in the 6.2 notes is
  "For GraalVM native image support only, Hibernate 6.5"
  ([6.2 notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes)).
* On each of the branches `6.0.x`, `6.1.x` and `6.2.x`, `spring-orm/spring-orm.gradle` declares
  `optional("org.hibernate:hibernate-core-jakarta")` and `framework-platform/framework-platform.gradle`
  pins it to `5.6.15.Final`. So Spring 6.2 itself is compiled and tested against the artifact in
  question
  ([6.2.x spring-orm.gradle](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-orm/spring-orm.gradle),
  [6.2.x platform](https://github.com/spring-projects/spring-framework/blob/6.2.x/framework-platform/framework-platform.gradle)).
  The same file pins `jakarta.persistence-api` to 3.0.0, which is the API Hibernate 5.6 Jakarta
  implements.
* The class comment of `HibernateJpaVendorAdapter` and of `HibernateJpaDialect` reads
  "Compatible with Hibernate ORM 5.5/5.6 as well as 6.0/6.1/6.2" on `6.0.x`, "as well as
  6.0/6.1/6.2/6.3" on `6.1.x` and "as well as 6.x" on `6.2.x`
  ([6.2.x adapter](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-orm/src/main/java/org/springframework/orm/jpa/vendor/HibernateJpaVendorAdapter.java),
  [6.2.x dialect](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-orm/src/main/java/org/springframework/orm/jpa/vendor/HibernateJpaDialect.java)).
  The adapter still has a branch for "Hibernate <6.2" that picks the old dialect classes.
  Between 6.0 and 6.2 the three vendor classes changed only in exception translation for commit
  and rollback (6.1) and in a GraalVM block removed from `SpringHibernateJpaPersistenceProvider`.
* `org.springframework.orm.hibernate5` exists on `6.0.x`, `6.1.x` and `6.2.x`
  (`LocalSessionFactoryBean` says "Compatible with Hibernate ORM 5.5/5.6, as of Spring
  Framework 6.0") and is gone on `7.0.x`, which has `org.springframework.orm.jpa.hibernate`
  instead. uPortal uses nothing from `orm.hibernate5`.
* Latest tags: `v6.0.23`, `v6.1.21`, `v6.2.19` (`git ls-remote --tags`, 2026-11-10). The Spring
  versions page lists 6.2 as "Jakarta EE 9-10"
  ([versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions)).

Copies of the files I read are in
`/tmp/claude-0/-home-user/edba9fa3-f7ed-56b9-9df5-6e5fb67f1472/scratchpad/sj/src`.

**Which uPortal classes would break: none, from the sources.**

* uPortal uses 12 classes of `spring-orm`, all in `org.springframework.orm` and `orm.jpa`:
  `JpaTransactionManager`, `HibernateJpaVendorAdapter`, `OpenEntityManagerInViewInterceptor`,
  `OpenEntityManagerInViewFilter`, `EntityManagerHolder`, `EntityManagerFactoryUtils`,
  `PersistenceAnnotationBeanPostProcessor`, `LocalContainerEntityManagerFactoryBean`,
  `JpaDialect`, `EntityManagerFactoryAccessor`, `DefaultJpaDialect` and
  `ObjectOptimisticLockingFailureException`. Each exists on `6.2.x` (fetched one by one).
* uPortal imports about 110 distinct `org.hibernate` types in 115 files. `hibernate-core-jakarta`
  is the same 5.6.15 code with the EE packages renamed, so the `org.hibernate` names and
  signatures stay. Where a signature names an EE type it now names the `jakarta` one, which is
  what renamed uPortal code expects. Nothing here needs a code change beyond the rename. That is
  the point of the split: `@Type`, the dialect classes, `MetadataSchemaTool`, the id optimizers
  and the event listeners stay as they are until the Hibernate 6 step.

**Hibernate 5.6 Jakarta artifacts.** Hibernate's own build at tag 5.6.15 defines four Jakarta
modules: `hibernate-core-jakarta`, `hibernate-envers-jakarta`, `hibernate-testing-jakarta` and
`hibernate-jpamodelgen-jakarta`
([settings.gradle](https://github.com/hibernate/hibernate-orm/blob/5.6.15/settings.gradle), lines
89 to 137). `hibernate-ehcache`, `hibernate-jcache` and `hibernate-infinispan` have no Jakarta
module. So there is **no `hibernate-ehcache-jakarta`**. Maven Central answered 429 to all four
of my metadata and listing requests (three at the start, one retry later), so this comes from
Hibernate's build and not from Central's index. Central did serve the jars themselves:
`hibernate-core-jakarta` 5.6.15.Final and `hibernate-jpamodelgen-jakarta` 5.6.15.Final both
downloaded in the proof.

**Does the split need S6 first? Yes, but not because of Hibernate.**

* `hibernate-ehcache` 5.6.15 has 12 classes. One of them, the generated
  `EhCacheMessageLogger_$logger`, names `javax.transaction.SystemException` and
  `Synchronization`. Nothing else in the jar names a renamed package. With a `javax` JTA API jar
  beside it, it runs on `hibernate-core-jakarta` (proved below).
* `hibernate-jcache` 5.6.15 has 8 classes and no reference to any renamed package
  (`javax.cache` is not renamed).
* Spring 6.0 removed `org.springframework.cache.ehcache`: "has been removed as it was providing
  support for Ehcache 2.x" (6.0 notes; the package is a 404 on `6.0.x` and `6.2.x`). uPortal
  names it in 11 files. And `ehcache-web`, which resource-server's page cache filter extends, is
  built on `javax.servlet`. So Ehcache 2 cannot cross to Spring 6 whatever Hibernate does.

### 2. The proof

Project: `/home/user/wt/uPortal-spike-jakarta56/spike/jakarta56` (Gradle 9.8.1 from the
checkout's wrapper, `--release 17`, run on JDK 21.0.12). It has one entity shaped like uPortal's
(sequence generator, `@Version`, `@NaturalId`, `@Cache(READ_WRITE)`, `@NaturalIdCache`, an
embeddable mapped in an `orm.xml` that keeps uPortal's old `java.sun.com` 2.0 header), a DAO with
`jakarta.annotation.PostConstruct`, `jakarta.persistence.PersistenceContext` and
`@Transactional`, a criteria query through the metamodel that `hibernate-jpamodelgen-jakarta`
generates, and one JUnit test. The test boots the context, writes one row, reads it twice by id,
twice by natural id and twice by a cacheable query, and asserts on Hibernate's statistics.

Spring 6.2 is the newest line the sources name, so I ran only 6.2.19. Run it with
`../../gradlew --no-daemon -p . test -PspringVersion=6.2.19 -PcacheKind=<kind>`.

| Run | Result |
| --- | --- |
| `cacheKind=jcache` (hibernate-jcache 5.6.15 without its hibernate-core, Caffeine 3.2.0 as the JCache provider) | **Passed.** `spring=6.2.19 hibernate=5.6.15.Final`, `l2 put=1 hit=4 naturalIdHit=2 queryHit=1`, region factory `JCacheRegionFactory` |
| the same with `-Pjaxb4=true` (`jakarta.xml.bind-api` 4.0.2 and `jaxb-runtime` 4.0.5 forced, as P6-1 would have for uPortal's own JAXB) | **Passed.** Hibernate 5.6 asks for JAXB 3.0 and parsed the `orm.xml` with 4.0 |
| `cacheKind=ehcache2 -PjavaxJta=true` (hibernate-ehcache 5.6.15 without its hibernate-core, `ehcache-core` 2.6.11, a subclass of `EhcacheRegionFactory` with the same overrides as uPortal's, missing cache strategy `fail`, plus `jboss-transaction-api_1.2_spec` 1.1.1) | **Passed.** Same counts, region factory `spike.SpikeEhcacheRegionFactory` |
| `cacheKind=ehcache2` without the JTA jar | **Failed at boot.** `BeanCreationException` for `entityManagerFactory`. First cause: `java.lang.ClassNotFoundException: javax.transaction.SystemException`, reached from `EhCacheMessageLogger.<clinit>` through `EhcacheRegionFactory.<clinit>` |

Logs: `/tmp/claude-0/-home-user/edba9fa3-f7ed-56b9-9df5-6e5fb67f1472/scratchpad/sj/logs`
(`s62-jcache-orm.log`, `s62-jcache-jaxb4-retry.log`, `s62-ehcache2-javaxjta-orm.log`,
`s62-ehcache2-nojta.log`).

Two more things the runs showed:

* The Ehcache 2 pass is a workaround, not a supported pairing. The generated logger class does
  not implement the three `CoreMessageLogger` methods that now take `jakarta.transaction` types.
  A call to one of them through the Ehcache logger would end in `AbstractMethodError`. I saw no
  such call, and Hibernate's core calls them on its own logger, but I would not ship on it.
* Caffeine's JCache provider logged a warning for the region `spike.Thing##NaturalId`:
  `ConfigException$BadPath ... Token not allowed in path expression: '##NaturalId'`. It then
  created the cache with defaults and the test passed. Hibernate names every natural-id region
  that way, and uPortal has 24 `@NaturalIdCache` entities. This is a note for S6b and has nothing
  to do with the namespace: build the regions in code from the region list, and do not rely on
  Caffeine's `application.conf` lookup by cache name.

Network: Maven Central answered 429 to the 4 metadata and listing requests, to
`javax.transaction-api` 1.3 (I used the JBoss spec jar from the Gradle cache instead) and once to
`jaxb-runtime` 4.0.5 (the single retry some minutes later worked). No 403 and no policy block.
Nothing was pushed and no CI run was started.

### 3. How far would the uPortal build get (estimate from the code, nothing migrated)

Counts are files at `7b24794905` that name the package, over Java, Groovy, JSP, tag, XML, TLD
and `.crn` files.

The Hibernate side of the switch is three build edits and no code: `hibernate-core` becomes
`hibernate-core-jakarta` (4 build files), `hibernate-jpamodelgen` 5.6.3 becomes
`hibernate-jpamodelgen-jakarta` 5.6.15 (12 build files), and the cache module is whatever S6b
left, with `hibernate-core` excluded. Jadira usertype 6.0.1 (12 files) can stay for this step:
its `spi` jar names `javax.persistence` only in repository classes uPortal does not use. That
last point is from a jar scan and is not run.

By module (57 module directories):

* **Nothing to rename (9):** `uPortal-concurrency`, `-health`, `-marketplace`,
  `-groups-filesystem`, `-groups-grouper`, `-groups-ldap`, `-groups-local`, and the two dialect
  modules.
* **Mechanical rename plus dependency changes (25):** `uPortal-core`, `-content-portlet`,
  `-content-publishing`, `-db-migrate`, `-index`, `-io-core`, `-layout-core`, `-persondir`,
  `-security-core`, `-security-filters`, `-security-services`, `-security-xslt`, `-session`,
  `-soffit-core`, `-soffit-renderer`, `-tenants`, `-tools`, `-utils-jmx`, `-utils-url` and the
  six `uPortal-pluto` modules. Pluto also needs the Servlet 6 methods on its wrappers and JAXB 4
  (46 files), which the earlier Jakarta spike already did.
* **Mechanical once a planned step on `javax` is done, or with a version that exists (17):**
  Ehcache callers that S3 and S6 move (`-events`, `-groups-core`, `-groups-pags`, `-hibernate`,
  `-layout-impl`, `-rdbm`, `-security-permissions`, `-soffit-connector`),
  `HandlerInterceptorAdapter` (`-i18n`, `-security-mvc`, `-url`), Web Flow (`-api-internal`,
  `-spring`, `-spring-portlet`, `-portlets`, `-api-rest`; ADR 0012 removes the flows, and Web
  Flow 3.0.2 exists for Spring 6), CAS client 4.0.4 (`-security-authn`), springfox and json-lib.
* **Touched by a library with no Jakarta release (6):** `-utils-core`, `-rendering`, `-web` and
  `-webapp` use resource-server (16 files). `-io-types`, `-groups-smartldap` and `-webapp` use
  Cernunnos (17 files). `-webapp` wires person-directory's `RequestAttributeSourceFilter` in one
  class.

**Smallest set of blockers for a portal that boots on Jakarta.** It is the same list with or
without Hibernate 6, because none of the blockers is a Hibernate one:

1. S6b: no Ehcache 2, no `org.springframework.cache.ehcache`, no `ehcache-web`.
2. resource-server: a Jakarta build, or its classes copied in (survey question 3).
3. person-directory: the one servlet filter copied in. Whether 1.8.16 runs on Spring 6 is still
   unconfirmed.
4. Cernunnos: unconfirmed whether it loads its servlet task classes at start.
5. Web Flow 2.5.1 gone or raised to 3.0.2, CAS client 4.0.4, Spring Security 6.5, and the small
   P5 items (multipart resolver, interceptor adapter, springfox, json-lib).
6. In-tree Pluto and the portlet API on `jakarta.servlet`, Tomcat 10.1 and the transformed
   portlet WARs in uPortal-start (P6-2).

The split removes one thing from that step and adds none: all of the Hibernate 6 work.

### What was not tested

* uPortal itself on Jakarta. Not compiled, not booted.
* Tomcat 10.1, `OpenEntityManagerInViewFilter`, two persistence units, `global.hbm.xml` with its
  `typedef`, uPortal's custom types on Jadira, `MetadataSchemaTool`, the dialect resolver and
  the database vendors. All of these are Hibernate 5.6 code paths that the Jakarta artifact
  renames but does not change, so I expect them to behave as on the current tip. Only a portal
  run and G-db can confirm it.
* Spring 6.0 and 6.1 were not run. The sources say they work, and they are not the target.

## What the split saves and costs against ADR 0002's single step

Saves:

* The atomic step P6-1 with P6-2 loses its Hibernate half. That step already has to change two
  repositories, every portlet WAR, Tomcat and Spring at once. Without the split it would also
  carry Hibernate 6's type system (14 files with `@Type`, 12 custom type files), 31 files of
  criteria queries on a new query engine, the removed dialect classes, the schema tool, the id
  generators and the SQL Server and MariaDB sequence question (survey section 6).
* A failure in P6-1 can then come from the namespace, Spring 6, Tomcat 10.1 or the portlets, and
  not from Hibernate. That is the decision driver "a failure in a step should point to one
  cause", applied to the largest step of the plan.
* G-db should not move at P6-1, because the SQL and DDL come from the same Hibernate code as
  today. Any G-db change at that step is then a finding in its own right.
* Removing Jadira (ADR 0007) has to be done before Hibernate 6.6 and no longer before the
  namespace switch.

Costs:

* One more checkpoint that is never released, with a pairing nobody else is likely to run for
  long: Spring 6.2 and Hibernate 5.6 are both out of open-source support.
* The Hibernate coordinates change twice (to `-jakarta` at P6-1, to 6.6 at P6-3), in about 16
  build files each time.
* One more full run of the four required checks, G-db on every vendor included.
* The cache module is the `javax` build of `hibernate-jcache` with an exclude. It works and the
  jar is clean, but it is not a pairing Hibernate publishes.
* ADR 0002 names four steps that cannot be split and counts P6-1 as one. It needs an amendment.

## Order against the cache plan (S3 to S6)

The split does not change the order. S3, S4 and S5 run on `javax` as planned. S6a, S6b and S6c
are merged before P6-1, as `notes/p4-3-plan.md` already says ("All of S6 must be merged before
P6").

What the spike adds:

* The reason S6b must precede P6-1 is Spring 6 and `ehcache-web`. Hibernate 5.6 Jakarta would
  tolerate Ehcache 2. So there is no shortcut in which the namespace switch goes first and the
  cache provider later.
* S6b should put Hibernate on `hibernate-jcache` or on a uPortal region factory that does not
  extend a `hibernate-ehcache` class. Either one crosses P6-1 without change, and
  `hibernate-jcache` also exists for 6.6. The Ehcache 2 workaround above should not be used.
* S6b should create the regions in code (the `##NaturalId` warning above).

Resulting order: S3, S4, S5, then S6a, S6b, S6c, then P6-1 with P6-2 (Spring 6.2, Hibernate 5.6
Jakarta, Tomcat 10.1), then P6-3 (Hibernate 6.6), then P7-1.

## Recommendation

Take the split. The sources and the proof agree, the extra work is small (artifact names and one
more run of the checks), and it takes the one part with database risk out of the one step that
cannot be made smaller in any other way.

Three things to do with it:

1. Amend ADR 0002: step 3 becomes "Spring 6.2 with Hibernate 5.6 Jakarta on Tomcat 10.1" and a
   new step "Hibernate 6.6" follows it. Both are checkpoints that are never released.
2. Make the first task of P6-1 a portal-level check of the Hibernate side alone: after the
   rename, run the JPA module tests and the persistence goldens on `hibernate-core-jakarta`
   before anything else is debugged. If that fails for a reason this spike missed, fall back to
   the single step. The fallback costs nothing, because the rename is the same.
3. Write the two S6b points above into the cache plan before S6b starts.

Proposed LOG rows (for the coordinator, not edited by me):

* `MOD-D?` P6 is split: the namespace switch keeps Hibernate 5.6 through
  `hibernate-core-jakarta` 5.6.15 on Spring 6.2, and Hibernate 6.6 becomes P6-3. Evidence: this
  report.
* `MOD-I?` There is no Jakarta build of `hibernate-ehcache`. The `javax` build fails on
  `hibernate-core-jakarta` with `ClassNotFoundException: javax.transaction.SystemException`
  unless a `javax` JTA API jar is present.
* `MOD-I?` Caffeine's JCache provider warns `BadPath` for Hibernate's `##NaturalId` region
  names and falls back to defaults. S6b must create regions in code.
