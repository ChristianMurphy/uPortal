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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.MapKeyColumn;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * One mapped value: a basic attribute, a collection element or a map key, with what its mapping
 * says about the values it can hold. Read from the JPA annotations (and the name of uPortal's
 * Hibernate user type), so it does not depend on Hibernate's internal metadata API.
 */
final class Slot {
    /** JPA's default {@code @Column(length)} */
    static final int DEFAULT_LENGTH = 255;

    final String path;
    final Class<?> type;
    final Type genericType;
    final int length;
    final boolean explicitLength;
    final boolean nullable;
    final boolean unique;
    final boolean lob;
    final TemporalType temporal;
    final EnumType enumType;

    /** The {@code @Type(type = ...)} name, e.g. "fname", "dateTime", "nullSafeString", or null */
    final String userType;

    /** Position of the slot among the entity's slots of the same Java type */
    final int ordinal;

    private Slot(
            String path,
            Class<?> type,
            Type genericType,
            int length,
            boolean explicitLength,
            boolean nullable,
            boolean unique,
            boolean lob,
            TemporalType temporal,
            EnumType enumType,
            String userType,
            int ordinal) {
        this.path = path;
        this.type = type;
        this.genericType = genericType;
        this.length = length;
        this.explicitLength = explicitLength;
        this.nullable = nullable;
        this.unique = unique;
        this.lob = lob;
        this.temporal = temporal;
        this.enumType = enumType;
        this.userType = userType;
        this.ordinal = ordinal;
    }

    /** A basic attribute */
    static Slot ofField(String path, Field field, boolean naturalId, int ordinal) {
        final Column column = field.getAnnotation(Column.class);
        return new Slot(
                path,
                field.getType(),
                field.getGenericType(),
                column == null ? DEFAULT_LENGTH : column.length(),
                column != null && column.length() != DEFAULT_LENGTH,
                (column == null || column.nullable())
                        && !field.getType().isPrimitive()
                        && !naturalId,
                naturalId || (column != null && column.unique()),
                field.isAnnotationPresent(Lob.class),
                temporal(field),
                enumType(field),
                userType(field),
                ordinal);
    }

    /** The elements of an element collection */
    static Slot ofElements(String path, Field field, Class<?> elementType, int ordinal) {
        final Column column = field.getAnnotation(Column.class);
        return new Slot(
                path,
                elementType,
                elementType,
                column == null ? DEFAULT_LENGTH : column.length(),
                column != null && column.length() != DEFAULT_LENGTH,
                column == null || column.nullable(),
                false,
                field.isAnnotationPresent(Lob.class),
                temporal(field),
                enumType(field),
                userType(field),
                ordinal);
    }

    /**
     * The keys of a map. Hibernate 4.3 applies the collection's {@code @Type} to the keys as well
     * as the values (the "only applies to map values" comments in the entities are wrong for 4.3),
     * so the keys of a nullSafeString map are stored with the '_' prefix too.
     */
    static Slot ofKeys(String path, Field field, Class<?> keyType) {
        final MapKeyColumn column = field.getAnnotation(MapKeyColumn.class);
        return new Slot(
                path,
                keyType,
                keyType,
                column == null ? DEFAULT_LENGTH : column.length(),
                column != null,
                false,
                true,
                false,
                null,
                null,
                keyType == String.class ? userType(field) : null,
                0);
    }

    Slot withType(Class<?> newType) {
        return new Slot(
                path,
                newType,
                newType,
                length,
                explicitLength,
                nullable,
                unique,
                lob,
                temporal,
                enumType,
                userType,
                ordinal);
    }

    boolean isFname() {
        return "fname".equals(userType);
    }

    /** The upper bound of a {@code Class<? extends X>} field, or Object */
    Class<?> classBound() {
        if (genericType instanceof ParameterizedType) {
            final Type argument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            if (argument instanceof Class) {
                return (Class<?>) argument;
            }
            if (argument instanceof WildcardType) {
                final Type upper = ((WildcardType) argument).getUpperBounds()[0];
                if (upper instanceof Class) {
                    return (Class<?>) upper;
                }
                if (upper instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) upper).getRawType();
                }
            }
            if (argument instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) argument).getRawType();
            }
        }
        return Object.class;
    }

    private static TemporalType temporal(Field field) {
        final Temporal temporal = field.getAnnotation(Temporal.class);
        return temporal == null ? null : temporal.value();
    }

    private static EnumType enumType(Field field) {
        final Enumerated enumerated = field.getAnnotation(Enumerated.class);
        return enumerated == null ? EnumType.ORDINAL : enumerated.value();
    }

    /**
     * The Hibernate {@code @Type} name. Read reflectively: Hibernate 6 replaced {@code type} with a
     * class-valued {@code value}, and this keeps the test source independent of either.
     */
    private static String userType(Field field) {
        for (final Annotation annotation : field.getAnnotations()) {
            if ("org.hibernate.annotations.Type".equals(annotation.annotationType().getName())) {
                for (final String attribute : new String[] {"type", "value"}) {
                    try {
                        final Method method = annotation.annotationType().getMethod(attribute);
                        final Object value = method.invoke(annotation);
                        if (value instanceof String) {
                            return (String) value;
                        }
                        if (value instanceof Class) {
                            return ((Class<?>) value).getName();
                        }
                    } catch (ReflectiveOperationException e) {
                        // try the next attribute name
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return path;
    }
}
