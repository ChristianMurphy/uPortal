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

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type.PersistenceType;
import org.apereo.portal.test.golden.DatabaseVendor;
import org.apereo.portal.test.golden.GoldenRenderer;
import org.joda.time.DateTime;

/**
 * Compares an entity graph as built with the graph read back in a new persistence context,
 * attribute by attribute from the JPA metamodel: basic values exactly (after the documented {@link
 * VendorDifferences} of the database), embedded values and owned associations recursively, other
 * associations by identifier, and collections by content (lists in order, sets and maps by
 * element). It also records the Java class of every value read back (e.g. {@code
 * java.sql.Timestamp} for a {@code java.util.Date} attribute), since Hibernate upgrades change
 * those too.
 */
final class EntityGraphComparator {
    private final Metamodel metamodel;
    private final PersistenceUnitUtil persistenceUnitUtil;
    private final DatabaseVendor vendor;
    private final List<String> differences = new ArrayList<>();
    private final SortedSet<String> valueClasses = new TreeSet<>();
    private final Map<Object, Object> compared = new IdentityHashMap<>();

    EntityGraphComparator(
            Metamodel metamodel, PersistenceUnitUtil persistenceUnitUtil, DatabaseVendor vendor) {
        this.metamodel = metamodel;
        this.persistenceUnitUtil = persistenceUnitUtil;
        this.vendor = vendor;
    }

    List<String> differences() {
        return differences;
    }

    /** "Type.attribute: java.class" for every non-null basic value read back */
    SortedSet<String> valueClasses() {
        return valueClasses;
    }

    void compare(String label, Object expected, Object actual) {
        compareManaged(label, expected, Proxies.unproxy(actual), 0);
    }

    private void compareManaged(String path, Object expected, Object actual, int depth) {
        if (expected == null || actual == null) {
            if (expected != actual) {
                differences.add(
                        path + ": expected " + render(expected) + " but was " + render(actual));
            }
            return;
        }
        if (compared.put(expected, actual) != null) {
            return;
        }
        if (expected.getClass() != actual.getClass()) {
            differences.add(
                    path
                            + ": expected a "
                            + expected.getClass().getName()
                            + " but was a "
                            + actual.getClass().getName());
            return;
        }
        final ManagedType<?> type = metamodel.managedType(expected.getClass());
        final String typeName = expected.getClass().getSimpleName();
        final Map<Class<?>, Integer> ordinals = new HashMap<>();
        for (final Attribute<?, ?> attribute : EntityGraphBuilder.sorted(type.getAttributes())) {
            final Field field = EntityGraphBuilder.field(attribute);
            final String attributePath = path + "." + attribute.getName();
            final Object expectedValue = EntityGraphBuilder.get(field, expected);
            final Object actualValue = Proxies.unproxy(EntityGraphBuilder.get(field, actual));
            switch (attribute.getPersistentAttributeType()) {
                case BASIC:
                    {
                        final SingularAttribute<?, ?> singular =
                                (SingularAttribute<?, ?>) attribute;
                        final int ordinal = ordinals.merge(field.getType(), 1, Integer::sum) - 1;
                        final Slot slot =
                                Slot.ofField(
                                        typeName + "." + attribute.getName(),
                                        field,
                                        EntityGraphBuilder.isNaturalId(field) || singular.isId(),
                                        ordinal);
                        if (actualValue != null
                                && !field.getType().isPrimitive()
                                && actualValue.getClass() != field.getType()) {
                            // e.g. java.sql.Timestamp for a java.util.Date attribute
                            valueClasses.add(
                                    slot.path
                                            + " ("
                                            + field.getType().getName()
                                            + "): "
                                            + actualValue.getClass().getName());
                        }
                        compareBasic(attributePath, slot, expectedValue, actualValue);
                        break;
                    }
                case EMBEDDED:
                    compareManaged(attributePath, expectedValue, actualValue, depth);
                    break;
                case MANY_TO_ONE:
                case ONE_TO_ONE:
                    compareReference(attributePath, field, expectedValue, actualValue, depth);
                    break;
                case ELEMENT_COLLECTION:
                    compareElements(
                            attributePath,
                            typeName + "." + attribute.getName(),
                            (PluralAttribute<?, ?, ?>) attribute,
                            field,
                            expectedValue,
                            actualValue,
                            ordinals);
                    break;
                case ONE_TO_MANY:
                case MANY_TO_MANY:
                    compareEntities(
                            attributePath,
                            (PluralAttribute<?, ?, ?>) attribute,
                            field,
                            expectedValue,
                            actualValue,
                            depth);
                    break;
                default:
                    throw new IllegalStateException(attributePath + ": unexpected " + attribute);
            }
        }
    }

