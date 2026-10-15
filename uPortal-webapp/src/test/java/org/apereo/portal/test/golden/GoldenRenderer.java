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
package org.apereo.portal.test.golden;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;
import org.hibernate.proxy.HibernateProxy;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * Renders query results and entities as stable, vendor-neutral text for the goldens: basic
 * attributes in name order, generated ids and versions left out, associations by their natural id
 * (or their basic attributes when they have none), collections sorted unless the mapping orders
 * them (an order column or {@code @OrderBy}), instants in UTC, and floating point numbers to 9
 * significant digits (a stored double may come back a few units of the last place off on some
 * databases; real changes are far larger).
 */
public final class GoldenRenderer {
    private static final MathContext DOUBLE_PRECISION = new MathContext(9);
    private static final DateTimeFormatter JODA_INSTANT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter JODA_LOCAL_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Metamodel metamodel;
    private final java.util.Set<String> masked;

    public GoldenRenderer(Metamodel metamodel) {
        this(metamodel, java.util.Collections.<String>emptySet());
    }

    /**
     * @param masked "Type.attribute" names whose values depend on when or where the test runs
     *     (creation times set to "now", random tokens); they are rendered as {@code *} when set
     */
    public GoldenRenderer(Metamodel metamodel, java.util.Set<String> masked) {
        this.metamodel = metamodel;
        this.masked = masked;
    }

    /** An entity or embeddable with its attributes; anything else as a value */
    public String render(Object value) {
        return render(value, 0);
    }

    private String render(Object value, int depth) {
        value = unproxy(value);
        if (value == null) {
            return "null";
        }
        final ManagedType<?> type = managedType(value.getClass());
        if (type == null) {
            return renderValue(value);
        }
        final StringBuilder result =
                new StringBuilder(value.getClass().getSimpleName()).append('{');
        boolean first = true;
        final boolean shortForm = depth > 0 && hasNaturalId(type);
        for (final Attribute<?, ?> attribute : sorted(type)) {
            final Field field = field(attribute);
            if (field == null || skipped(attribute, field)) {
                continue;
            }
            if (shortForm && !isNaturalId(field)) {
                continue;
            }
            final Object attributeValue = get(field, value);
            final String rendered;
            if (masked.contains(value.getClass().getSimpleName() + "." + attribute.getName())) {
                if (!first) {
                    result.append(", ");
                }
                first = false;
                result.append(attribute.getName())
                        .append('=')
                        .append(attributeValue == null ? "null" : "*");
                continue;
            }
            switch (attribute.getPersistentAttributeType()) {
                case BASIC:
                case EMBEDDED:
                    rendered = render(attributeValue, depth + 1);
                    break;
                case MANY_TO_ONE:
                case ONE_TO_ONE:
                    rendered =
                            depth >= 2
                                    ? describe(attributeValue)
                                    : render(attributeValue, depth + 1);
                    break;
                default:
                    if (depth >= 1) {
                        // Collections of nested entities: only their size
                        rendered = attributeValue == null ? "null" : "size " + size(attributeValue);
                    } else {
                        rendered = renderCollection(attributeValue, field, depth + 1);
                    }
            }
            if (!first) {
                result.append(", ");
            }
            first = false;
            result.append(attribute.getName()).append('=').append(rendered);
        }
        return result.append('}').toString();
    }

