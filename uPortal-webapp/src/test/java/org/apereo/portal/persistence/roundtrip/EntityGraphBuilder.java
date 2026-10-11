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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapKey;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type.PersistenceType;

/**
 * Builds a fully populated instance of any mapped entity, generically from the JPA metamodel: every
 * basic attribute gets an edge value for the {@link Variant} ({@link EdgeValues}), embedded values
 * are filled recursively, collections get elements, owned (cascaded) associations are built with
 * the same variant, and required associations to independent entities are built with {@link
 * Variant#TYPICAL} values and persisted first. Fields are set directly, as Hibernate does when it
 * loads an entity, so constructors and setters cannot hide a value from the round trip.
 */
final class EntityGraphBuilder {
    private static final int MAX_DEPTH = 4;

    private final EntityManager entityManager;
    private final Metamodel metamodel;
    private final EdgeValues edgeValues;
    private int nextSeed;

    EntityGraphBuilder(
            EntityManager entityManager,
            Metamodel metamodel,
            EdgeValues edgeValues,
            int firstSeed) {
        this.entityManager = entityManager;
        this.metamodel = metamodel;
        this.edgeValues = edgeValues;
        this.nextSeed = firstSeed;
    }

    int nextSeed() {
        return nextSeed++;
    }

    /** A new, populated, not yet persisted instance; prerequisites are already persisted */
    <T> T buildRoot(Class<T> type, Variant variant) {
        return type.cast(build(type, variant, nextSeed(), null, 0));
    }

    private Object build(Class<?> type, Variant variant, int seed, Object parent, int depth) {
        final Class<?> concrete = concrete(type, seed);
        final Object instance = instantiate(concrete);
        final ManagedType<?> managedType = metamodel.managedType(concrete);
        fill(instance, managedType, variant, seed, parent, depth, false);
        return instance;
    }

    /**
     * An entity that only exists in the collection of an owner (a unidirectional one-to-many with a
     * non-null join column): returns a new owner, built with {@link Variant#TYPICAL} values, whose
     * collection holds one {@code childType} built for the variant.
     */
    Object buildOwnerOf(
            Class<?> ownerType,
            PluralAttribute<?, ?, ?> collection,
            Class<?> childType,
            Variant variant) {
        final Object owner = build(ownerType, Variant.TYPICAL, nextSeed(), null, 0);
        final Object child = build(childType, variant, nextSeed(), owner, 1);
        final Field field = field(collection);
        if (collection instanceof MapAttribute) {
            final MapKey mapKey = field.getAnnotation(MapKey.class);
            final Map<Object, Object> map = newMap(field.getType());
            map.put(get(fieldNamed(child.getClass(), mapKey.name()), child), child);
            set(field, owner, map);
        } else {
            final Collection<Object> elements = newCollection(field.getType());
            elements.add(child);
            set(field, owner, elements);
        }
        return owner;
    }

    private void fill(
            Object instance,
            ManagedType<?> managedType,
            Variant variant,
            int seed,
            Object parent,
            int depth,
            boolean idPart) {
        final Map<Class<?>, Integer> ordinals = new HashMap<>();
        final String typeName = managedType.getJavaType().getSimpleName();
        for (final Attribute<?, ?> attribute : sorted(managedType.getAttributes())) {
            final Field field = field(attribute);
            final String path = typeName + "." + attribute.getName();
            switch (attribute.getPersistentAttributeType()) {
                case BASIC:
                    {
                        final SingularAttribute<?, ?> singular =
                                (SingularAttribute<?, ?>) attribute;
                        if (singular.isVersion()
                                || (singular.isId()
                                        && field.isAnnotationPresent(GeneratedValue.class))) {
                            continue;
                        }
                        final int ordinal = ordinals.merge(field.getType(), 1, Integer::sum) - 1;
                        final Slot slot =
                                Slot.ofField(
                                        path,
                                        field,
                                        idPart || isNaturalId(field) || singular.isId(),
                                        ordinal);
                        set(field, instance, edgeValues.value(slot, variant, seed));
                        break;
                    }
                case EMBEDDED:
                    {
                        final Class<?> embeddableType = field.getType();
                        final Object embedded = instantiate(embeddableType);
                        fill(
                                embedded,
                                metamodel.embeddable(embeddableType),
                                variant,
                                seed,
                                parent,
                                depth,
                                ((SingularAttribute<?, ?>) attribute).isId());
                        set(field, instance, embedded);
                        break;
                    }
                case MANY_TO_ONE:
                case ONE_TO_ONE:
                    set(
                            field,
                            instance,
                            reference(
                                    instance,
                                    (SingularAttribute<?, ?>) attribute,
                                    field,
                                    variant,
                                    parent,
                                    depth,
                                    idPart));
                    break;
                case ELEMENT_COLLECTION:
                    set(
                            field,
                            instance,
                            elementCollection(
                                    path,
                                    (PluralAttribute<?, ?, ?>) attribute,
                                    field,
                                    variant,
                                    ordinals));
                    break;
                case ONE_TO_MANY:
                case MANY_TO_MANY:
                    {
                        final Object collection =
                                entityCollection(
                                        instance,
                                        (PluralAttribute<?, ?, ?>) attribute,
                                        field,
                                        variant,
                                        depth);
                        if (collection != null) {
                            set(field, instance, collection);
                        }
                        break;
                    }
                default:
                    throw new IllegalStateException(path + ": unexpected " + attribute);
            }
        }
    }

