/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.persistence.roundtrip;

import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.sql.DataSource;
import org.apereo.portal.test.golden.DatabaseVendor;
import org.apereo.portal.test.golden.GoldenFiles;
import org.apereo.portal.test.golden.JvmTimeZone;
import org.apereo.portal.test.golden.PersistenceUnits;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Entity round trip (MOD-D29 item 7): every entity mapped in the three persistence units is built
 * with edge values ({@link Variant}), persisted, flushed, and read back in a new persistence
 * context, then compared field by field, including collections and associations. This runs on the
 * database of dataSource.properties (HSQLDB, or each vendor in db-integration.yml) and in two JVM
 * time zones.
 *
 * <p>The golden {@code roundtrip/<unit>-<zone>.txt} records, from HSQLDB with Hibernate 4.3:
 *
 * <ul>
 *   <li>{@code entity}: every entity type in the unit's metamodel, so a type that disappears or
 *       appears is noticed;
 *   <li>{@code class}: the Java class of each basic value read back;
 *   <li>{@code lossy}: each value that does not survive the round trip today, as it is today;
 *   <li>{@code row}: the rows stored in the database, with generated ids normalised ({@link
 *       StoredRows}).
 * </ul>
 *
 * <p>Other databases must produce the same file once the {@link VendorDifferences} are applied.
 */