    private String renderCollection(Object collection, Field field, int depth) {
        if (collection == null) {
            return "null";
        }
        final List<String> elements = new ArrayList<>();
        if (collection instanceof Map) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) collection).entrySet()) {
                elements.add(
                        render(entry.getKey(), depth) + "->" + render(entry.getValue(), depth));
            }
            elements.sort(Comparator.naturalOrder());
        } else {
            for (final Object element : (Collection<?>) collection) {
                elements.add(render(element, depth));
            }
            // A bag (a list without an order column or @OrderBy) comes back in whatever order the
            // database returns its rows
            if (!isOrdered(field)) {
                elements.sort(Comparator.naturalOrder());
            }
        }
        return elements.toString();
    }

    /**
     * Whether a collection attribute has a defined order: a list with an index column or an
     * {@code @OrderBy}. Any other collection (a bag) comes back in whatever order the database
     * returns its rows. Annotations are matched by name, so this works with the Hibernate 4.3
     * {@code @IndexColumn} and with JPA's {@code @OrderColumn}.
     */
    public static boolean isOrdered(Field field) {
        if (!List.class.isAssignableFrom(field.getType())) {
            return false;
        }
        for (final Annotation annotation : field.getAnnotations()) {
            final String name = annotation.annotationType().getName();
            if (name.equals("javax.persistence.OrderColumn")
                    || name.equals("javax.persistence.OrderBy")
                    || name.equals("org.hibernate.annotations.IndexColumn")) {
                return true;
            }
        }
        return false;
    }

    private static int size(Object collection) {
        return collection instanceof Map
                ? ((Map<?, ?>) collection).size()
                : ((Collection<?>) collection).size();
    }

    private String describe(Object entity) {
        entity = unproxy(entity);
        return entity == null ? "null" : entity.getClass().getSimpleName();
    }

    /** A basic value */
    public static String renderValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return '"' + escape((String) value) + '"';
        }
        if (value instanceof Double || value instanceof Float) {
            return renderDouble(((Number) value).doubleValue());
        }
        if (value instanceof DateTime) {
            return ((DateTime) value).withZone(DateTimeZone.UTC).toString();
        }
        // The goldens were recorded from Joda-Time values: an instant in UTC with milliseconds,
        // and a time of day with seconds and milliseconds
        if (value instanceof Instant) {
            return JODA_INSTANT.format((Instant) value);
        }
        if (value instanceof ZonedDateTime) {
            return JODA_INSTANT.format(((ZonedDateTime) value).toInstant());
        }
        if (value instanceof LocalTime) {
            return JODA_LOCAL_TIME.format((LocalTime) value);
        }
        if (value instanceof Date) {
            return java.time.Instant.ofEpochMilli(((Date) value).getTime()).toString();
        }
        if (value instanceof Calendar) {
            return java.time.Instant.ofEpochMilli(((Calendar) value).getTimeInMillis()).toString();
        }
        if (value instanceof Class) {
            return ((Class<?>) value).getName();
        }
        if (value instanceof Locale) {
            return ((Locale) value).toLanguageTag();
        }
        if (value instanceof Collection || value instanceof Map) {
            return String.valueOf(value);
        }
        return escape(String.valueOf(value));
    }

    public static String renderDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == 0) {
            return "0";
        }
        return new BigDecimal(value).round(DOUBLE_PRECISION).stripTrailingZeros().toString();
    }

    /** Non-printable and non-ASCII characters as \\uXXXX, so goldens stay plain ASCII */
    public static String escape(String value) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e || c == '"' || c == '\\') {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /** The inverse of {@link #escape} */
    public static String unescape(String escaped) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < escaped.length(); i++) {
            if (escaped.startsWith("\\u", i) && i + 6 <= escaped.length()) {
                result.append((char) Integer.parseInt(escaped.substring(i + 2, i + 6), 16));
                i += 5;
            } else {
                result.append(escaped.charAt(i));
            }
        }
        return result.toString();
    }

    private ManagedType<?> managedType(Class<?> type) {
        for (final ManagedType<?> managedType : metamodel.getManagedTypes()) {
            if (managedType.getJavaType() == type) {
                return managedType;
            }
        }
        return null;
    }

    private static List<Attribute<?, ?>> sorted(ManagedType<?> type) {
        return type.getAttributes().stream()
                .sorted(Comparator.comparing(Attribute::getName))
                .collect(Collectors.toList());
    }

    private boolean hasNaturalId(ManagedType<?> type) {
        for (final Attribute<?, ?> attribute : type.getAttributes()) {
            final Field field = field(attribute);
            if (field != null && isNaturalId(field)) {
                return true;
            }
        }
        return false;
    }

    /** Generated ids, optimistic-lock versions and inverse collections are not rendered */
    private static boolean skipped(Attribute<?, ?> attribute, Field field) {
        if (attribute instanceof SingularAttribute) {
            final SingularAttribute<?, ?> singular = (SingularAttribute<?, ?>) attribute;
            if (singular.isVersion()) {
                return true;
            }
            if (singular.isId()
                    && field.isAnnotationPresent(javax.persistence.GeneratedValue.class)) {
                return true;
            }
        }
        final javax.persistence.OneToMany oneToMany =
                field.getAnnotation(javax.persistence.OneToMany.class);
        return oneToMany != null && !oneToMany.mappedBy().isEmpty();
    }

    static boolean isNaturalId(Field field) {
        for (final Annotation annotation : field.getAnnotations()) {
            if ("org.hibernate.annotations.NaturalId"
                    .equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static Field field(Attribute<?, ?> attribute) {
        final java.lang.reflect.Member member = attribute.getJavaMember();
        Field field = null;
        if (member instanceof Field) {
            field = (Field) member;
        } else if (member instanceof Method) {
            for (Class<?> c = member.getDeclaringClass();
                    c != null && field == null;
                    c = c.getSuperclass()) {
                try {
                    field = c.getDeclaredField(attribute.getName());
                } catch (NoSuchFieldException e) {
                    // look in the superclass
                }
            }
        }
        if (field != null) {
            field.setAccessible(true);
        }
        return field;
    }

    private static Object get(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Object unproxy(Object value) {
        if (value instanceof HibernateProxy) {
            return ((HibernateProxy) value).getHibernateLazyInitializer().getImplementation();
        }
        return value;
    }
}