    private void compareReference(
            String path, Field field, Object expected, Object actual, int depth) {
        if (expected == null || actual == null) {
            if (expected != actual) {
                differences.add(
                        path + ": expected " + describe(expected) + " but was " + describe(actual));
            }
            return;
        }
        final Object expectedId = persistenceUnitUtil.getIdentifier(expected);
        final Object actualId = persistenceUnitUtil.getIdentifier(actual);
        if (!Objects.equals(expectedId, actualId)) {
            // Generated ids differ between databases and runs, so they are not in the message
            differences.add(
                    path
                            + ": expected the entity that was stored but was another "
                            + actual.getClass().getSimpleName());
            return;
        }
        // Everything built for this graph is compared in full
        compareManaged(path, expected, actual, depth + 1);
    }

    private void compareElements(
            String path,
            String slotPath,
            PluralAttribute<?, ?, ?> attribute,
            Field field,
            Object expected,
            Object actual,
            Map<Class<?>, Integer> ordinals) {
        final Class<?> elementType = attribute.getElementType().getJavaType();
        final boolean embeddable =
                attribute.getElementType().getPersistenceType() == PersistenceType.EMBEDDABLE;
        final int ordinal = ordinals.merge(elementType, 1, Integer::sum) - 1;
        final Slot slot = Slot.ofElements(slotPath + "[]", field, elementType, ordinal);
        if (expected instanceof Map) {
            final Map<?, ?> expectedMap = (Map<?, ?>) expected;
            final Map<?, ?> actualMap = actual == null ? new HashMap<>() : (Map<?, ?>) actual;
            final Map<Object, Object> actualByKey = new HashMap<>();
            for (final Map.Entry<?, ?> entry : actualMap.entrySet()) {
                actualByKey.put(keyOf(Proxies.unproxy(entry.getKey())), entry.getValue());
            }
            final Map<Object, Object> expectedByKey = new HashMap<>();
            for (final Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (readsBackAsNull(slot, embeddable, entry.getValue())) {
                    // Hibernate does not read a map entry whose value is null (Oracle stores ''
                    // as NULL, see VendorDifferences)
                    continue;
                }
                expectedByKey.put(
                        keyOf(expectedKey(slotPath, attribute, field, entry.getKey())),
                        entry.getValue());
            }
            for (final Map.Entry<Object, Object> entry : expectedByKey.entrySet()) {
                final Object key = entry.getKey();
                // entity keys are "Type#id"; generated ids stay out of the message
                final String entryPath =
                        path + "{" + abbreviate(String.valueOf(key).replaceAll("#\\d+$", "")) + "}";
                if (!actualByKey.containsKey(key)) {
                    differences.add(entryPath + ": missing after the round trip");
                } else if (embeddable) {
                    compareManaged(entryPath, entry.getValue(), actualByKey.get(key), 0);
                } else {
                    compareBasic(entryPath, slot, entry.getValue(), actualByKey.get(key));
                }
            }
            for (final Object key : actualByKey.keySet()) {
                if (!expectedByKey.containsKey(key)) {
                    differences.add(
                            path
                                    + "{"
                                    + abbreviate(String.valueOf(key).replaceAll("#\\d+$", ""))
                                    + "}: unexpected entry");
                }
            }
            return;
        }
        final List<Object> expectedList = new ArrayList<>();
        for (final Object element : list(expected)) {
            // A set element the database turns into NULL (Oracle's '') is not read back: Hibernate
            // skips null set elements (a list keeps its null at the index)
            if (embeddable
                    || element == null
                    || isOrdered(field)
                    || VendorDifferences.expectedAfterRoundTrip(vendor, slot, element) != null) {
                expectedList.add(element);
            }
        }
        final List<Object> actualList = list(actual);
        if (isOrdered(field)) {
            if (expectedList.size() != actualList.size()) {
                differences.add(
                        path
                                + ": expected "
                                + expectedList.size()
                                + " elements but was "
                                + actualList.size()
                                + " "
                                + renderAll(actualList));
                return;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                if (embeddable) {
                    compareManaged(path + "[" + i + "]", expectedList.get(i), actualList.get(i), 0);
                } else {
                    compareBasic(
                            path + "[" + i + "]", slot, expectedList.get(i), actualList.get(i));
                }
            }
            return;
        }
        // Sets: compare the rendered (and vendor-adjusted) values
        final Set<String> expectedSet = new TreeSet<>();
        for (final Object element : expectedList) {
            expectedSet.add(
                    render(VendorDifferences.expectedAfterRoundTrip(vendor, slot, element)));
        }
        final Set<String> actualSet = new TreeSet<>();
        for (final Object element : actualList) {
            actualSet.add(render(element));
        }
        if (!expectedSet.equals(actualSet)) {
            differences.add(path + ": expected " + expectedSet + " but was " + actualSet);
        }
    }