@RunWith(Parameterized.class)
public class EntityRoundTripGoldenTest {
    /**
     * Fails a stuck or very slow case with the stack of the test thread, before the time limit of
     * the CI job cancels the whole run without a report.
     */
    @Rule
    public final Timeout timeout =
            Timeout.builder()
                    .withTimeout(5, TimeUnit.MINUTES)
                    .withLookingForStuckThread(true)
                    .build();

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityRoundTripGoldenTest.class);

    /**
     * Entities not round-tripped in some variants, with the reason. Every entity is covered by at
     * least one variant.
     */
    private static final Map<String, Set<Variant>> SKIPPED_VARIANTS =
            Collections.singletonMap(
                    // PROCESSING_TYPE is a unique (natural id) enum with only 3 values
                    "EventAggregatorStatusImpl", EnumSet.of(Variant.NULLS));

    private static GenericXmlApplicationContext context;
    private static String contextZone;

    private final String zone;
    private final String persistenceUnit;

    public EntityRoundTripGoldenTest(String zone, String persistenceUnit) {
        this.zone = zone;
        this.persistenceUnit = persistenceUnit;
    }

    @Parameters(name = "{1} in {0}")
    public static Collection<Object[]> parameters() {
        final List<Object[]> parameters = new ArrayList<>();
        for (final String zone : JvmTimeZone.GOLDEN_ZONES) {
            for (final String unit : Arrays.asList("PortalDb", "RawEventsDb", "AggrEventsDb")) {
                parameters.add(new Object[] {zone, unit});
            }
        }
        return parameters;
    }

    @AfterClass
    public static void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /** One context per zone, created after the zone is set so every connection sees it */
    private static GenericXmlApplicationContext context(String zone) {
        if (context != null && !zone.equals(contextZone)) {
            closeContext();
        }
        if (context == null) {
            context =
                    new GenericXmlApplicationContext("classpath:goldenPersistenceTestContext.xml");
            contextZone = zone;
        }
        return context;
    }

    @Test
    public void roundTripsEveryEntity() {
        try (JvmTimeZone timeZone = JvmTimeZone.set(zone)) {
            final GenericXmlApplicationContext ctx = context(zone);
            final EntityManagerFactory emf =
                    ctx.getBean(
                            persistenceUnit + "EntityManagerFactory", EntityManagerFactory.class);
            final DataSource dataSource = ctx.getBean(persistenceUnit, DataSource.class);
            final DatabaseVendor vendor = DatabaseVendor.of(dataSource);

            final String goldenName =
                    "roundtrip/" + persistenceUnit + "-" + timeZone.fileId() + ".txt";
            final Map<String, Map<String, String>> storedTags =
                    StoredRows.tags(GoldenFiles.expectedLines(goldenName));

            PersistenceUnits.deleteAll(emf);
            try {
                roundTrip(emf, dataSource, vendor, goldenName, storedTags);
            } catch (AssertionError | RuntimeException e) {
                // A failed clean-up must not hide why the round trip failed
                try {
                    PersistenceUnits.deleteAll(emf);
                } catch (RuntimeException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
            PersistenceUnits.deleteAll(emf);
        }
    }

    private void roundTrip(
            EntityManagerFactory emf,
            DataSource dataSource,
            DatabaseVendor vendor,
            String goldenName,
            Map<String, Map<String, String>> storedTags) {
        final Metamodel metamodel = emf.getMetamodel();
        final StoredRows before = readRows(dataSource, vendor, storedTags);
        final List<String> golden = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final EntityGraphComparator comparator =
                new EntityGraphComparator(metamodel, emf.getPersistenceUnitUtil(), vendor);
        final EdgeValues edgeValues = new EdgeValues(vendor);
        int seed = 1;

        for (final EntityType<?> entityType : PersistenceUnits.entityTypes(metamodel)) {
            final Class<?> type = entityType.getJavaType();
            if (Modifier.isAbstract(type.getModifiers())) {
                golden.add("entity " + type.getName() + " (abstract, covered by its subclasses)");
                continue;
            }
            final Map.Entry<Class<?>, PluralAttribute<?, ?, ?>> owner = ownerOf(metamodel, type);
            golden.add(
                    "entity "
                            + type.getName()
                            + (owner == null
                                    ? ""
                                    : " (stored in "
                                            + owner.getKey().getSimpleName()
                                            + "."
                                            + owner.getValue().getName()
                                            + ")"));
            for (final Variant variant : Variant.TESTED) {
                if (SKIPPED_VARIANTS
                        .getOrDefault(type.getSimpleName(), Collections.emptySet())
                        .contains(variant)) {
                    continue;
                }
                final String label = type.getSimpleName() + "[" + variant + "]";
                final Class<?> rootType = owner == null ? type : owner.getKey();
                final Object root;
                final Object id;
                final EntityManager writer = emf.createEntityManager();
                final EntityTransaction tx = writer.getTransaction();
                try {
                    tx.begin();
                    final EntityGraphBuilder builder =
                            new EntityGraphBuilder(writer, metamodel, edgeValues, seed);
                    root =
                            owner == null
                                    ? builder.buildRoot(type, variant)
                                    : builder.buildOwnerOf(
                                            owner.getKey(), owner.getValue(), type, variant);
                    seed = builder.nextSeed();
                    writer.persist(root);
                    writer.flush();
                    tx.commit();
                    id = emf.getPersistenceUnitUtil().getIdentifier(root);
                } catch (RuntimeException | LinkageError e) {
                    failures.add(label + ": could not be stored: " + rootCause(e));
                    LOGGER.warn("{} could not be stored", label, e);
                    if (tx.isActive()) {
                        tx.rollback();
                    }
                    continue;
                } finally {
                    writer.close();
                }
                final EntityManager reader = emf.createEntityManager();
                try {
                    reader.getTransaction().begin();
                    final Object reloaded =
                            reader.find(rootType, detachedId(reader, metamodel, emf, vendor, id));
                    comparator.compare(label, root, reloaded);
                    reader.getTransaction().commit();
                } catch (RuntimeException e) {
                    failures.add(label + ": could not be read back: " + rootCause(e));
                    LOGGER.warn("{} could not be read back", label, e);
                } finally {
                    reader.close();
                }
            }
        }
        for (final String valueClass : comparator.valueClasses()) {
            golden.add("class " + valueClass);
        }
        for (final String difference : comparator.differences()) {
            golden.add("lossy " + difference);
        }
        final StoredRows stored = readRows(dataSource, vendor, storedTags).minus(before);
        golden.addAll(stored.render());
        if (vendor == DatabaseVendor.POSTGRESQL) {
            VendorDifferences.checkPostgresqlLobs(
                    persistenceUnit, stored.oidLobColumns(), failures);
        }
        if (!failures.isEmpty()) {
            final String message =
                    failures.size()
                            + " problems storing or reading back entities on "
                            + vendor.id()
                            + " ("
                            + persistenceUnit
                            + ", JVM zone "
                            + zone
                            + ")\n"
                            + String.join("\n", failures);
            GoldenFiles.jobSummary(message);
            fail(message);
        }
        GoldenFiles.verify(
                goldenName,
                golden,
                vendor,
                "JVM zone " + zone,
                VendorDifferences.goldenLines(vendor));
    }

    private static StoredRows readRows(
            DataSource dataSource, DatabaseVendor vendor, Map<String, Map<String, String>> tags) {
        try {
            return StoredRows.read(dataSource, vendor, tags);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the stored rows", e);
        }
    }

    /**
     * The id to find the stored entity by. A composite id is copied with its entity references read
     * in {@code reader}: the id object of the stored entity still points at the instances that were
     * built, and finding by it would put those (not the database's values) into the reloaded
     * entity. String ids and id fields are stored as {@link VendorDifferences#storedId} says.
     */
    private static Object detachedId(
            EntityManager reader,
            Metamodel metamodel,
            EntityManagerFactory emf,
            DatabaseVendor vendor,
            Object id) {
        if (metamodel.getEmbeddables().stream().noneMatch(e -> e.getJavaType() == id.getClass())) {
            return VendorDifferences.storedId(vendor, id);
        }
        final Object copy = EntityGraphBuilder.instantiate(id.getClass());
        for (final Attribute<?, ?> attribute :
                metamodel.embeddable(id.getClass()).getAttributes()) {
            final Field field = EntityGraphBuilder.field(attribute);
            Object value = EntityGraphBuilder.get(field, id);
            if (attribute.isAssociation() && value != null) {
                value =
                        reader.find(
                                value.getClass(),
                                emf.getPersistenceUnitUtil().getIdentifier(value));
            } else {
                value = VendorDifferences.storedId(vendor, value);
            }
            EntityGraphBuilder.set(field, copy, value);
        }
        return copy;
    }

    /**
     * For an entity that only exists in an owner's collection (a unidirectional one-to-many with a
     * non-null join column), the owner type and that collection; otherwise null.
     */
    static Map.Entry<Class<?>, PluralAttribute<?, ?, ?>> ownerOf(
            Metamodel metamodel, Class<?> type) {
        for (final EntityType<?> ownerType : PersistenceUnits.entityTypes(metamodel)) {
            if (Modifier.isAbstract(ownerType.getJavaType().getModifiers())) {
                continue;
            }
            for (final Attribute<?, ?> attribute :
                    EntityGraphBuilder.sorted(ownerType.getAttributes())) {
                if (attribute.getPersistentAttributeType()
                        != Attribute.PersistentAttributeType.ONE_TO_MANY) {
                    continue;
                }
                final PluralAttribute<?, ?, ?> plural = (PluralAttribute<?, ?, ?>) attribute;
                final Field field = EntityGraphBuilder.field(attribute);
                final OneToMany oneToMany = field.getAnnotation(OneToMany.class);
                final JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (oneToMany != null
                        && oneToMany.mappedBy().isEmpty()
                        && joinColumn != null
                        && !joinColumn.nullable()
                        && plural.getElementType().getJavaType().isAssignableFrom(type)) {
                    return new AbstractMap.SimpleImmutableEntry<>(ownerType.getJavaType(), plural);
                }
            }
        }
        return null;
    }

    static String rootCause(Throwable e) {
        Throwable cause = e;
        final StringBuilder chain = new StringBuilder();
        while (cause != null) {
            if (chain.length() > 0) {
                chain.append(" <- ");
            }
            chain.append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        final String text = chain.toString().replace('\n', ' ');
        return text.length() > 600 ? text.substring(text.length() - 600) : text;
    }
}
