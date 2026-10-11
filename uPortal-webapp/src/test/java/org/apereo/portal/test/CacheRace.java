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
package org.apereo.portal.test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreLoadEvent;
import org.hibernate.event.spi.PreLoadEventListener;

/**
 * Runs a load and a write in the order that leaves a stale second-level cache entry, if the
 * entity's cache strategy allows it (MOD-I204, MOD-I310 to MOD-I319).
 *
 * <p>Hibernate reads an entity's row, calls the pre-load listeners, then puts the loaded state into
 * the second-level cache. {@link #race} starts the reader in a new thread and pauses it at its
 * first pre-load of the given entity class, after the read and before the put. The writer then runs
 * and commits in the calling thread, and the reader goes on. A strategy that checks timestamps or
 * versions (READ_WRITE) rejects the reader's put; NONSTRICT_READ_WRITE accepts it, and the cache
 * keeps the state from before the write.
 */
public final class CacheRace {

    private static final long WAIT_SECONDS = 30;

    private static final PauseAtPreLoad PAUSE = new PauseAtPreLoad();

    private CacheRace() {}

    private static final class PauseAtPreLoad implements PreLoadEventListener {
        private static final long serialVersionUID = 1L;

        private final AtomicReference<Thread> thread = new AtomicReference<>();
        private volatile Class<?> entityClass;
        private volatile CountDownLatch read = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void pause(Thread reader, Class<?> entity) {
            read = new CountDownLatch(1);
            release = new CountDownLatch(1);
            entityClass = entity;
            thread.set(reader);
        }

        @Override
        public void onPreLoad(PreLoadEvent event) {
            if (entityClass.isInstance(event.getEntity())
                    && thread.compareAndSet(Thread.currentThread(), null)) {
                read.countDown();
                try {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void install(EntityManagerFactory emf) {
        final EventListenerRegistry registry =
                emf.unwrap(SessionFactoryImplementor.class)
                        .getServiceRegistry()
                        .getService(EventListenerRegistry.class);
        for (PreLoadEventListener listener :
                registry.getEventListenerGroup(EventType.PRE_LOAD).listeners()) {
            if (listener == PAUSE) {
                return;
            }
        }
        registry.appendListeners(EventType.PRE_LOAD, PAUSE);
    }

    /** Evicts every second-level cache region, so the next load reads the rows. */
    public static void evictAll(EntityManagerFactory emf) {
        emf.unwrap(SessionFactoryImplementor.class).getCache().evictAllRegions();
    }

    /**
     * Runs {@code reader} in a new thread, paused at its first pre-load of {@code entity} while
     * {@code writer} runs to its commit in this thread. Returns what the reader returned.
     *
     * @throws AssertionError if the reader didn't load an {@code entity} from the database
     */
    public static <T> T race(
            EntityManagerFactory emf, Class<?> entity, Callable<T> reader, Runnable writer)
            throws InterruptedException {
        install(emf);
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread thread =
                new Thread(
                        () -> {
                            try {
                                result.set(reader.call());
                            } catch (Throwable e) {
                                failure.set(e);
                            }
                        },
                        "cache-race-reader");
        PAUSE.pause(thread, entity);
        final CountDownLatch read = PAUSE.read;
        final CountDownLatch release = PAUSE.release;
        thread.start();
        final boolean paused = read.await(WAIT_SECONDS, TimeUnit.SECONDS);
        try {
            if (paused) {
                writer.run();
            }
        } finally {
            PAUSE.thread.set(null);
            release.countDown();
            thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        }
        if (failure.get() != null) {
            throw new AssertionError("the reader failed", failure.get());
        }
        if (!paused) {
            throw new AssertionError(
                    "the reader loaded no " + entity.getSimpleName() + " from the database");
        }
        return result.get();
    }
}