    private void compareEntities(
            String path,
            PluralAttribute<?, ?, ?> attribute,
            Field field,
            Object expected,
            Object actual,
            int depth) {
        final List<Object> expectedList =
                expected instanceof Map
                        ? new ArrayList<>(((Map<?, ?>) expected).values())
                        : list(expected);
        final List<Object> actualList =
                actual instanceof Map
                        ? new ArrayList<>(((Map<?, ?>) actual).values())
                        : list(actual);
        final javax.persistence.OneToMany oneToMany =
                field.getAnnotation(javax.persistence.OneToMany.class);
        final javax.persistence.ManyToMany manyToMany =
                field.getAnnotation(javax.persistence.ManyToMany.class);
        final String mappedBy = oneToMany != null ? oneToMany.mappedBy() : manyToMany.mappedBy();
        final boolean owned =
                EntityGraphBuilder.cascades(
                        oneToMany != null ? oneToMany.cascade() : manyToMany.cascade());
        if (expected == null || (!mappedBy.isEmpty() && (!owned || depth > 0))) {
            // The builder leaves the inverse side of other entities' relationships alone
            return;
        }
        if (expected instanceof Map) {
            final Map<?, ?> expectedMap = (Map<?, ?>) expected;
            final Map<?, ?> actualMap = actual == null ? new HashMap<>() : (Map<?, ?>) actual;
            // Hibernate's PersistentMap#keySet() has no equals(), so compare copies
            final java.util.Set<Object> expectedKeys = new java.util.HashSet<>();
            for (final Object key : expectedMap.keySet()) {
                expectedKeys.add(expectedKey(path, attribute, field, key));
            }
            if (!expectedKeys.equals(new java.util.HashSet<>(actualMap.keySet()))) {
                differences.add(
                        path
                                + ": expected keys "
                                + renderAll(expectedKeys)
                                + " but was "
                                + renderAll(actualMap.keySet()));
            }
        }
        final Map<Object, Object> actualById = new HashMap<>();
        for (final Object element : actualList) {
            final Object unproxied = Proxies.unproxy(element);
            actualById.put(persistenceUnitUtil.getIdentifier(unproxied), unproxied);
        }
        final List<Object> expectedIds = new ArrayList<>();
        for (final Object element : expectedList) {
            expectedIds.add(persistenceUnitUtil.getIdentifier(element));
        }
        final List<Object> actualIds = new ArrayList<>();
        for (final Object element : actualList) {
            actualIds.add(persistenceUnitUtil.getIdentifier(Proxies.unproxy(element)));
        }
        final boolean ordered = isOrdered(field);
        if (!ordered) {
            checkOrderBy(path, field, actualList);
        }
        if (ordered
                ? !expectedIds.equals(actualIds)
                : !new java.util.HashSet<>(expectedIds).equals(new java.util.HashSet<>(actualIds))
                        || expectedIds.size() != actualIds.size()) {
            final List<String> expectedSymbols = symbols(expectedIds, expectedIds);
            final List<String> actualSymbols = symbols(expectedIds, actualIds);
            if (!ordered) {
                Collections.sort(expectedSymbols);
                Collections.sort(actualSymbols);
            }
            differences.add(
                    path
                            + ": expected the entities "
                            + expectedSymbols
                            + " but was "
                            + actualSymbols);
            return;
        }
        for (int i = 0; i < expectedList.size(); i++) {
            final Object element = expectedList.get(i);
            compareManaged(
                    path + "[" + i + "]",
                    element,
                    actualById.get(persistenceUnitUtil.getIdentifier(element)),
                    depth + 1);
        }
    }

