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

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;

/** Helpers over a persistence unit for the golden tests, using only the JPA API */
public final class PersistenceUnits {
    private PersistenceUnits() {}

    /** The unit's entity types, by class name */
    public static List<EntityType<?>> entityTypes(Metamodel metamodel) {
        return metamodel.getEntities().stream()
                .sorted(Comparator.comparing(e -> e.getJavaType().getName()))
                .collect(Collectors.toList());
    }

    /** Deletes every entity of the unit, as {@code BaseJpaDaoTest} does after each test */
    public static void deleteAll(EntityManagerFactory emf) {
        deleteAll(emf, type -> true);
    }

    /**
     * Deletes the entities of the matching types with {@code EntityManager#remove} (so cascades and
     * collection tables are handled), retrying the types that are still referenced. When a pass
     * deletes no type, fails with each type left and the root cause of its last failure.
     *
     * <p>An element collection row that holds NULL (Oracle stores '' as NULL) is deleted with its
     * owner by {@code ElementCollectionDeleteIntegrator} (MOD-I144), as in the portal.
     */
    public static void deleteAll(EntityManagerFactory emf, Predicate<Class<?>> types) {
        final Set<EntityType<?>> selected = selected(emf, types);
        final Map<String, String> left = remove(emf, selected);
        if (!left.isEmpty()) {
            final StringBuilder message = new StringBuilder("Cannot delete:");
            for (final Map.Entry<String, String> type : left.entrySet()) {
                message.append("\n  ").append(type.getKey()).append(": ").append(type.getValue());
            }
            throw new IllegalStateException(message.toString());
        }
    }

    /**
     * Removes every entity of the unit with {@code EntityManager#remove} only, as the portal's own
     * code would, retrying the types that are still referenced.
     *
     * @return the types it could not remove (simple class name), each with the root cause of its
     *     last failure; empty when all were removed
     */
    public static Map<String, String> removeAll(EntityManagerFactory emf) {
        return remove(emf, selected(emf, type -> true));
    }

    private static Set<EntityType<?>> selected(
            EntityManagerFactory emf, Predicate<Class<?>> types) {
        final Set<EntityType<?>> selected = new LinkedHashSet<>();
        for (final EntityType<?> entityType : entityTypes(emf.getMetamodel())) {
            if (types.test(entityType.getJavaType())) {
                selected.add(entityType);
            }
        }
        return selected;
    }

    private static Map<String, String> remove(EntityManagerFactory emf, Set<EntityType<?>> types) {
        Set<EntityType<?>> remaining = types;
        final Map<String, String> causes = new TreeMap<>();
        while (!remaining.isEmpty()) {
            final Set<EntityType<?>> failed = new LinkedHashSet<>();
            for (final EntityType<?> entityType : remaining) {
                final EntityManager em = emf.createEntityManager();
                try {
                    em.getTransaction().begin();
                    for (final Object entity :
                            em.createQuery("SELECT e FROM " + entityType.getName() + " e")
                                    .getResultList()) {
                        em.remove(entity);
                    }
                    em.getTransaction().commit();
                } catch (RuntimeException e) {
                    failed.add(entityType);
                    causes.put(entityType.getJavaType().getSimpleName(), rootCause(e));
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                } finally {
                    em.close();
                }
            }
            if (failed.size() == remaining.size()) {
                final Map<String, String> left = new TreeMap<>();
                for (final EntityType<?> entityType : failed) {
                    final String name = entityType.getJavaType().getSimpleName();
                    left.put(name, causes.get(name));
                }
                return left;
            }
            remaining = failed;
        }
        return new TreeMap<>();
    }

    private static String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