    private Object reference(
            Object owner,
            SingularAttribute<?, ?> attribute,
            Field field,
            Variant variant,
            Object parent,
            int depth,
            boolean idPart) {
        final OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToOne != null && !oneToOne.mappedBy().isEmpty()) {
            return null;
        }
        final Class<?> target = attribute.getType().getJavaType();
        if (parent != null && target.isInstance(parent)) {
            return parent;
        }
        final JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        final ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        final boolean optional =
                (manyToOne == null || manyToOne.optional())
                        && (oneToOne == null || oneToOne.optional())
                        && (joinColumn == null || joinColumn.nullable())
                        && !isNaturalId(field)
                        && !idPart;
        if ((variant == Variant.NULLS && optional) || depth >= MAX_DEPTH) {
            if (!optional) {
                throw new IllegalStateException(
                        owner.getClass().getSimpleName()
                                + "."
                                + field.getName()
                                + ": required association deeper than "
                                + MAX_DEPTH);
            }
            return null;
        }
        final CascadeType[] cascade = manyToOne != null ? manyToOne.cascade() : oneToOne.cascade();
        if (cascades(cascade)) {
            return build(target, variant, nextSeed(), owner, depth + 1);
        }
        return prerequisite(target, depth);
    }

    /** An independent entity the instance refers to, persisted now */
    private Object prerequisite(Class<?> type, int depth) {
        final Object prerequisite = build(type, Variant.TYPICAL, nextSeed(), null, depth + 1);
        entityManager.persist(prerequisite);
        return prerequisite;
    }

    private Object elementCollection(
            String path,
            PluralAttribute<?, ?, ?> attribute,
            Field field,
            Variant variant,
            Map<Class<?>, Integer> ordinals) {
        final Class<?> elementType = attribute.getElementType().getJavaType();
        final boolean embeddable =
                attribute.getElementType().getPersistenceType() == PersistenceType.EMBEDDABLE;
        final int ordinal = ordinals.merge(elementType, 1, Integer::sum) - 1;
        final Slot elementSlot = Slot.ofElements(path + "[]", field, elementType, ordinal);
        int size = size(variant);
        final List<Object> elements = new ArrayList<>();
        if (size == 0
                && variant == Variant.NULLS
                && attribute.getCollectionType() == PluralAttribute.CollectionType.LIST
                && elementSlot.nullable
                && !embeddable) {
            // A list may hold a null element
            elements.add(null);
        }
        if (elementType.isEnum()) {
            final Object[] constants = elementType.getEnumConstants();
            size = Math.min(size, constants.length);
            final int offset = variant == Variant.MAX ? constants.length - size : 0;
            for (int i = 0; i < size; i++) {
                elements.add(constants[offset + i]);
            }
        } else {
            for (int i = 0; i < size; i++) {
                final int seed = nextSeed();
                if (embeddable) {
                    final Object embedded = instantiate(elementType);
                    fill(
                            embedded,
                            metamodel.embeddable(elementType),
                            variant,
                            seed,
                            null,
                            MAX_DEPTH,
                            false);
                    elements.add(embedded);
                } else {
                    elements.add(edgeValues.value(elementSlot, variant, seed));
                }
            }
        }
        if (attribute instanceof MapAttribute) {
            final Class<?> keyType = ((MapAttribute<?, ?, ?>) attribute).getKeyJavaType();
            final Map<Object, Object> map = newMap(field.getType());
            for (final Object element : elements) {
                if (element == null) {
                    continue;
                }
                final Object key;
                if (metamodel.getEntities().stream().anyMatch(e -> e.getJavaType() == keyType)) {
                    key = prerequisite(keyType, 0);
                } else {
                    key =
                            edgeValues.value(
                                    Slot.ofKeys(path + "{}", field, keyType), variant, nextSeed());
                }
                map.put(key, element);
            }
            return map;
        }
        final Collection<Object> collection = newCollection(field.getType());
        collection.addAll(elements);
        return collection;
    }

    private Object entityCollection(
            Object owner,
            PluralAttribute<?, ?, ?> attribute,
            Field field,
            Variant variant,
            int depth) {
        final OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        final ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
        final String mappedBy = oneToMany != null ? oneToMany.mappedBy() : manyToMany.mappedBy();
        final CascadeType[] cascade =
                oneToMany != null ? oneToMany.cascade() : manyToMany.cascade();
        final boolean owned = cascades(cascade);
        final Class<?> elementType = attribute.getElementType().getJavaType();
        if (!mappedBy.isEmpty() && (!owned || depth > 0)) {
            // The other side owns the relationship; filled only for the entity under test
            return null;
        }
        final boolean selfReference = elementType.isAssignableFrom(owner.getClass());
        final int size =
                depth >= MAX_DEPTH - 1 || (selfReference && depth > 0)
                        ? 0
                        : Math.min(size(variant), mappedBy.isEmpty() ? 3 : 2);
        final List<Object> elements = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (owned) {
                elements.add(build(elementType, variant, nextSeed(), owner, depth + 1));
            } else {
                elements.add(prerequisite(elementType, depth));
            }
        }
        if (attribute instanceof MapAttribute) {
            final MapKey mapKey = field.getAnnotation(MapKey.class);
            if (mapKey == null || mapKey.name().isEmpty()) {
                throw new IllegalStateException(
                        owner.getClass().getSimpleName()
                                + "."
                                + field.getName()
                                + ": only @MapKey(name) entity maps are supported");
            }
            final Map<Object, Object> map = newMap(field.getType());
            for (final Object element : elements) {
                map.put(get(fieldNamed(element.getClass(), mapKey.name()), element), element);
            }
            return map;
        }
        final Collection<Object> collection = newCollection(field.getType());
        collection.addAll(elements);
        return collection;
    }

    private static int size(Variant variant) {
        switch (variant) {
            case MAX:
                return 3;
            case UNICODE:
                return 2;
            case NULLS:
                return 0;
            default:
                return 1;
        }
    }

    /** A concrete entity type for a possibly abstract one, rotating through the subclasses */
    private Class<?> concrete(Class<?> type, int seed) {
        if (!Modifier.isAbstract(type.getModifiers()) && !type.isInterface()) {
            return type;
        }
        final List<Class<?>> candidates =
                metamodel.getEntities().stream()
                        .map(EntityType::getJavaType)
                        .filter(
                                c ->
                                        type.isAssignableFrom(c)
                                                && !Modifier.isAbstract(c.getModifiers()))
                        .sorted(Comparator.comparing(Class::getName))
                        .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No concrete entity for " + type.getName());
        }
        return candidates.get(seed % candidates.size());
    }

    static List<Attribute<?, ?>> sorted(Set<? extends Attribute<?, ?>> attributes) {
        return attributes.stream()
                .sorted(Comparator.comparing(Attribute::getName))
                .collect(Collectors.toList());
    }

    static boolean cascades(CascadeType[] cascade) {
        return Arrays.asList(cascade).contains(CascadeType.ALL)
                || Arrays.asList(cascade).contains(CascadeType.PERSIST);
    }

    /** Hibernate's {@code @NaturalId}, matched by name so the source compiles on any version */
    static boolean isNaturalId(Field field) {
        for (final Annotation annotation : field.getAnnotations()) {
            if ("org.hibernate.annotations.NaturalId"
                    .equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    static Field field(Attribute<?, ?> attribute) {
        if (!(attribute.getJavaMember() instanceof Field)) {
            if (attribute.getJavaMember() instanceof Method) {
                // property access: the field of the same name holds the state
                return fieldNamed(
                        attribute.getJavaMember().getDeclaringClass(), attribute.getName());
            }
            throw new IllegalStateException("No field for " + attribute);
        }
        final Field field = (Field) attribute.getJavaMember();
        field.setAccessible(true);
        return field;
    }

    static Field fieldNamed(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                final Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new IllegalStateException(type.getName() + " has no field " + name);
    }

    static Object get(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void set(Field field, Object instance, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Cannot set " + field + " to " + (value == null ? null : value.getClass()), e);
        }
    }

    static Object instantiate(Class<?> type) {
        try {
            final Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + type.getName(), e);
        }
    }

    private static Collection<Object> newCollection(Class<?> type) {
        if (SortedSet.class.isAssignableFrom(type)) {
            return new TreeSet<>();
        }
        if (Set.class.isAssignableFrom(type)) {
            return new LinkedHashSet<>();
        }
        return new ArrayList<>();
    }

    private static Map<Object, Object> newMap(Class<?> type) {
        if (SortedMap.class.isAssignableFrom(type)) {
            return new TreeMap<>();
        }
        return new LinkedHashMap<>();
    }
}
