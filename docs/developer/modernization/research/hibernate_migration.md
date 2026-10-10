# Hibernate ORM migration path for uPortal: 4.3.11.Final (javax/JPA 2.1) → 5.6 → 6.6 → 7.x (Jakarta Persistence 3.2), in-place DB upgrade

As of 2026-10-10. Method: I read the official `migration-guide.adoc` from every hibernate-orm branch (5.0 through 7.4, 8.0, and main = 8.1) on GitHub, plus the hibernate.org site data files, the Hibernate source on each branch, Maven Central metadata, the Spring Framework wiki (cloned), and the OpenRewrite `rewrite-hibernate` repo (cloned). hibernate.org, docs.openrewrite.org, endoflife.date and thorben-janssen.com could not be reached from this environment (proxy/DNS policy), so facts from those sites come from search-engine summaries and are labeled that way. I also grepped the local uPortal checkout at `/home/user/uPortal` so the notes cover what uPortal actually uses. Those findings link to local files (`file://`).

---

## Q1. What is the latest Hibernate ORM release (support status, min Java, Jakarta Persistence version) as of Oct 2026, and which versions are still supported?

### Takeaway
The latest stable line is **Hibernate ORM 7.4** (latest patch **7.4.12.Final**). It needs Java 17+ (tested on 17/21/25/26), implements Jakarta Persistence 3.2 and targets Jakarta EE 11. **8.0 is still in beta** (8.0.0.Beta3). It targets Jakarta Persistence 4.0 and EE 12, and is not GA as of Oct 2026. 6.6 still gets patch releases (6.6.58) but is officially "limited support". 5.x and 4.x are long EOL. The supported practical target for uPortal is 7.4, or whatever 7.x line Spring 7.x is tested against (see Q5).

