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
     */
    public static void deleteAll(EntityManagerFactory emf, Predicate<Class<?>> types) {
        Set<EntityType<?>> remaining = new LinkedHashSet<>();
        for (final EntityType<?> entityType : entityTypes(emf.getMetamodel())) {
            if (types.test(entityType.getJavaType())) {
                remaining.add(entityType);
            }
        }
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
                    causes.put(entityType.getJavaType().getName(), rootCause(e));
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                } finally {
                    em.close();
                }
            }
            if (failed.size() == remaining.size()) {
                final StringBuilder message = new StringBuilder("Cannot delete:");
                for (final EntityType<?> entityType : failed) {
                    final String name = entityType.getJavaType().getName();
                    message.append("\n  ").append(name).append(": ").append(causes.get(name));
                }
                throw new IllegalStateException(message.toString());
            }
            remaining = failed;
        }
    }

    private static String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