    private void compareBasic(String path, Slot slot, Object expected, Object actual) {
        final Object adjusted = VendorDifferences.expectedAfterRoundTrip(vendor, slot, expected);
        if (!basicEquals(adjusted, actual)) {
            differences.add(path + ": expected " + render(adjusted) + " but was " + render(actual));
        }
    }

    /**
     * Whether the elements of a collection attribute come back in the order they were stored: a
     * list with an index column. An {@code @OrderBy} list is ordered by its key only, and elements
     * with equal keys come back in any order (Oracle returns them differently from HSQLDB), so it
     * is compared as a multiset and checked by {@link #checkOrderBy}.
     */
    static boolean isOrdered(Field field) {
        return GoldenRenderer.isOrdered(field)
                && field.getAnnotation(javax.persistence.OrderBy.class) == null;
    }

    /** Checks that a reloaded {@code @OrderBy} list is sorted by the columns it names */
    private void checkOrderBy(String path, Field field, List<Object> actualList) {
        final javax.persistence.OrderBy orderBy =
                field.getAnnotation(javax.persistence.OrderBy.class);
        if (orderBy == null || actualList.size() < 2) {
            return;
        }
        // Later items only order the ties of the first one, so the first is checked
        final String[] first = orderBy.value().split(",")[0].trim().split("\\s+");
        final boolean descending = first.length > 1 && "DESC".equalsIgnoreCase(first[1]);
        Comparable<Object> previous = null;
        for (final Object element : actualList) {
            final Object unproxied = Proxies.unproxy(element);
            final Field keyField = orderByField(unproxied.getClass(), first[0]);
            if (keyField == null) {
                differences.add(path + ": @OrderBy(\"" + orderBy.value() + "\") names no field");
                return;
            }
            @SuppressWarnings("unchecked")
            final Comparable<Object> key =
                    (Comparable<Object>) EntityGraphBuilder.get(keyField, unproxied);
            if (previous != null && key != null) {
                final int order = previous.compareTo(key);
                if (descending ? order < 0 : order > 0) {
                    differences.add(path + ": not in @OrderBy(\"" + orderBy.value() + "\") order");
                    return;
                }
            }
            previous = key;
        }
    }

