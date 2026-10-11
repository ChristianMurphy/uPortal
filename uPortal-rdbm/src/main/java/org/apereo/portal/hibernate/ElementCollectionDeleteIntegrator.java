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
package org.apereo.portal.hibernate;

import org.hibernate.boot.Metadata;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.hibernate.type.CollectionType;
import org.hibernate.type.Type;

/**
 * Deletes the rows of an entity's element collections that Hibernate loaded as empty, before the
 * entity's own row is deleted (MOD-I144).
 *
 * <p>Hibernate skips an element or map entry whose value is NULL when it loads a collection, and
 * when it deletes the owner it only deletes the collection's rows if the loaded collection was not
 * empty. Oracle stores '' as NULL, so an owner whose element collection holds only '' values (a
 * layout attribute's target element names, a portlet entity's window states, a portlet definition's
 * empty localizations) kept its collection rows, and deleting the owner failed with ORA-02292. This
 * listener deletes all rows of each loaded, empty element collection by its owner's id. Collections
 * that were not loaded, or not empty, are deleted by Hibernate as before.
 *
 * <p>Configured via the {@link java.util.ServiceLoader} mechanism.
 */
public class ElementCollectionDeleteIntegrator implements Integrator {

    @Override
    public void integrate(
            Metadata metadata,
            SessionFactoryImplementor sessionFactory,
            SessionFactoryServiceRegistry serviceRegistry) {
        serviceRegistry
                .getService(EventListenerRegistry.class)
                .appendListeners(EventType.PRE_DELETE, new EmptyElementCollectionDeleter());
    }

    @Override
    public void disintegrate(
            SessionFactoryImplementor sessionFactory,
            SessionFactoryServiceRegistry serviceRegistry) {
        // nothing to release
    }

    static final class EmptyElementCollectionDeleter implements PreDeleteEventListener {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean onPreDelete(PreDeleteEvent event) {
            final EntityPersister persister = event.getPersister();
            final Type[] types = persister.getPropertyTypes();
            for (int i = 0; i < types.length; i++) {
                if (!types[i].isCollectionType()) {
                    continue;
                }
                final CollectionPersister collectionPersister =
                        persister
                                .getFactory()
                                .getMetamodel()
                                .collectionPersister(((CollectionType) types[i]).getRole());
                if (collectionPersister.isOneToMany()
                        || collectionPersister.getElementType().isEntityType()) {
                    continue;
                }
                final Object value = persister.getPropertyValue(event.getEntity(), i);
                if (value instanceof PersistentCollection
                        && ((PersistentCollection) value).wasInitialized()
                        && ((PersistentCollection) value).empty()) {
                    collectionPersister.remove(event.getId(), event.getSession());
                }
            }
            return false;
        }
    }
}