### Cited Findings
- Latest published `org.hibernate.orm:hibernate-core` per Maven Central metadata is `8.0.0.Beta3`, metadata lastUpdated 2026-10-04. The highest final version per line is 6.6.58.Final, 7.0.10.Final, 7.1.36.Final, 7.2.25.Final, 7.3.13.Final and **7.4.12.Final**. — [Maven Central hibernate-core metadata](https://repo1.maven.org/maven2/org/hibernate/orm/hibernate-core/maven-metadata.xml)
- The git tags match. 8.0 only has `8.0.0.Alpha1`, `Beta1`, `Beta2` and `Beta3`. There are branches `7.0`…`7.4`, `8.0`, `wip/9.0` and `main`, and `main`'s migration guide is titled "8.1 Migration Guide". — [hibernate-orm tags](https://github.com/hibernate/hibernate-orm/tags); [main migration guide](https://github.com/hibernate/hibernate-orm/blob/main/migration-guide.adoc)
- The last 5.x release is `org.hibernate:hibernate-core` 5.6.15.Final. — [Maven Central (org.hibernate) metadata](https://repo1.maven.org/maven2/org/hibernate/hibernate-core/maven-metadata.xml)
- Integration constraints from the hibernate.org site data:
  - **7.4**: Java 17/21/25/26, Jakarta Persistence 3.2, Jakarta Data 1.0, Jakarta EE 11, Quarkus 3.37–3.40, WildFly 41, Spring Boot 4.1. Release highlights: `@Temporal`, `@Audited`, limits & fetch joins, `hbm.xml` transformation. — [7.4 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/7.4/series.yml)
  - **7.3**: Java 17/21/25/26, Jakarta Persistence 3.2, EE 11, WildFly 40. — [7.3 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/7.3/series.yml)
  - **7.2**: `status: limited-support`, Java 17/21/25, Jakarta Persistence 3.2, Spring Boot 4.0. — [7.2 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/7.2/series.yml)
  - **7.0**: "Apache License, Jakarta Persistence 3.2, Java 17, QuerySpecification, mapping.xsd, Hibernate Models". Java 17/21/23. — [7.0 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/7.0/series.yml)
  - **8.0**: "Jakarta Persistence 4.0", Jakarta EE 12, Jakarta Data 1.1, Java 17/21/25/26. — [8.0 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/8.0/series.yml)
  - **6.6**: Java 11/17/21, with Java 25 from 6.6.40+ and Java 26 from 6.6.49+. Jakarta Persistence 3.1, EE 10, Spring Boot 3.4–3.5, LGPL 2.1. — [6.6 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/6.6/series.yml)
  - **6.2**: Java 11–21, Jakarta Persistence 3.1, Spring Boot 3.1. — [6.2 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/6.2/series.yml)
  - **5.6**: Java 8/11/17/18. JPA 2.2 (javax) in the main artifacts, and Jakarta Persistence 3.0 "through `*-jakarta` artifacts". — [5.6 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/5.6/series.yml)
  - **5.4**: Java 8/11, and 17 from 5.4.32+. JPA 2.2. — [5.4 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/5.4/series.yml)
  - **5.3**: JPA 2.2. — [5.3 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/5.3/series.yml)
  - **4.3**: Java 6/7, JPA 2.1, Java EE 7. — [4.3 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/4.3/series.yml)
- Java baselines from the migration guides:
  - 5.2 needs Java 8. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
  - 6.0 needs Java 11 and moves to `jakarta.persistence`. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
  - 7.0 needs Java 17 and Jakarta Persistence 3.2, and relicenses to Apache 2.0. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
  - Building Hibernate 7.2+ itself needs JDK 25 and Gradle 9, but the artifacts stay Java 17 compatible. — [7.2 MG](https://github.com/hibernate/hibernate-orm/blob/7.2/migration-guide.adoc)
- Support status, from a search summary because hibernate.org was unreachable here: the hibernate.org getting-started page listed "7.4.1.Final 2026-06-09" as the latest stable release and "6.6.53.Final 2026-06-09" as "limited-support". endoflife.date says limited-support series "only receive updates that are requested by Red Hat customers". endoflife.ai instead lists 6.6 as EOL on Jun 9 2026, which conflicts. — [hibernate.org getting started](https://hibernate.org/orm/documentation/getting-started/); [endoflife.date](https://endoflife.date/hibernate-orm); contradicted by [endoflife.ai](https://endoflife.ai/hibernate-orm)

### Inferences
- **Target 7.4.x**, the current stable line. Upgrading within 7.x later should be routine: the 7.1→7.4 guides are small. Consider 8.0 only after it goes GA and Spring 7.1 is released (see Q5).
- 6.6 is still being patched (6.6.58 is newer than the 6.6.53 seen in June 2026). Even so, plan for 6.6 only as a **transit stop** (the last javax-free 6.x before 7.0), not a resting place.
- 4.3 → 5.x → 6.x is the documented path, but the intermediate hops are only needed to the extent the Spring version forces them (Q5). Database-wise, the schema changes accumulate (Q3).

### Gaps
- I could not fetch hibernate.org/orm/releases, so I have no authoritative EOL dates or explicit support status for 7.0, 7.1, 7.3 or 6.6. Only 7.2 carries an explicit `limited-support` flag in the site data.
- I don't know the release date of 7.4.12.Final. Maven's lastUpdated timestamp covers the whole artifact.

---

## Q2. Per-hop breaking changes (4.3→5.x, 5.6→6.x, 6.6→7.x, plus the 8.0 preview) and the mitigation for each

### Takeaway
- **4.3 → 5.x** is mostly bootstrap, SPI and naming changes:
  - entitymanager merged into core
  - naming strategy split
  - new id-generator default
  - 5.3 changes to TableGenerator semantics, 2LC SPI and query-cache region names
  - Postgres LOB changes
- **5.6 → 6.0** is the big break:
  - `jakarta.*` namespace
  - type system rewrite (`@TypeDef` and string `@Type` gone, `UserType` redesigned)
  - SQM query strictness
  - legacy Criteria removed
  - hibernate-ehcache gone
  - changes to the default JDBC/DDL types for UUID, Instant, Duration, arrays and enums
  - sequence naming changes
  - `hibernate.ejb.*` settings removed
- **6.x minors** each add DDL/type default changes, each with a "legacy" switch.
- **7.0** removes the remaining deprecated Hibernate-native APIs and annotations: `Session.save/update/saveOrUpdate/delete/load`, `@Where`, `@Index`, `@IndexColumn`, `@Proxy`, `@LazyToOne`, `@Loader` and others. It also adds strict annotation validation and some Oracle/SQL Server DDL changes.
- **7.1–7.4** are smaller.
- **8.0** (beta) is JPA 4.0.

### Cited Findings

#### uPortal's current Hibernate surface (local checkout, grounding)
- **Configuration and bootstrap:**
  - `persistence.xml` uses `hibernate.ejb.cfgfile` to point each persistence unit at `hibernate.cfg.xml`, `hibernate-raw-events.cfg.xml` and `hibernate-aggr-events.cfg.xml`, with `hibernate.archive.autodetection=""`. — [persistence.xml](file://uPortal/uPortal-webapp/src/main/resources/META-INF/persistence.xml)
  - The cfg files set `hibernate.id.new_generator_mappings=true`, `max_fetch_depth=3`, `default_batch_fetch_size=4`, `jdbc.batch_size=25`, the query cache and 2LC. They name the SessionFactory `PortalDb` with `session_factory_name_is_jndi=false`, and include `properties/db/global.hbm.xml`. — [hibernate.cfg.xml](file://uPortal/uPortal-webapp/src/main/resources/properties/db/hibernate.cfg.xml)
  - Spring uses `LocalContainerEntityManagerFactoryBean` (a subclass) with `HibernateJpaVendorAdapter` (`generateDdl=false`) and sets `hibernate.cache.region.factory_class=org.hibernate.cache.ehcache.SpringBeanEhCacheRegionFactory`. — [persistenceContext.xml](file://uPortal/uPortal-webapp/src/main/resources/properties/contexts/persistenceContext.xml)
- **`global.hbm.xml` typedefs:**
  - Six custom UserTypes: `nullSafeString`, `fname`, `windowState`, `qname`, `json` (Jackson) and `stats_json`.
  - Jadira Joda types: `dateTime` = `PersistentDateTime`, `dateTimeTZ`, `localDate` with `databaseZone=jvm`, `localTime`, and `monthDay` = `PersistentMonthDayAsString`.
  - — [global.hbm.xml](file://uPortal/uPortal-webapp/src/main/resources/properties/db/global.hbm.xml)
- **Annotation usage counts** (source grep):
  - `@Type(type="…")`: dateTime ×11, fname ×7, nullSafeString ×6, monthDay ×2, class ×2, `org.hibernate.type.StringType` ×2, localDate ×1, localTime ×1.
  - Hibernate annotations imported: `@Cache` ×52, `@NaturalId` ×28, `@NaturalIdCache` ×24, `@Fetch` ×19, `org.hibernate.annotations.Index` ×18, `@Immutable` ×5, `@IndexColumn` ×4.
  - 9 UserType implementation classes.
  - **No `org.hibernate.criterion` (legacy Criteria) usage.**
  - — [uPortal source tree](file:///home/user/uPortal)
- **Hibernate internals that uPortal overrides or uses, which will break:**
  - `org.hibernate.cache.ehcache.SpringBeanEhCacheRegionFactory` and `org.hibernate.tool.hbm2ddl.FixedDatabaseMetadata`, both placed in Hibernate's own packages. — [uPortal-hibernate](file://uPortal/uPortal-hibernate/src/main/java)
  - Custom dialects `PostgreSQL81Dialect`, `MySQL5InnoDBCompressedDialect` (overrides `getSqlTypeDescriptorOverride`) and `Oracle12ForceClobDialect` (`registerColumnType(Types.VARCHAR, "clob")`). — [uPortal-hibernate4-dialects](file://uPortal/uPortal-hibernate/uPortal-hibernate4-dialects)
  - `PortalDialectResolver`. — [PortalDialectResolver.java](file://uPortal/uPortal-utils/uPortal-utils-core/src/main/java/org/apereo/portal/utils/PortalDialectResolver.java)
  - Use of `org.hibernate.id.enhanced.Optimizer`/`OptimizerFactory`/`AccessCallback`. — [HibernateStyleCounterStore.java](file://uPortal/uPortal-rdbm/src/main/java/org/apereo/portal/utils/HibernateStyleCounterStore.java)
  - `SchemaExport`/hbm2ddl tooling, `org.hibernate.cfg.Configuration` and `org.hibernate.metamodel.source.MetadataImplementor` (4.3-only internal) in the dbloader. — [uPortal-tools dbloader](file://uPortal/uPortal-tools/src/main/java/org/apereo/portal/tools/dbloader)

#### 4.3 → 5.0
- **Bootstrap and transactions:**
  - `Configuration` was repurposed and many methods removed. Bootstrapping is now via `MetadataSources`/`StandardServiceRegistry`. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - The transaction SPI was redesigned: `hibernate.transaction.factory_class` became `hibernate.transaction.coordinator_class` (`jdbc`/`jta`). JPA bootstrap picks the coordinator from the persistence-unit transaction type. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
- **Naming strategy split:**
  - `NamingStrategy` was replaced by `ImplicitNamingStrategy` plus `PhysicalNamingStrategy`.
  - The **default implicit strategy changed to JPA-compliant**. If implicit names break, the guide suggests `legacy-hbm` (`ImplicitNamingStrategyLegacyHbmImpl`). — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - Short names: `default`/`jpa`, `legacy-hbm` (original Hibernate NamingStrategy), `legacy-jpa` (the JPA 1.0-era strategy) and `component-path`. — [5.6 User Guide naming.adoc](https://github.com/hibernate/hibernate-orm/blob/5.6/documentation/src/main/asciidoc/userguide/chapters/domain/naming.adoc)
  - `ImplicitNamingStrategyLegacyJpaImpl` "Corresponds roughly to the legacy org.hibernate.cfg.EJB3NamingStrategy class". — [ImplicitNamingStrategyLegacyJpaImpl (5.6)](https://github.com/hibernate/hibernate-orm/blob/5.6/hibernate-core/src/main/java/org/hibernate/boot/model/naming/ImplicitNamingStrategyLegacyJpaImpl.java)
- **ID generation:**
  - The `hibernate.id.new_generator_mappings` default flipped to `true`. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - The old `org.hibernate.id.TableGenerator` and `TableHiLoGenerator` were removed, and `org.hibernate.id.SequenceGenerator` was deprecated. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - UUID ids may need `@Column(length=16)` to get BINARY(16). — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
- **Other changes:**
  - `hibernate.jdbc.batch_versioned_data` now defaults to `true`, except on Oracle dialects (other than 12c). — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - `hibernate.cache.default_cache_concurrency_strategy` values now use `AccessType#getExternalName`. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - Since 5.0.3, enum `AttributeConverter` queries must use the enum value, not the converted literal. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc)
  - **Oracle12cDialect maps `byte[]` to BLOB instead of LONG RAW** (5.0.8 / 5.1). Keep the old type with `hibernate.dialect.oracle.prefer_long_raw` (5.1 name; the 5.0 guide calls it `prefer_longvarbinary`). — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc); [5.1 MG](https://github.com/hibernate/hibernate-orm/blob/5.1/migration-guide.adoc)

#### 5.1
- Schema tooling was unified with JPA schema generation. This affects direct users of `SchemaExport`, `SchemaUpdate`, `SchemaValidator` and `SchemaManagementTool`. — [5.1 MG](https://github.com/hibernate/hibernate-orm/blob/5.1/migration-guide.adoc)
- `hibernate.hbm2ddl.jdbc_metadata_extraction_strategy=grouped` became the default and "may require `hibernate.default_schema` and/or `hibernate.default_catalog`". `individually` restores the old behavior. — [5.1 MG](https://github.com/hibernate/hibernate-orm/blob/5.1/migration-guide.adoc)
- A bug since 4.3 made `LAZY` many-to-one associations in embeddables and composite IDs load eagerly. It was fixed in 5.1.15, 5.2.18 and 5.3.2, so those associations are now lazy. — [5.1 MG](https://github.com/hibernate/hibernate-orm/blob/5.1/migration-guide.adoc)

#### 5.2
- Requires Java 8. The `hibernate-java8` module was merged, so java.time is supported natively. **`hibernate-entitymanager` was merged into `hibernate-core`**: `Session` extends `EntityManager`, `SessionFactory` extends `EntityManagerFactory`, and `org.hibernate.Query` is deprecated in favor of `org.hibernate.query.Query`. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
- `Transaction.begin()` now throws if a transaction is already active.
- Flushing outside a transaction is no longer allowed. `hibernate.allow_update_outside_transaction=true` restores it.
- `getFlushMode()` now returns the JPA `FlushModeType`. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
- **Avoid 5.2.9/5.2.10 on PostgreSQL.** Those releases changed how `@Lob` String/Clob was stored (varchar instead of large objects), and 5.2.11 reverted it, leaving the data unreadable in either direction. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
- Generator-name scope:
  - 5.2.13 made id generator names global.
  - 5.2.17+ made them local again by default.
  - `hibernate.jpa.compliance.global_id_generators` enables global scope. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
- `hibernate.legacy_limit_handler` exists for dialects without offset support, including plain `SQLServerDialect` but not `SQLServer2005Dialect`. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)

#### 5.3 (JPA 2.2)
- JDBC-style `?` parameters were removed from HQL. Native-query positional binding became 1-based; `hibernate.query.sql.jdbc_style_params_base=true` temporarily reverts. — [5.3 MG](https://github.com/hibernate/hibernate-orm/blob/5.3/migration-guide.adoc)
- **`@TableGenerator` now stores the *last* generated value instead of the next one.** "Existing applications migrating to 5.3 and using the `@TableGenerator` have to set the `hibernate.id.generator.stored_last_used` configuration property to `false`." — [5.3 MG](https://github.com/hibernate/hibernate-orm/blob/5.3/migration-guide.adoc)
  - The 5.3 source defaults it to `true` and applies it to both row initialization and the returned value. — [TableGenerator 5.3](https://github.com/hibernate/hibernate-orm/blob/5.3/hibernate-core/src/main/java/org/hibernate/id/enhanced/TableGenerator.java)
- 2LC provider SPI redesign (HHH-11356):
  - `QueryCacheFactory` became `TimestampsCacheFactory`.
  - `SecondLevelCacheStatistics#getEntries` now returns an empty map.
  - `hibernate-infinispan` was dropped; use `org.infinispan:infinispan-hibernate-cache`.
  - `JpaIntegrator` was removed. — [5.3 MG](https://github.com/hibernate/hibernate-orm/blob/5.3/migration-guide.adoc)
- **Query-cache region names changed:**
  - 4.3 used `org.hibernate.cache.spi.UpdateTimestampsCache` (`REGION_NAME = UpdateTimestampsCache.class.getName()`) and `org.hibernate.cache.internal.StandardQueryCache`. — [4.3 UpdateTimestampsCache](https://github.com/hibernate/hibernate-orm/blob/4.3/hibernate-core/src/main/java/org/hibernate/cache/spi/UpdateTimestampsCache.java)
  - 5.3+ uses `default-query-results-region` and `default-update-timestamps-region`. — [5.3 RegionFactory](https://github.com/hibernate/hibernate-orm/blob/5.3/hibernate-core/src/main/java/org/hibernate/cache/spi/RegionFactory.java)
  - uPortal's `ehcache.xml` still configures the 4.3 names, plus `…ClusterMutex-NaturalId`, across 184 `<cache>` entries. — [ehcache.xml](file://uPortal/uPortal-webapp/src/main/resources/properties/ehcache.xml)
- An entity's `@Where` clause now also applies when loading collections of that entity; `hibernate.use_entity_where_clause_for_collections=false` reverts. — [5.3 MG](https://github.com/hibernate/hibernate-orm/blob/5.3/migration-guide.adoc)

#### 5.4 / 5.5 / 5.6
- **5.4:**
  - **`hibernate.id.sequence.increment_size_mismatch_strategy`** was introduced ("@since 5.4"). Values are `EXCEPTION` (default), `LOG`, `FIX` and `NONE`, applied when the DB sequence increment ≠ the mapping's allocationSize. — [AvailableSettings 5.6](https://github.com/hibernate/hibernate-orm/blob/5.6/hibernate-core/src/main/java/org/hibernate/cfg/AvailableSettings.java)
  - The SQL Server JDBC driver must be ≥ 6.1.2.
  - `hibernate.id.disable_delayed_identity_inserts` is available as a temporary escape hatch. — [5.4 MG](https://github.com/hibernate/hibernate-orm/blob/5.4/migration-guide.adoc)
- **5.5** adds Jakarta Persistence (via `*-jakarta` artifacts), reimplements hbm.xml parsing on JAXB, and makes enhanced proxies always on. — [5.5 MG](https://github.com/hibernate/hibernate-orm/blob/5.5/migration-guide.adoc)
- **5.6:**
  - **Javassist removed.** `hibernate.bytecode.provider=javassist` is now invalid; ByteBuddy is the only option. — [5.6 MG](https://github.com/hibernate/hibernate-orm/blob/5.6/migration-guide.adoc)
  - **5.6.2 changed the PostgreSQL CLOB DDL from `text` to `oid`.** The guide warns that `text` columns holding LOB OIDs can **lose data to `vacuumlo`**, recommends `alter … type oid using clobfield::oid`, and suggests `@Type(type="text")` for plain large strings. — [5.6 MG](https://github.com/hibernate/hibernate-orm/blob/5.6/migration-guide.adoc)

#### 5.6 → 6.0 (Java 11, Jakarta)
- **Namespace and tooling:**
  - Packages, settings and orm.xml/persistence.xml XSDs move from `javax.persistence` to `jakarta.persistence`.
  - The guide recommends the Eclipse Transformer.
  - Hibernate still accepts the javax settings and XSDs "as a temporary aid", with a deprecation warning. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Sequence naming:**
  - **Implicit sequences are now one per entity hierarchy, named `<entity name>_seq`**, instead of a single `hibernate_sequence`.
  - Implicit sequences now use allocationSize **50**.
  - **`hibernate.id.db_structure_naming_strategy=single` or `legacy`** keeps the old names.
  - `import.sql` sequences must be reset (`alter sequence … restart with max+1+allocation`). — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
  - The default is `StandardNamingStrategy`, and the short names are `single`, `legacy` and `standard`. — [MappingSettings 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/cfg/MappingSettings.java)
- **`hibernate.id.new_generator_mappings` no longer exists.** `USE_NEW_ID_GENERATOR_MAPPINGS` is present in 5.6 `AvailableSettings` but absent in 6.0 and 6.6. — [AvailableSettings 6.0](https://github.com/hibernate/hibernate-orm/blob/6.0/hibernate-core/src/main/java/org/hibernate/cfg/AvailableSettings.java)
- **Type system rewrite:**
  - `@TypeDef`, `@TypeDefs`, `@AnyMetaDef` and String-based type references were removed, and so were the `BasicType` implementation classes (`StringType` etc.). UserTypes are now referenced by class via the new `@Type`.
  - `JavaTypeDescriptor` became `JavaType`, and `SqlTypeDescriptor` became `JdbcType`.
  - The `yes_no`/`true_false`/`numeric_boolean` types became `YesNoConverter`, `TrueFalseConverter` and `NumericBooleanConverter`.
  - `CompositeUserType` was redesigned around an "embeddable projection", and `@Columns` gave way to `@AttributeOverride`.
  - `@CollectionType(type=String)` became `Class<? extends UserCollectionType>`. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Default JDBC type changes:**
  - `BIGINT`/`count()` in native queries now returns `Long`, not `BigInteger`.
  - **Duration** maps to `INTERVAL_SECOND`/`numeric(21)`; revert with `hibernate.type.preferred_duration_jdbc_type=BIGINT`.
  - **UUID** maps to native `uuid`/`binary(16)`; revert with `hibernate.type.preferred_uuid_jdbc_type=BINARY`.
  - **Instant** maps to `TIMESTAMP_UTC` (`timestamp with time zone`); revert with `hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **SQM query changes:**
  - A query with joins but no select (`from A a join a.b`) now returns `List<A>`.
  - `hibernate.hql.bulk_id_strategy` became `hibernate.query.mutation_strategy`.
  - Criteria queries are no longer copied unless `hibernate.criteria.copy_tree` is enabled.
  - Pass-through of unknown identifiers was removed; use `sql('…')`.
  - `update from` is disallowed.
  - DISTINCT deduplication is now automatic and `HINT_PASS_DISTINCT_THROUGH` was removed.
  - **Comparing an association to an FK value is disallowed**: write `a.assoc.id = 1`, not `a.assoc = 1`. Comparing paths of different types is also disallowed.
  - The collection pseudo-attributes `.size`, `.elements` etc. were removed; use the `size()`/`value()`/`index()` functions.
  - HQL ordinal parameters are 1-based.
  - `Query#iterate()` was removed.
  - Callable `NativeQuery` was removed; use `ProcedureCall`/`StoredProcedureQuery`.
  - `fetch all properties` was removed.
  - Streams must be closed explicitly. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Runtime behavior:**
  - **The query cache stores full entity state** (the layout is configurable from 6.5).
  - `Interceptor#onSave` takes an `Object` id.
  - Fetch-circularity detection is now breadth-first, which generates more joins; the guide recommends `LAZY`.
  - **`@Fetch(FetchMode.JOIN)` on LAZY associations is now really lazy on find/get**; set `fetch=EAGER` to keep the old behavior.
  - hbm.xml is deprecated.
  - **The legacy Criteria API was removed.**
  - JMX/JACC integration was removed. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Removed setting names:**
  - `hibernate.ejb.cfgfile` → `hibernate.cfg_xml_file`
  - `hibernate.ejb.xml_files` → `hibernate.orm_xml_files`
  - `hibernate.hbmxml.files` → `hibernate.hbm_xml_files`
  - `hibernate.ejb.persistenceUnitName` → `hibernate.persistenceUnitName`
  - the `hibernate.ejb.classcache.`/`collectioncache.`/`event.` prefixes → `hibernate.classcache.` etc.
  - `hibernate.multiTenancy` was removed. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Dialects:**
  - Community-maintained dialects moved to `org.hibernate.orm:hibernate-community-dialects`.
  - Version-specific dialects are deprecated; dialects now detect the DB version (use `PostgreSQLDialect`, `MySQLDialect`, `MariaDBDialect`, `OracleDialect`, `SQLServerDialect`, `HSQLDialect`).
  - Oracle `NUMBER(n,0)` is now inferred as INTEGER/BIGINT. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- `@Where` now applies to *all* interactions, including bulk DML. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Second-level cache:**
  - The 5.6 guide says the `hibernate-ehcache` integration "covers Ehcache 2.x"; Ehcache 3 goes through JCache. — [5.6 Caching.adoc](https://github.com/hibernate/hibernate-orm/blob/5.6/documentation/src/main/asciidoc/userguide/chapters/caching/Caching.adoc)
  - The 6.6 guide lists only JCache (`hibernate-jcache`) and Infinispan as built-in providers. — [6.6 Caching.adoc](https://github.com/hibernate/hibernate-orm/blob/6.6/documentation/src/main/asciidoc/userguide/chapters/caching/Caching.adoc)
  - `org.hibernate:hibernate-ehcache` never got past `6.0.0.Alpha7` on Maven Central. — [Maven Central hibernate-ehcache](https://repo1.maven.org/maven2/org/hibernate/hibernate-ehcache/maven-metadata.xml)
  - JCache logs a warning per unconfigured region by default (`hibernate.javax.cache.missing_cache_strategy` = `create-warn`; alternatives `fail` and `create`). Auto-created caches may be unbounded. — [6.6 Caching.adoc](https://github.com/hibernate/hibernate-orm/blob/6.6/documentation/src/main/asciidoc/userguide/chapters/caching/Caching.adoc)

#### 6.1 – 6.6
- **6.1:**
  - Basic arrays and collections map to SQL `ARRAY`, falling back to JSON/XML, instead of serialized VARBINARY. Revert with `@JdbcTypeCode(SqlTypes.VARBINARY)`.
  - Ordinal enums default to `SMALLINT` (was `TINYINT`). — [6.1 MG](https://github.com/hibernate/hibernate-orm/blob/6.1/migration-guide.adoc)
  - `hibernate.transform_hbm_xml.enabled` (opt-in, since 6.1) transforms hbm.xml into mapping.xml. — [MappingSettings 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/cfg/MappingSettings.java)
- **6.2 type defaults:**
  - **`hibernate.timezone.default_storage` defaults to `DEFAULT`**, i.e. native `timestamp with time zone` where supported. `NORMALIZE` gives the 5.x behavior; the guide notes 5.x normalized to `hibernate.jdbc.time_zone`.
  - UUID maps to `uuid` on MariaDB and to `uniqueidentifier` on SQL Server; revert with `preferred_uuid_jdbc_type=BINARY`.
  - Oracle JSON maps to `blob`/`json`.
  - Enum ordinals use TINYINT unless the enum has more than 128 entries, and **STRING enums use the MySQL `ENUM` type**.
  - `Byte[]`/`Character[]` are disallowed by default; `hibernate.type.wrapper_array_handling=legacy` reverts.
  - DDL now has check constraints for boolean and enum, and UNIQUE on optional logical one-to-ones. — [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc)
- **6.2 minimum DB versions:**
  - MySQL 5.7, SQL Server 10.0, MariaDB 10.3, PostgreSQL 10.0, Oracle 11.2, HSQLDB 2.6.1, H2 1.4.197.
  - Older version-specific dialects (e.g. `PostgreSQL81Dialect`, `MariaDB102Dialect`) moved to `org.hibernate.community.dialect` in hibernate-community-dialects. — [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc)
- **6.2 other changes:**
  - Comparing a temporal attribute to a string literal now fails; use `date 2020-01-01`.
  - `select *` with joins needs an alias in native entity queries.
  - `hibernate.cdi.extensions` was added (default false).
  - The enhancement defaults changed.
  - Most of `org.hibernate.cfg` internals moved to `org.hibernate.boot`. — [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc)
- **6.3:**
  - Numeric literal typing follows JPA 3.2.
  - **`= null` / `<> null` comparisons no longer have special handling ("without a way to retain backwards compatibility")**; use `is null`.
  - `hibernate-jpamodelgen` must be on the annotation-processor path, which needs maven-compiler-plugin ≥ 3.12.0 for BOM-managed versions. — [6.3 MG](https://github.com/hibernate/hibernate-orm/blob/6.3/migration-guide.adoc)
- **6.4:** `@GeneratedValue` on a non-id field now throws `AnnotationException`. `@SoftDelete` was added. — [6.4 MG](https://github.com/hibernate/hibernate-orm/blob/6.4/migration-guide.adoc)
- **6.5:**
  - `hibernate.type.java_time_use_direct_jdbc` was added (opt-in).
  - `hibernate.cache.query_cache_layout` was added: `SHALLOW` gives the 5.x layout, `FULL` the 6.0 layout, and `AUTO` is the default.
  - H2 STRING enums map to `enum`.
  - `hibernate.boot.allow_jdbc_metadata_access` was added.
  - `@GenericGenerator` was deprecated.
  - Query result type checks are stricter (`TypeMismatchException`).
  - **Unique keys are now named via `ImplicitNamingStrategy`.**
  - Annotation problems are now errors instead of warnings.
  - The annotation processor was renamed to `org.hibernate.processor.HibernateProcessor`.
  - Bytecode-enhanced classes must be re-enhanced. — [6.5 MG](https://github.com/hibernate/hibernate-orm/blob/6.5/migration-guide.adoc)
- **6.6:**
  - Merging a versioned detached entity whose row was deleted now throws `OptimisticLockException` instead of re-inserting.
  - A class may no longer be both `@MappedSuperclass` and `@Embeddable`.
  - `Expression#as()` no longer converts; use `JpaExpression#cast`.
  - **`@Table` on a SINGLE_TABLE subclass is now a mapping error.**
  - Oracle implicit array type names changed. — [6.6 MG](https://github.com/hibernate/hibernate-orm/blob/6.6/migration-guide.adoc)

#### 6.6 → 7.0 (Java 17, Jakarta Persistence 3.2)
- **License and requirements:** Apache 2.0; `hibernate-ucp` and `TeradataDialect` dropped; Java 17; JPA 3.2 generics affect Criteria and EntityGraph; Hibernate Models replaces HCANN. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **Session API removals:**
  - **`Session#load` removed** → `getReference`.
  - `Session#get` deprecated → `find`.
  - **`save`/`update`/`saveOrUpdate` removed** → `persist`/`merge`.
  - **`delete` removed** → `remove`.
  - `CascadeType.SAVE_UPDATE`/`DELETE` removed → `PERSIST`/`MERGE`/`REMOVE`.
  - `refresh(String, Object)` removed. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **Removed annotations:**
  - `@Persister`, `@Proxy`, `@SelectBeforeUpdate`, `@Loader`, Hibernate `@Table`, `@GeneratorType`, `@LazyToOne`, `@LazyCollection`
  - `@Where`/`@WhereJoinTable` → `@SQLRestriction`/`@SQLJoinTableRestriction`
  - Hibernate `@OrderBy` → `@SQLOrder`
  - **Hibernate `@ForeignKey`/`@Index` → JPA `@ForeignKey`/`@Index`**
  - **`@IndexColumn` → JPA `@OrderColumn`**
  - `@Cache#include` → `includeLazy`
  - `@Target` → `@TargetEmbeddable`
  - `@DynamicInsert/@DynamicUpdate#value` removed. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **API removals:**
  - `org.hibernate.Metamodel`, `Session.LockRequest` and `EmptyInterceptor` were removed. Implement `Interceptor` directly; its deprecated `Serializable`-id methods are gone.
  - The old `Integrator#integrate(Metadata, SessionFactoryImplementor, SessionFactoryServiceRegistry)` overload was removed.
  - **The `UserType#nullSafeGet`/`nullSafeSet` signatures changed** (via deprecation).
  - `LockOptions` was deprecated.
  - Several dialect-specific `JdbcType`s became internal; use `@JdbcTypeCode`. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **Stricter validation:**
  - Multiple attribute-type annotations on one attribute are rejected.
  - **Misplaced annotations are now errors**, e.g. a property-level `@Column` under field access.
  - **`GenerationType.SEQUENCE` is only valid with `@SequenceGenerator`, and `TABLE` only with `@TableGenerator`.**
  - Bad JavaBean names such as `String isDefault()` are rejected.
  - Converters on `@Id`/`@Version`/`@Enumerated`/`@Temporal`/associations are errors. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **Behavior changes:**
  - `StatelessSession` uses the 2LC by default, and `hibernate.jdbc.batch_size` no longer applies to it.
  - Ambiguous `createQuery("from X join y")` without a result class is rejected.
  - **persist/flush throws `EntityExistsException` when a CascadeType.PERSIST/ALL association references a *detached* instance.**
  - Refreshing or locking detached entities is disallowed (`hibernate.allow_refresh_detached_entity` removed).
  - There is no implicit `cascade=PERSIST` for `@Id`/`@MapsId` associations.
  - **Native queries return `java.time` types**; `hibernate.query.native.prefer_jdbc_datetime_types=true` reverts.
  - Criteria implicit treat was removed.
  - **The SessionFactory now gets its name from the persistence-unit name** and may try to bind it into JNDI; use `hibernate.session_factory_jndi_name` / `session_factory_name_is_jndi`.
  - **Classpath entity discovery now needs `hibernate-scan-jandex`.**
  - Unowned `@OrderColumn` no longer issues UPDATEs.
  - **Bulk update/delete of `@Immutable` entities now throws**; `hibernate.query.immutable_entity_update_query_handling_mode=allow` reverts. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **DDL changes in 7.0:**
  - Oracle timestamp precision becomes 9 and SQL Server 7.
  - **Oracle `float`/`double` map to `binary_float`/`binary_double`**; `hibernate.dialect.oracle.use_binary_floats=false` reverts.
  - Arrays map to XML on SQL Server and JSON on MySQL/MariaDB; `hibernate.type.preferred_array_jdbc_type=VARBINARY` reverts.
  - `char` maps to `varchar(1)`. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **Settings and tooling:** `hibernate.mapping.precedence` was removed; the Vibur/Proxool/UCP pools were dropped; the Maven enhance plugin is now `org.hibernate.orm:hibernate-maven-plugin`. — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)

#### 7.1 – 7.4
- **7.1:**
  - The `LockOptions` constants and per-alias lock modes were removed; use `FindOption`/`LockOption`/`RefreshOption`.
  - Bidirectional and "extended" enhancement are deprecated.
  - `Session#getLobHelper` is deprecated.
  - DDL now has check constraints for non-null single-table subclass columns.
  - The Maven plugin defaults were fixed in 7.1.5. — [7.1 MG](https://github.com/hibernate/hibernate-orm/blob/7.1/migration-guide.adoc)
- **7.2:**
  - `Session.contains(String,Object)` and `byMultipleIds()` are deprecated.
  - `AzureSQLServerDialect` is deprecated.
  - Child sessions now flush and close with their parent.
  - hibernate-testing moves to JUnit 6.
  - With `hibernate.boot.allow_jdbc_metadata_access=false`, set `jakarta.persistence.database-product-version` (SQL Server 2022 = 16.0). — [7.2 MG](https://github.com/hibernate/hibernate-orm/blob/7.2/migration-guide.adoc)
- **7.3:**
  - `@Columns` was removed.
  - Classmate was removed.
  - Collections of read-only entities are now read-only.
  - **`getSingleResult()` always throws on more than one row**; use `ResultListTransformer.uniqueResultTransformer()`.
  - Query and lock timeouts that cause a rollback now throw `PersistenceException`.
  - **Schema actions, including `import.sql`, now run even with no entities.**
  - `@Version` columns are `NOT NULL` in DDL. — [7.3 MG](https://github.com/hibernate/hibernate-orm/blob/7.3/migration-guide.adoc)
- **7.4:**
  - Oracle `current date` becomes `trunc(current_date)`.
  - **MySQLDialect no longer defaults `hibernate.max_fetch_depth` to 2.**
  - Eager `@Any` associations are join-fetched.
  - **A limit combined with a collection fetch is now applied in SQL**; the `org.hibernate.limitInMemory` hint reverts.
  - JSON function encoding changed in 7.4.4.
  - DDL: UNIQUE on `@ElementCollection` sets, NOT NULL on `@CreationTimestamp`/`@UpdateTimestamp`.
  - **Oracle 23+ value-based LOB DDL** (`lob(col) query as value`) fails with ORA-43853 in MSSM tablespaces such as SYSTEM, and some 23c builds hit ORA-00600 with SKIP LOCKED. `hibernate.dialect.oracle.value_lob_enabled=false` disables it.
  - Optional migration from Envers to core `@Audited`. — [7.4 MG](https://github.com/hibernate/hibernate-orm/blob/7.4/migration-guide.adoc)

#### 8.0 (beta, Jakarta Persistence 4.0) — preview only
- **Validation and query API:**
  - Bean Validation groups are re-split per lifecycle event; **`pre-persist` validation is now empty by default**, so use `pre-insert`.
  - The Query API is split into `TypedQuery` and `Statement`, and `@NamedQuery` can no longer define mutations (use `@NamedStatement`).
  - `Session.replicate()` was removed.
  - **The whole `org.hibernate.transform` package (`Transformers`, `AliasToBean…`) was removed.**
  - `StatelessSession.insert()` returns `void`.
  - `Session.get` semantics changed under JPA 4. — [8.0 MG](https://github.com/hibernate/hibernate-orm/blob/8.0/migration-guide.adoc)
- **Defaults:**
  - `hibernate.type.java_time_use_direct_jdbc` now defaults to `true`.
  - **Bean Validation constraints now influence DDL by default** (`hibernate.tooling.schema.apply_validation_constraints`).
  - `@NativeGenerator` defaults to allocation size 50. — [8.0 MG](https://github.com/hibernate/hibernate-orm/blob/8.0/migration-guide.adoc)
- **Artifacts and mapping:**
  - `@GenericGenerator` is un-deprecated but reworked.
  - **Relocation POMs under `org.hibernate` are no longer published**, so you must use `org.hibernate.orm`.
  - OSGi manifest headers were removed.
  - `cascade="lock"` was removed.
  - The legacy hbm.xml binder still exists in 8.0. The `ModelBinder` and `HbmXmlTransformer` sources are present on the 6.6, 7.0, 7.4 and 8.0 branches. — [8.0 MG](https://github.com/hibernate/hibernate-orm/blob/8.0/migration-guide.adoc); [hbm ModelBinder 8.0](https://github.com/hibernate/hibernate-orm/blob/8.0/hibernate-core/src/main/java/org/hibernate/boot/model/source/internal/hbm/ModelBinder.java)

### Inferences
- **uPortal-specific work list (all mechanical but mandatory):**
  1. Replace `hibernate.ejb.cfgfile` with `hibernate.cfg_xml_file` (6.0), or move the cfg.xml contents into `persistence.xml`/Spring `jpaProperties`.
  2. Rewrite the 9 UserTypes against the 6.x `UserType<J>` API and again for 7.0's `nullSafeGet`/`nullSafeSet` signature. Where possible, turn them into `AttributeConverter`s, which are API-stable.
  3. Delete `global.hbm.xml` typedefs (or keep hbm.xml only temporarily) and change each `@Type(type="fname")` to `@Type(FunctionalNameType.class)` or `@Convert`.
  4. Replace Jadira (no Hibernate 6 support exists, see Q6) with either `java.time` fields or Joda↔java.sql `AttributeConverter`s.
  5. `@Type(type="org.hibernate.type.StringType")` and `@Type(type="class")` must go; Class is natively supported in 6+.
  6. Convert the 18 Hibernate `@Index` usages to JPA `@Table(indexes=…)` **with the same explicit names**.
  7. Convert the 4 `@IndexColumn(name=…)` to `@OrderColumn(name=…)`. Hibernate `@IndexColumn` defaults to base 0, the same as JPA; if any used `base=1`, add `@ListIndexBase(1)`.
  8. Delete the `org.hibernate.*`-package overrides (`SpringBeanEhCacheRegionFactory`, `FixedDatabaseMetadata`).
  9. Rewrite the custom dialects against the 6.x `DdlTypeRegistry`/`JdbcTypeRegistry` APIs. The 7.4 `OracleDialect` uses `ddlTypeRegistry.addDescriptor(new DdlTypeImpl(…))` and `jdbcTypeRegistry.addDescriptor(…)`, and `registerColumnType` is gone. — basis: [OracleDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/OracleDialect.java)
  10. Port the dbloader `SchemaExport` code to `MetadataSources` (5.x) and then to `SchemaManager` or JPA schema generation (6.2+/7.0).
  11. `@Immutable` entities hit by bulk HQL updates will throw in 7.0.
  12. `max_fetch_depth=3` is already explicit, so the 7.4 MySQL change does not matter.
- **`@Fetch(FetchMode.JOIN)` on associations declared LAZY** (19 imports) changes behavior in 6.0, from eager-on-find to lazy. Audit for `LazyInitializationException`s, especially outside OpenEntityManagerInView.
- The 7.0 detached-reference `EntityExistsException` and the 7.3 strict `getSingleResult()` are the two runtime changes most likely to surface only in integration tests. Plan test coverage for both.

### Gaps
- I did not check whether `OptimizerFactory`, `AccessCallback` and `IntegralDataTypeHolder` (used by `HibernateStyleCounterStore`) still exist with compatible signatures in 6.6/7.4.
- I did not verify how 6.x/7.x handle hbm.xml `<typedef>` in the legacy binder versus the transformer. Moving typedefs to annotations sidesteps the question.

---

## Q3. Which hops carry real DATA-compatibility risk for an in-place upgrade, how to stay compatible, and how to verify schema equality

### Takeaway
The highest-risk area for uPortal is **ID generation on SQL Server and MariaDB**:
- uPortal's 4.3 setup made Hibernate store sequence values in *tables* named `UP_*_SEQ` on those databases, because 4.3 used dialects without sequence support there.
- Hibernate 6/7 dialects for those databases always use native sequences.
- So without a dialect override or a one-time conversion, inserts will fail, or worse, restart numbering.

Other real data risks:
- PostgreSQL `@Lob` handling (OIDs stored in `text` columns, `vacuumlo`), including one uPortal mapping whose `@Type(StringType)` override disappears in 6.0
- timezone storage for `OffsetDateTime`/`ZonedDateTime` (6.2)
- Joda/Jadira column semantics
- Oracle 23 booleans

Most other 6.x/7.x DDL default changes (enum, UUID, Instant, Duration, arrays, Oracle floats, check and unique constraints) only affect *newly generated* DDL and schema validation. Each has a documented legacy switch. Verify with Hibernate schema validation against a production-copy database for each target DB, plus a DDL diff between a fresh 4.3 install and a fresh new-version install.

### Cited Findings
**A. Identifier generation (highest risk)**
- **uPortal mapping pattern:**
  - `@GeneratedValue(generator="UP_X_GEN")` with no strategy (AUTO), plus **both** `@SequenceGenerator(name="UP_X_GEN", sequenceName="UP_X_SEQ", allocationSize=N)` **and** `@TableGenerator(name="UP_X_GEN", pkColumnValue="UP_X_PROP", allocationSize=N)` on the same class.
  - 49 files have each annotation.
  - allocationSize values include 1, 5, 10, 100, 1000, 5000 and 10000.
  - — [UniqueStrings.java example](file://uPortal/uPortal-events/src/main/java/org/apereo/portal/events/aggr/UniqueStrings.java)
- **The sequence definition wins in 4.3 and 5.6.**
  - In 4.3 `AnnotationBinder.buildLocalGenerators`, the table generator is `put` into the name→generator map first and the sequence generator second, so the sequence overwrites it. — [AnnotationBinder 4.3](https://github.com/hibernate/hibernate-orm/blob/4.3/hibernate-core/src/main/java/org/hibernate/cfg/AnnotationBinder.java)
  - 5.6 `buildGenerators` uses the same order. — [AnnotationBinder 5.6](https://github.com/hibernate/hibernate-orm/blob/5.6/hibernate-core/src/main/java/org/hibernate/cfg/AnnotationBinder.java)
  - 6.6 `GeneratorBinder` uses the same order. — [GeneratorBinder 6.6](https://github.com/hibernate/hibernate-orm/blob/6.6/hibernate-core/src/main/java/org/hibernate/boot/model/internal/GeneratorBinder.java)
  - 7.4 `visitIdGeneratorDefinitions` visits TableGenerator, then SequenceGenerator, then GenericGenerator, and resolves an explicitly named generator by name lookup. — [GeneratorBinder 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/boot/model/internal/GeneratorBinder.java)
- **The 5.0 default flip does not affect uPortal.** uPortal already sets `hibernate.id.new_generator_mappings=true` in all three cfg files, so it already uses the enhanced `SequenceStyleGenerator`. — [hibernate.cfg.xml](file://uPortal/uPortal-webapp/src/main/resources/properties/db/hibernate.cfg.xml); the setting is removed in 6.0: [AvailableSettings 6.0](https://github.com/hibernate/hibernate-orm/blob/6.0/hibernate-core/src/main/java/org/hibernate/cfg/AvailableSettings.java)
- **The dialects uPortal ran under in 4.3:**
  - `PortalDialectResolver` forces `SQLServer2005Dialect` for SQL Server > 9 and `MySQL5InnoDBCompressedDialect` for MySQL major 5. Everything else falls back to Hibernate's resolver. — [PortalDialectResolver.java](file://uPortal/uPortal-utils/uPortal-utils-core/src/main/java/org/apereo/portal/utils/PortalDialectResolver.java)
  - Hibernate 4.3's `StandardDialectResolver` maps "MySQL" ≥ 5 to `MySQL5Dialect` and SQL Server 11 to `SQLServer2012Dialect`. — [StandardDialectResolver 4.3](https://github.com/hibernate/hibernate-orm/blob/4.3/hibernate-core/src/main/java/org/hibernate/engine/jdbc/dialect/internal/StandardDialectResolver.java)
- **Sequence support in Hibernate 7.4 dialects:**
  - `SQLServerDialect`: minimum 12.0, and `getSequenceSupport()` always returns a sequence implementation (`SQLServer16SequenceSupport` or `SQLServerSequenceSupport`). — [SQLServerDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/SQLServerDialect.java)
  - `MariaDBDialect`: minimum 10.6, returns `MariaDBSequenceSupport`. — [MariaDBDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/MariaDBDialect.java)
  - `MySQLDialect`: minimum 8, returns `NoSequenceSupport`. — [MySQLDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/MySQLDialect.java)
  - A MariaDB 10.3 dialect with sequence support has existed since 5.x. — [MariaDB103Dialect javadoc](https://docs.hibernate.org/orm/5.6/javadocs/org/hibernate/dialect/MariaDB103Dialect.html); [Vlad Mihalcea – MariaDB 10.3 sequences](https://vladmihalcea.com/mariadb-10-3-database-sequences)
- **Sequence increment validation (5.4+):** startup fails by default (`EXCEPTION`) when the DB sequence increment ≠ allocationSize. `LOG`, `FIX` and `NONE` are the alternatives. — [AvailableSettings 5.6](https://github.com/hibernate/hibernate-orm/blob/5.6/hibernate-core/src/main/java/org/hibernate/cfg/AvailableSettings.java)
  - Forum reports show the error text ("increment size of the [X] sequence is set to [50] in the entity mapping while the associated database sequence increment size is [1]"). They also warn that lowering allocationSize against existing data invites collisions. — [discourse.hibernate.org](https://discourse.hibernate.org/t/sequencegenerator-allocationsize-versus-increment-by/5070); [forum.hibernate.org](https://forum.hibernate.org/viewtopic.php?p=2491551)
- **6.0 implicit sequence naming (`<entity>_seq`, allocation 50)** only affects generators without an explicit name. `hibernate.id.db_structure_naming_strategy=legacy` (5.3+ names) or `single` (`hibernate_sequence`) keeps the old names. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc); [Thorben Janssen – Migrating to Hibernate 6 (via search summary)](https://thorben-janssen.com/migrating-to-hibernate-6)
- **5.3 `@TableGenerator` semantics:** existing apps must set `hibernate.id.generator.stored_last_used=false`. — [5.3 MG](https://github.com/hibernate/hibernate-orm/blob/5.3/migration-guide.adoc)
- **7.2+ has `SchemaManager.resynchronizeGenerators()`.** It "restarts every sequence so that the next generated unique key will be larger than the largest key currently in use … also updates such tables" for table-based generators. It is `@Incubating`. — [SchemaManager 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/relational/SchemaManager.java)
- **7.0 generator validation:** "no longer valid to combine `GenerationType#SEQUENCE` with anything other than `@SequenceGenerator` nor `GenerationType#TABLE` with anything other than `@TableGenerator`." — [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
- **8.0 `@NativeGenerator`** changes its default allocation size from 1 to 50, so sequences need altering if it is adopted. — [8.0 MG](https://github.com/hibernate/hibernate-orm/blob/8.0/migration-guide.adoc)

**B. LOB / string columns**
- **PostgreSQL:** 4.3–5.6.1 created `text` columns for CLOBs while storing large-object OIDs in them. 5.6.2+ creates `oid`. `vacuumlo` can delete LOBs referenced only from `text` columns, which is real **data loss**; the guide gives `alter … type oid using clobfield::oid`. — [5.6 MG](https://github.com/hibernate/hibernate-orm/blob/5.6/migration-guide.adoc)
- 7.4 `PostgreSQLDialect` still maps BLOB/CLOB/NCLOB DDL to `oid` and forces `ClobJdbcType.CLOB_BINDING`/`BlobJdbcType.BLOB_BINDING`. — [PostgreSQLDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/PostgreSQLDialect.java)
- **uPortal `@Lob` fields:**
  - `PersistentPortalEvent.eventData`, `PersistentAnalyticsEvent.eventData` and `PortletPreferenceImpl.name` are plain `@Lob String`.
  - **`PortletPreferenceImpl` `PREF_VALUE` is `@Lob @Column(length=100000) @Type(type="org.hibernate.type.StringType")`**: the `@Type` override makes it a VARCHAR-bound column today.
  - — [PortletPreferenceImpl.java](file://uPortal/uPortal-content/uPortal-content-portlet/src/main/java/org/apereo/portal/portlet/dao/jpa/PortletPreferenceImpl.java); [PersistentPortalEvent.java](file://uPortal/uPortal-events/src/main/java/org/apereo/portal/events/handlers/db/PersistentPortalEvent.java)
- **Never pass through 5.2.9/5.2.10 on PostgreSQL**: data written by them is unreadable by 5.2.11+ and vice versa. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
- **Oracle:**
  - uPortal's `Oracle12ForceClobDialect` maps `VARCHAR` beyond 4000 to `clob` and `CHAR` to `char(1 char)`. — [Oracle12ForceClobDialect.java](file://uPortal/uPortal-hibernate/uPortal-hibernate4-dialects/src/main/java/org/apereo/portal/utils/hibernate4/dialects/Oracle12ForceClobDialect.java)
  - `byte[]` → BLOB vs LONG RAW is governed by `hibernate.dialect.oracle.prefer_long_raw`. — [5.1 MG](https://github.com/hibernate/hibernate-orm/blob/5.1/migration-guide.adoc)
  - Oracle 23+ value-LOB DDL in 7.4 can be disabled with `hibernate.dialect.oracle.value_lob_enabled=false`. — [7.4 MG](https://github.com/hibernate/hibernate-orm/blob/7.4/migration-guide.adoc)

**C. Temporal / boolean / enum / UUID / Duration columns**
- **Timezones:**
  - 6.2: `hibernate.timezone.default_storage=DEFAULT` stores offsets natively where supported. **Loading `OffsetDateTime`/`ZonedDateTime` written by 5.x with a non-UTC `hibernate.jdbc.time_zone` can return wrong values**; set `NORMALIZE`. — [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc)
  - 6.0: Instant uses `TIMESTAMP_UTC`; `preferred_instant_jdbc_type=TIMESTAMP` reverts. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
  - 6.5 adds opt-in direct java.time JDBC binding; 8.0 makes it the default, and `hibernate.type.java_time_use_direct_jdbc=false` reverts. — [6.5 MG](https://github.com/hibernate/hibernate-orm/blob/6.5/migration-guide.adoc); [8.0 MG](https://github.com/hibernate/hibernate-orm/blob/8.0/migration-guide.adoc)
- **Booleans:** on Oracle, Hibernate 7.4 uses `BIT`→`number(1,0)` below version 23 and **native `boolean` from 23 onwards** (`getPreferredSqlTypeCodeForBoolean`). `hibernate.type.preferred_boolean_jdbc_type` (since 6.0) overrides it, e.g. `=BIT`. — [OracleDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/OracleDialect.java); [MappingSettings 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/cfg/MappingSettings.java)
- **Enums:**
  - Ordinal enums: 6.1 SMALLINT, then 6.2 TINYINT unless more than 128 entries. The 6.1 guide says validation tolerates the difference.
  - STRING enums: MySQL/MariaDB `ENUM` from 6.2, H2 from 6.5. Revert with `@JdbcTypeCode(SqlTypes.VARCHAR)` or `columnDefinition`.
  - Generated DDL gets check constraints from 6.2.
  - — [6.1 MG](https://github.com/hibernate/hibernate-orm/blob/6.1/migration-guide.adoc); [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc); [6.5 MG](https://github.com/hibernate/hibernate-orm/blob/6.5/migration-guide.adoc)
- **UUID / Duration / arrays / Oracle floats:**
  - UUID: native types (6.0; MariaDB and SQL Server in 6.2); revert with `preferred_uuid_jdbc_type=BINARY`.
  - Duration: revert with `preferred_duration_jdbc_type=BIGINT`.
  - Arrays: revert with `@JdbcTypeCode(VARBINARY)` (6.1) or `preferred_array_jdbc_type=VARBINARY` (7.0).
  - Oracle `binary_float`/`binary_double`: revert with `use_binary_floats=false` (7.0); the guide says changing the schema is not required.
  - — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc); [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc); [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)

**D. Table / column / constraint names**
- The 5.0 default implicit naming became JPA-compliant. The Hibernate team's forum advice for 4.x-like names is `hibernate.implicit_naming_strategy=legacy-hbm`. — [5.0 MG](https://github.com/hibernate/hibernate-orm/blob/5.0/migration-guide.adoc); [forum.hibernate.org thread](https://forum.hibernate.org/viewtopic.php?p=2491838)
- `legacy-jpa` corresponds to the 4.x `EJB3NamingStrategy`, which hibernate-entitymanager used. — [ImplicitNamingStrategyLegacyJpaImpl 5.6](https://github.com/hibernate/hibernate-orm/blob/5.6/hibernate-core/src/main/java/org/hibernate/boot/model/naming/ImplicitNamingStrategyLegacyJpaImpl.java)
- **Implicit FK/UK/index names change algorithm:**
  - 4.3 used `Constraint.generateName("FK_", table, columns)`, i.e. `FK_` + hash. — [Constraint 4.3](https://github.com/hibernate/hibernate-orm/blob/4.3/hibernate-core/src/main/java/org/hibernate/mapping/Constraint.java)
  - 5.x uses `NamingHelper.generateHashedFkName("FK", …)`, plus `"UK"`/`"IDX"` hashing in `ImplicitNamingStrategyJpaCompliantImpl`, which the legacy strategies extend. — [ImplicitNamingStrategyJpaCompliantImpl 5.6](https://github.com/hibernate/hibernate-orm/blob/5.6/hibernate-core/src/main/java/org/hibernate/boot/model/naming/ImplicitNamingStrategyJpaCompliantImpl.java)
  - 6.5 started applying the ImplicitNamingStrategy to unique-key names. — [6.5 MG](https://github.com/hibernate/hibernate-orm/blob/6.5/migration-guide.adoc)
- Users report that Hibernate 5 `hbm2ddl update` against a 4.x-created DB created **duplicate FKs with new names**, because it matches constraints by name. Unique constraints from `@Column(unique=true)` and join tables bypass the naming strategy. — [forum.hibernate.org](https://forum.hibernate.org/viewtopic.php?p=2491838); [fluent-hibernate README](https://github.com/v-ladynev/fluent-hibernate/blob/master/README.md)
- uPortal declares no `@ForeignKey` or naming-strategy settings (source grep), so all FK names are implicit. Index names come from Hibernate `@Index(name=…)`. — [uPortal source tree](file:///home/user/uPortal)
- `hibernate.mapping.default_list_semantics` defaults to `BAG` (6.0+) for Lists without `@OrderColumn`. — [MappingSettings 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/cfg/MappingSettings.java)

**E. Minimum DB versions and the HSQLDB dev DB**
- Minimum versions by dialect:

  | Database | 6.6 | 7.4 |
  |---|---|---|
  | PostgreSQL | 12 | **14** |
  | Oracle | 19 | 19 |
  | MySQL | 8 | 8 |
  | MariaDB | 10.4 | **10.6** |
  | SQL Server | 11.0 (2012) | **12.0 (2014)** |
  | HSQLDB | 2.6.1 | 2.6.1 |
  | H2 | 2.1.214 | 2.1.214 |

  8.0 has the same minimums as 7.4. Older versions need hibernate-community-dialects. — [PostgreSQLDialect 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/dialect/PostgreSQLDialect.java); [6.2 MG](https://github.com/hibernate/hibernate-orm/blob/6.2/migration-guide.adoc)
- uPortal-start pins `hsqldbVersion=2.5.1`, below 2.6.1, while uPortal itself uses 2.7.4. — [uPortal-start gradle.properties](file://uPortal-start/gradle.properties); [uPortal gradle.properties](file://uPortal/gradle.properties)

**F. Verifying schema equality**
- **Hibernate's own validation:**
  - `SchemaManager` (6.2+) provides `validateMappedObjects`, `exportMappedObjects`, `truncateMappedObjects` and `dropMappedObjects`. It extends `jakarta.persistence.SchemaManager` as of JPA 3.2 / Hibernate 7.0 and adds `populate`, `resynchronizeGenerators` and `forSchema`. — [SchemaManager 7.4](https://github.com/hibernate/hibernate-orm/blob/7.4/hibernate-core/src/main/java/org/hibernate/relational/SchemaManager.java); [7.0 MG](https://github.com/hibernate/hibernate-orm/blob/7.0/migration-guide.adoc)
  - The 6.x guides consistently phrase type-default changes as "schema validation errors could occur", which makes `hbm2ddl validate` the canary for each change. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Liquibase:** the liquibase-hibernate extension (`liquibase-hibernate6`; a hibernate5 build also exists) can diff entity mappings against a live DB. It has known spurious `modifyDataType` diffs, and maintainers call it a secondary tool. — [liquibase-hibernate on JitPack](https://jitpack.io/p/liquibase/liquibase-hibernate); [Liquibase forum](https://forum.liquibase.org/t/diffchangelog-is-generating-redundant-modifydatatype-changes/8032)

### Inferences
- **ID-generation plan (most important):**
  1. Before upgrading, inventory each production DB. Check whether each `UP_*_SEQ` object is a real SEQUENCE or a TABLE with a `next_val` column, and record the increment of each sequence versus its allocationSize.
     - Expect tables on SQL Server (`SQLServer2005Dialect`) and on MySQL/MariaDB.
     - Expect sequences on PostgreSQL, Oracle and HSQLDB.
     - The table fallback is how `SequenceStyleGenerator` behaves when the dialect lacks sequences. I know this behavior but did not re-verify it in the source.
  2. Choose one approach per database:
     - **Option A:** subclass `SQLServerDialect`/`MariaDBDialect` and override `getSequenceSupport()` to return `NoSequenceSupport.INSTANCE`, which keeps the table-backed generators exactly as they are.
     - **Option B:** run a one-time migration that creates sequences `START WITH` each table's `next_val`, using the same increment as allocationSize, then drops the tables. Then run `SchemaManager.resynchronizeGenerators()` (7.2+) or a manual `max(id)+allocation` check as a safety net.
     - Either way, set `hibernate.id.sequence.increment_size_mismatch_strategy=EXCEPTION` (the default) in test environments to catch drift. Avoid `FIX` in production until the effect is understood.
  3. Remove the dead same-named `@TableGenerator` annotations so 6.x/7.x validation cannot pick a different generator. Set `hibernate.id.generator.stored_last_used=false` defensively during the 5.x hop in case any entity relies on a TableGenerator alone.
  4. Keep the explicit `sequenceName` values. Also set `hibernate.id.db_structure_naming_strategy=legacy` in case any `@GeneratedValue` lacks a named generator.
- **PostgreSQL LOBs:**
  - Keep `@Lob String` columns as OID-in-`text`; Hibernate 7.4 still uses CLOB binding, so reads work. Before production, though, either `ALTER … TYPE oid USING col::oid` or make sure `vacuumlo` is never run.
  - For `PREF_VALUE`, replace `@Type(StringType)` with an explicit 6.x mapping that preserves VARCHAR binding and the existing DDL (e.g. drop `@Lob` and use `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` or `@Column(columnDefinition=…)` per DB). Otherwise Hibernate 6+ will start treating it as a CLOB/oid. Verify on all five databases.
- **Recommended "freeze the 4.3/5.x behavior" settings for the 6.x/7.x hops.** Each is documented. Apply them first, then remove them one at a time with explicit data migrations if desired:
  - `hibernate.implicit_naming_strategy=legacy-jpa`, after confirming with a DDL diff; `legacy-hbm` is the alternative.
  - `hibernate.id.db_structure_naming_strategy=legacy`
  - `hibernate.timezone.default_storage=NORMALIZE`
  - `hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`
  - `hibernate.type.preferred_uuid_jdbc_type=BINARY`, only if UUIDs are mapped as `java.util.UUID`
  - `hibernate.type.preferred_duration_jdbc_type=BIGINT`
  - `hibernate.type.preferred_boolean_jdbc_type=BIT`, on Oracle 23+
  - `hibernate.type.wrapper_array_handling=legacy`
  - `hibernate.type.preferred_array_jdbc_type=VARBINARY`
  - `hibernate.dialect.oracle.use_binary_floats=false`
  - `hibernate.dialect.oracle.value_lob_enabled=false`, where MSSM tablespaces are used
  - `hibernate.query.native.prefer_jdbc_datetime_types=true`, if native-query consumers expect `java.sql` types
  - `hibernate.cache.query_cache_layout=SHALLOW`
- **Verification recipe:**
  1. Restore production snapshots for each of PostgreSQL, MySQL/MariaDB, Oracle and SQL Server.
  2. Boot the new version with `jakarta.persistence.schema-generation.database.action=none` plus `hbm2ddl.auto=validate` (or call `SchemaManager.validateMappedObjects()`).
  3. Separately, generate DDL scripts from both the old and new mapping (via JPA `schema-generation.scripts.*`) and diff them, normalizing hashed constraint names.
  4. Run smoke inserts on every entity type to confirm the generators continue numbering without collisions.
  5. Use Flyway or Liquibase only for the explicit data and DDL conversions, not for diff generation.

### Gaps
- I did not confirm how 6.x/7.x treat `@Lob` + `@Type`-less `String` on SQL Server, MySQL and Oracle relative to 4.3 DDL (`nvarchar(max)`/`longtext`/`clob`), or how Oracle `byte[]` columns (LONG RAW vs BLOB) were created under uPortal's `Oracle12ForceClobDialect`. Its superclass was not inspected.
- I did not verify the exact 4.3 DDL for sequences created by `SchemaExport` with the pooled optimizer, i.e. whether `increment by` equals allocationSize. Check against real databases.
- No Flyway "baseline" documentation was fetched. The Flyway/Liquibase recommendations above are general practice, not sourced.
- Jadira `PersistentDateTime` stores a UTC/JVM-zone `timestamp`. Exactly how it stores values (zone handling, `databaseZone=jvm` for `localDate`) determines how to write equivalent java.time/converter mappings, and I did not research it.

---

## Q4. Is there automated tooling (OpenRewrite recipes, Eclipse Transformer), and how complete is it?

### Takeaway
OpenRewrite's `rewrite-hibernate` has composite recipes `MigrateToHibernate60`, `61`, `62`, `63`, `64`, `65`, `66`, `70` and `71`, each chaining the previous one. They handle mostly mechanical work: dependencies, javax→jakarta, `@Type(type=String)` → class, boolean types → converters, descriptor renames, dialect class renames, some 7.0 Session method renames, and `@LazyCollection`. There are **no 4.x→5.x recipes and no 7.2+ recipes**, and semantic issues are left to humans: UserType bodies, hbm typedefs, Jadira, ID generators, DDL, query semantics. The Eclipse Transformer covers javax→jakarta only. The repo is under the Moderne Source Available License, not Apache.

### Cited Findings
- **Recipe files:** `hibernate-6.0.yml` … `hibernate-6.6.yml`, `hibernate-7.0.yml`, `hibernate-7.1.yml` and `hibernate-validator.yml`. Last commit 2026-10-08. — [rewrite-hibernate recipes dir](https://github.com/openrewrite/rewrite-hibernate/tree/main/src/main/resources/META-INF/rewrite)
- **License:** "Moderne Source Available License". The build applies `org.openrewrite.build.moderne-source-available-license`. — [LICENSE.md](https://github.com/openrewrite/rewrite-hibernate/blob/main/LICENSE.md)
- **Latest artifact:** `org.openrewrite.recipe:rewrite-hibernate` 2.25.0, metadata dated 2026-08-12. — [Maven Central](https://repo1.maven.org/maven2/org/openrewrite/recipe/rewrite-hibernate/maven-metadata.xml)
- **`org.openrewrite.hibernate.MigrateToHibernate60` includes:**
  - `MigrateToHibernateDependencies60`, the dependency changes
  - `AddScalarPreferStandardBasicTypes`
  - `EmptyInterceptorToInterface`
  - `MigrateBooleanMappings` ("Replaces type mapping of booleans with appropriate attribute converters")
  - `TypeAnnotationParameter` ("Hibernate 6.x has 'type' parameter of type String replaced with 'value' of type class"; it also matches `@TypeDef`/`@TypeDefs`)
  - `TypeDescriptorToType`
  - `org.openrewrite.java.migrate.jakarta.JavaxPersistenceToJakartaPersistence`
  - `JavaxPersistenceXmlToJakartaPersistenceXml`
  - `MigrateToHypersistenceUtilsHibernate60`
  - — [hibernate-6.0.yml](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/main/resources/META-INF/rewrite/hibernate-6.0.yml); [TypeAnnotationParameter.java](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/main/java/org/openrewrite/hibernate/TypeAnnotationParameter.java)
- **Other recipes:**
  - A separate `MigrateUserType` Java recipe adapts UserType to the generic 6.x interface. — [MigrateUserType.java](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/main/java/org/openrewrite/hibernate/MigrateUserType.java)
  - `MigrateToHibernate62` adds `MigrateDialect` (ChangeType for removed or version-specific dialects) and `ReplaceLazyCollectionAnnotation`. — [hibernate-6.2.yml](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/main/resources/META-INF/rewrite/hibernate-6.2.yml)
- **`MigrateToHibernate70`:**
  - chains `MigrateToHibernate66`
  - renames `Session.save(…)` → `persist`, `delete` → `remove`, `get(Class, Serializable)` → `find`, `load` → `getReference`
  - maps `CascadeType.DELETE` → `REMOVE`
  - maps `MetadataContributor`/`AdditionalJaxbMappingProducer` → `AdditionalMappingContributor`
  - maps removed DB2/HANA/MariaDB106 dialects to generic ones
  - `RemoveGeneratedValueStrategyWithGenericGenerator`
  - `MigrateToHibernate71` only bumps versions and Hypersistence.
  - — [hibernate-7.0.yml](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/main/resources/META-INF/rewrite/hibernate-7.0.yml); [hibernate-7.1.yml](https://github.com/openrewrite/rewrite-hibernate/blob/main/src/main/resources/META-INF/rewrite/hibernate-7.1.yml)
- **Eclipse Transformer:** the 6.0 guide recommends it "along with appropriate 'rules'" to transform packages, settings and XSD references, and points to Hibernate's own `rules/` directory. — [6.0 MG](https://github.com/hibernate/hibernate-orm/blob/6.0/migration-guide.adoc)
- **Hypersistence:** the 6.0 recipe migrates `com.vladmihalcea:hibernate-types-*` to `io.hypersistence:hypersistence-utils-hibernate-60` (search summary of the OpenRewrite docs). — [OpenRewrite doc: MigrateToHypersistenceUtilsHibernate60](https://docs.openrewrite.org/recipes/hibernate/migratetohypersistenceutilshibernate60)

### Inferences
- **Coverage for uPortal:**
  - The recipes handle: the javax→jakarta imports and `persistence.xml`; `@Type(type="fully.qualified.Class")` → `@Type(Class.class)`; and `JavaTypeDescriptor`/`SqlTypeDescriptor` renames in the custom dialect.
  - Probably not: `@Type` short names like `"fname"` or `"dateTime"`, which refer to hbm `<typedef>` aliases and cannot be resolved to a class without the hbm file. Treat these as manual work.
  - Not covered at all: Hibernate `@Index` → JPA, `@IndexColumn` → `@OrderColumn`, Jadira, Ehcache, `hibernate.ejb.*` properties, custom dialect APIs, ID generators, and anything in 4.3→5.x.
- **The 7.0 renames are not semantics-preserving.**
  - `save` → `persist` changes the return type (`save` returned the id; `persist` is void) and cascade semantics.
  - `update`/`saveOrUpdate` are not handled.
  - Review every change; uPortal mostly uses JPA `EntityManager`, which limits the exposure.
- **Sequencing:** run OpenRewrite in stages that match the Spring hops. Use `UpgradeSpringFramework_5_3`, then `JakartaEE10` + `MigrateToHibernate66`, then the Spring 7 / Hibernate 7.x recipes. Compile and run tests between stages.

### Gaps
- I couldn't fetch docs.openrewrite.org (blocked), so I couldn't confirm recipe option docs or whether newer 7.2+/7.4 recipes exist beyond the repo's `main` branch.
- I did not test how `TypeAnnotationParameter` handles non-FQCN type names.

---

## Q5. Hibernate + Spring compatibility matrix: must Hibernate and Spring move in lockstep?

### Takeaway
Yes, effectively in lockstep:

| Spring Framework | Hibernate ORM it supports |
|---|---|
| 4.3 | up to 5.2 |
| 5.1 | 5.3 |
| 5.3 | 5.2+, focused on 5.4.x (5.6 runs on it) |
| 6.0 | 6.1, or 5.6 `-jakarta` |
| 6.2 | 6.x (6.5+ for native image; Spring Boot 3.4/3.5 ship Hibernate 6.6) |
| 7.0 | 7.1/7.2 (JPA 3.2); Boot 4.1 pairs Hibernate 7.4 |
| 7.1 (Nov 2026) | 7.3, with optional JPA 4.0 / Hibernate 8.0 |

The javax→jakarta switch (Spring 6 with Hibernate 6) must happen together. Spring 6.0 also removed Spring's Ehcache 2 cache-manager support, and Spring 7.0 moved native Hibernate support from `orm.hibernate5` to `orm.jpa.hibernate`.

### Cited Findings
- **Spring 4.3:** "Spring 4.3 supports all current versions of its optionally integrated libraries, including _Hibernate ORM 5.2_". 4.2 introduced Hibernate 5.0 support "next to the existing support for Hibernate ORM 3.6 and 4.2 / 4.3". — [Upgrading to Spring Framework 4.x](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-4.x)
- **Spring 5.0:** minimum "Hibernate 5.0+" and "EhCache 2.10+". "Packages `web.view.tiles2` and `orm.hibernate3/hibernate4` dropped." — [Spring 5.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.0-Release-Notes)
- **Spring 5.1:** "Support for Hibernate ORM 5.3", including bean container integration with Hibernate's new SPI. — [Spring 5.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.1-Release-Notes)
- **Spring 5.3:**
  - "Hibernate support has been upgraded to a Hibernate ORM 5.2+ baseline, with a focus on ORM 5.4.x". — [Spring 5.3 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-5.3-Release-Notes)
  - JDK 8–21, javax EE 8 (JPA 2.2). "Open source support ended in August 2024". — [Spring Framework Versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions)
- **Spring 6.0:**
  - "upgrade to Hibernate ORM 5.6.x with the `hibernate-core-jakarta` artifact … Alternatively, consider migrating to Hibernate ORM 6.1 right away". Also "JPA support for Hibernate ORM 6.1 (retaining compatibility with Hibernate ORM 5.6)".
  - "The `org.springframework.cache.ehcache` package has been removed as it was providing support for Ehcache 2.x".
  - — [Spring 6.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes)
- **Spring 6.2:**
  - "For GraalVM native image support only, Hibernate 6.5". — [Spring 6.2 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes)
  - JDK 17–25, Jakarta EE 9–10 (JPA 3.1). "Open source support ends in June 2026". — [Spring Framework Versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions)
  - Hibernate 6.6 lists Spring Boot 3.4–3.5. — [6.6 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/6.6/series.yml)
- **Spring 7.0:**
  - "retains a JDK 17 baseline … recommending JDK 25", "Jakarta EE 11 baseline", "JPA 3.2 (Hibernate ORM 7.1/7.2)".
  - "the native Hibernate support formerly living in `org.springframework.orm.hibernate5` having migrated to the `orm.jpa.hibernate` package … `LocalSessionFactoryBean/Builder`, `HibernateTransactionManager`, `SpringBeanContainer`, `SpringSessionContext`".
  - `MutablePersistenceUnitInfo` no longer implements `PersistenceUnitInfo`. — [Spring 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
  - 7.0.x is "the current production line (November 2025)". 7.x uses EE 11 and early EE 12 (JPA 4.0). — [Spring Framework Versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions)
- **Spring 7.1:**
  - "Hibernate ORM 7.3 support; runtime compatibility with Hibernate ORM 7.1 and 7.2 is retained".
  - "optional support for the upcoming JPA 4.0 and Hibernate ORM 8.0".
  - "Existing applications can already run against Hibernate 8.0 on Spring Framework 7.0.10". — [Spring 7.1 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes)
  - The latest `spring-orm` on Maven Central is 7.1.0-M2 (2026-09-24), so 7.1 is not GA. — [Maven Central spring-orm](https://repo1.maven.org/maven2/org/springframework/spring-orm/maven-metadata.xml)
- **Spring Boot pairings from the Hibernate side:** Boot 4.0 with Hibernate 7.2; Boot 4.1 with Hibernate 7.4. — [7.2 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/7.2/series.yml); [7.4 series.yml](https://github.com/hibernate/hibernate.org/blob/production/_data/projects/orm/releases/7.4/series.yml)

### Inferences
- **Feasible phased path for uPortal:**
  1. **Spring 4.3 + Hibernate 5.2.x** (≥ 5.2.18, avoiding 5.2.9/5.2.10). This is the max for Spring 4.3; entitymanager merges into core and naming/ID settings are frozen.
  2. **Spring 5.3 + Hibernate 5.6.15** (javax, JPA 2.2). Ehcache 2 still works here.
  3. **Spring 6.2 + Hibernate 6.6** (jakarta, Java 17). Spring 6.2's OSS support ended in June 2026, so treat this only as a transit/test checkpoint. The Ehcache 2 → JCache move is forced here, by Hibernate 6.0 and Spring 6.0 together.
  4. **Spring 7.0.x + Hibernate 7.x.** Spring 7.0 names 7.1/7.2; Boot 4.1 pairs Spring 7.0.x with Hibernate 7.4, so 7.4 is a tested combination. Move to Spring 7.1 + Hibernate 7.3/7.4 (or 8.0) once 7.1 is GA.
- Collapsing steps 3 and 4 (Spring 7 + Hibernate 7.4 directly from 5.6) is possible because each Hibernate guide is cumulative. It concentrates the jakarta, type-system, query and 7.0-removal risks into one release, though. A test-only 6.6 stage still helps isolate DDL/type drift with `validate`.
- uPortal uses `LocalContainerEntityManagerFactoryBean` + `HibernateJpaVendorAdapter` rather than `orm.hibernate5` classes, so the Spring 7 package move mostly affects any `HibernateTemplate`/`LocalSessionFactoryBean` usage elsewhere. I did not check for that.

### Gaps
- No single official Spring×Hibernate matrix exists. The pairings above come from per-release notes.
- I did not verify that Hibernate 7.3/7.4 is officially supported (as opposed to merely working) on Spring 7.0.x beyond the Boot 4.1 pairing.

---

## Q6. Real-world migration reports and gotchas in large legacy apps

### Takeaway
Practitioners report the same pain points:
- sequence naming and allocation mismatches when moving to 6.x (missing `<entity>_SEQ`, increment ≠ allocationSize)
- FK/constraint renaming when moving from 4.x to 5.x, producing duplicates under `hbm2ddl update`
- custom-type breakage (UserType API, Hypersistence, HQL over custom types)
- third-party type libraries that never reached Hibernate 6: **Jadira usertype's last release is 7.0.0.CR1 from 2018**, with no Hibernate 6 support

For caching, Ehcache 3 has no JGroups replication. Its clustering requires a Terracotta server, so uPortal's `ehcache-jgroupsreplication` setup has no drop-in equivalent.

### Cited Findings
- **Thorben Janssen** (via search summary; site blocked):
  - After moving to Hibernate 6, apps failed on missing `Author_SEQ`-style sequences because "the entity-specific sequences don't exist in the database".
  - He recommends configuring `hibernate.id.db_structure_naming_strategy` (`single` = pre-5.3 defaults, `legacy` = 5.3+ defaults) during migration.
  - He also notes the allocation size of 50 and the need to fix `import.sql`.
  - — [Thorben Janssen – Migrating to Hibernate 6](https://thorben-janssen.com/migrating-to-hibernate-6); [Things to know when migrating to Hibernate 6.x](https://thorben-janssen.com/things-to-know-when-migrating-to-hibernate-6-x)
- **Hibernate forum (4→5):** a user saw Hibernate 5 leave 4.x FK names (`FK281588E9730950D2`) in place and add duplicates (`FKbxtm5y7q7jul8or4g1gmkpa6v`) under `hbm2ddl update`. The team suggested `hibernate.implicit_naming_strategy=legacy-hbm`. — [forum.hibernate.org](https://forum.hibernate.org/viewtopic.php?p=2491838); [forum.hibernate.org](https://forum.hibernate.org/viewtopic.php?p=2491833)
- **Discourse:**
  - The validator requires DB `INCREMENT BY` to equal `allocationSize`, which users find "overzealous". — [discourse.hibernate.org](https://discourse.hibernate.org/t/sequencegenerator-allocationsize-versus-increment-by/5070)
  - HQL `max()` over a Hypersistence custom type worked on 5 but failed on 6. — [discourse.hibernate.org](https://discourse.hibernate.org/t/hibernate-6-x-breaks-function-validation-on-custom-usertype-parameters/10870)
  - A custom ID generator broke between 5.x and 6.3. — [hibernate-orm discussion #7410](https://github.com/hibernate/hibernate-orm/discussions/7410)
  - Migrating custom dialects to Hibernate 6. — [discourse.hibernate.org](https://discourse.hibernate.org/t/migration-of-dialect-to-hibernate-6/6956)
- **Jadira:**
  - The latest `org.jadira.usertype:usertype.core` is `7.0.0.CR1`, metadata dated 2018-01-25; earlier GA versions are 6.0.1.GA and older. — [Maven Central usertype.core](https://repo1.maven.org/maven2/org/jadira/usertype/usertype.core/maven-metadata.xml)
  - Search found no evidence of Hibernate 6 support. One forum thread reports AbstractMethodError incompatibilities even with Hibernate 5. — [forum.hibernate.org](https://forum.hibernate.org/viewtopic.php?p=2487729); [Jadira usertype guide](https://jadira.sourceforge.net/usertype-userguide.html)
- **Native java.time:** native since 5.2 (hibernate-java8 merged), so `java.time` fields need no extra library. — [5.2 MG](https://github.com/hibernate/hibernate-orm/blob/5.2/migration-guide.adoc)
- **Ehcache 3 clustering:** "To enable clustering with Terracotta, you will have to deploy a Terracotta server configured with clustered cache storage." — [Ehcache 3 clustered-cache doc](https://github.com/ehcache/ehcache3/blob/master/docs/src/docs/asciidoc/user/clustered-cache.adoc)
- **Ehcache 2 replication** used JGroups/RMI/JMS peer providers. — [Ehcache 2.7 JGroups replication](https://www.ehcache.org/documentation/2.7/replication/jgroups-replicated-caching)
- **uPortal's cache setup:** `ehcache.xml` / `ehcache-no-jgroups.xml` configure `JGroupsCacheManagerPeerProviderFactory` and `RMICacheManagerPeerProviderFactory`. — [ehcache-no-jgroups.xml](file://uPortal/uPortal-webapp/src/main/resources/properties/ehcache-no-jgroups.xml)
- **Hibernate 6.6 built-in providers** are JCache and Infinispan only. — [6.6 Caching.adoc](https://github.com/hibernate/hibernate-orm/blob/6.6/documentation/src/main/asciidoc/userguide/chapters/caching/Caching.adoc)

### Inferences
- **Jadira replacement options, in order of preference:**
  1. Migrate entity fields from Joda types (`DateTime`, `LocalDate`, `LocalTime`, `MonthDay`) to `java.time`. This needs no library, but ripples into the uPortal APIs and events code.
  2. Write small `AttributeConverter<org.joda.time.DateTime, java.sql.Timestamp>` (etc.) classes that reproduce Jadira's storage. Jadira stores in UTC/JVM zone; check `databaseZone=jvm` on `localDate`, and `PersistentMonthDayAsString` stores a string.
  3. Write custom 6.x/7.x `UserType`s.

  Converters are the most API-stable choice across 6.x→7.x→8.0. Validate round-trips against existing rows, especially the zone semantics around DST.
- **Second-level cache:**
  - Replace `SpringBeanEhCacheRegionFactory` with `hibernate.cache.region.factory_class=jcache` plus Ehcache 3 (`org.ehcache:ehcache` 3.12.0 exists on Maven Central; use the jakarta classifier/variant).
  - Port the 184 cache definitions to Ehcache 3 XML. Rename the query-cache regions to `default-query-results-region` and `default-update-timestamps-region`, and set `missing_cache_strategy=fail` in tests to catch name mismatches.
  - If the cluster needs JGroups-style replication or invalidation of entity caches, Infinispan (`infinispan-hibernate-cache`, JGroups-based) is the closest equivalent. Otherwise run local caches and accept staleness, or deploy Terracotta.
- **Highest-yield up-front tests for a large legacy app:**
  - boot with schema `validate` on each DB type
  - insert one row of every entity (generators)
  - round-trip every custom-typed column (Joda, JSON, fname/qname/windowState, nullSafeString)
  - run every HQL/JPQL string through `createQuery` at startup (SQM strictness in 6.0/6.2/6.3)
  - exercise the query cache and natural-id cache with the new region names

### Gaps
- I couldn't fetch Thorben Janssen's and Vlad Mihalcea's full articles (blocked); their points above come from search snippets.
- I found no published case study of a uPortal-scale (or Apereo) Hibernate 4→7 in-place migration.
- I didn't verify Infinispan's current Hibernate 7.x integration artifact or version, or whether Ehcache 3.12 publishes a jakarta variant suitable for JCache under Jakarta EE 11.