    /** The field an {@code @OrderBy} item names, by property name or column name */
    private static Field orderByField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (final Field candidate : c.getDeclaredFields()) {
                final javax.persistence.Column column =
                        candidate.getAnnotation(javax.persistence.Column.class);
                if (candidate.getName().equals(name)
                        || (column != null && column.name().equalsIgnoreCase(name))) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Whether a collection value reads back as null, so Hibernate leaves its entry out: a basic
     * value the database stores as NULL, or an embeddable whose attributes all are.
     */
    private boolean readsBackAsNull(Slot slot, boolean embeddable, Object value) {
        if (value == null) {
            return true;
        }
        if (!embeddable) {
            return VendorDifferences.expectedAfterRoundTrip(vendor, slot, value) == null;
        }
        for (final Attribute<?, ?> attribute :
                metamodel.embeddable(value.getClass()).getAttributes()) {
            Object attributeValue =
                    EntityGraphBuilder.get(EntityGraphBuilder.field(attribute), value);
            if (attributeValue instanceof String) {
                attributeValue = vendor.storedString((String) attributeValue);
            }
            if (attributeValue != null) {
                return false;
            }
        }
        return true;
    }

    static boolean basicEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof DateTime && actual instanceof DateTime) {
            final DateTime e = (DateTime) expected;
            final DateTime a = (DateTime) actual;
            return e.getMillis() == a.getMillis() && e.getZone().equals(a.getZone());
        }
        if (expected instanceof Date && actual instanceof Date) {
            return ((Date) expected).getTime() == ((Date) actual).getTime();
        }
        if (expected instanceof Calendar && actual instanceof Calendar) {
            return ((Calendar) expected).getTimeInMillis() == ((Calendar) actual).getTimeInMillis();
        }
        if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
            return ((BigDecimal) expected).compareTo((BigDecimal) actual) == 0;
        }
        if (expected instanceof Double && actual instanceof Double) {
            return Double.compare((Double) expected, (Double) actual) == 0;
        }
        return expected.equals(actual);
    }

    /**
     * A map key as the database returns it: String keys get the vendor's documented changes ({@link
     * VendorDifferences#expectedAfterRoundTrip}), like any other stored string.
     */
    private Object expectedKey(
            String slotPath, PluralAttribute<?, ?, ?> attribute, Field field, Object key) {
        if (!(key instanceof String) || !(attribute instanceof MapAttribute)) {
            return key;
        }
        final Class<?> keyType = ((MapAttribute<?, ?, ?>) attribute).getKeyJavaType();
        return VendorDifferences.expectedAfterRoundTrip(
                vendor, Slot.ofKeys(slotPath + "{}", field, keyType), key);
    }

    private Object keyOf(Object key) {
        if (key != null
                && metamodel.getEntities().stream()
                        .anyMatch(e -> e.getJavaType() == key.getClass())) {
            return key.getClass().getSimpleName() + "#" + persistenceUnitUtil.getIdentifier(key);
        }
        return key == null ? null : render(key);
    }

    private static String describe(Object entity) {
        return entity == null ? "null" : "a " + entity.getClass().getSimpleName();
    }

    /**
     * Ids as e1, e2... by their position among the expected ids (x for any other), since generated
     * ids differ between databases and runs
     */
    private static List<String> symbols(List<Object> expectedIds, List<Object> ids) {
        final List<String> symbols = new ArrayList<>();
        for (final Object id : ids) {
            final int index = expectedIds.indexOf(id);
            symbols.add(index < 0 ? "x" : "e" + (index + 1));
        }
        return symbols;
    }

    private static List<Object> list(Object collection) {
        final List<Object> result = new ArrayList<>();
        if (collection != null) {
            result.addAll((Collection<?>) collection);
        }
        return result;
    }

    private static String renderAll(Collection<?> values) {
        final List<String> rendered = new ArrayList<>();
        for (final Object value : values) {
            rendered.add(render(value));
        }
        return rendered.toString();
    }

    /** A stable, short rendering for messages and goldens */
    static String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return renderString((String) value);
        }
        if (value instanceof DateTime) {
            return value.toString();
        }
        if (value instanceof Date) {
            return java.time.Instant.ofEpochMilli(((Date) value).getTime())
                    + "("
                    + value.getClass().getSimpleName()
                    + ")";
        }
        if (value instanceof Calendar) {
            return java.time.Instant.ofEpochMilli(((Calendar) value).getTimeInMillis())
                    + "(Calendar)";
        }
        if (value instanceof Class) {
            return ((Class<?>) value).getName();
        }
        if (value instanceof java.util.Locale) {
            final java.util.Locale locale = (java.util.Locale) value;
            return "Locale(" + escape(locale.toString()) + ", tag " + locale.toLanguageTag() + ")";
        }
        return String.valueOf(value);
    }

    static String renderString(String value) {
        if (value.length() <= 60) {
            return '"' + escape(value) + '"';
        }
        return '"'
                + escape(value.substring(0, 24))
                + "..\"(len="
                + value.length()
                + ", sha256="
                + sha256(value).substring(0, 12)
                + ")";
    }

    static String abbreviate(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40) + "..";
    }

    static String escape(String value) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e || c == '"' || c == '\\' || c == '|') {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    static String sha256(String value) {
        try {
            final byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (final byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
