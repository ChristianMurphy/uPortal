/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.properties;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import org.junit.Test;

/**
 * Concurrent first calls to {@link PropertiesManager} must all see the loaded properties
 * (MOD-I103).
 *
 * <p>The test loads its own copy of PropertiesManager, so the properties start out unloaded, in a
 * class loader whose portal.properties stream blocks until the test releases it. That holds the
 * first caller inside the load while a second caller asks for a property, every time.
 */
public class PropertiesManagerConcurrencyTest {

    private static final String PROPERTIES_MANAGER = PropertiesManager.class.getName();
    private static final String PORTAL_PROPERTIES = "properties/portal.properties";

    @Test(timeout = 60000)
    public void secondCallerWaitsWhileTheFirstLoadsTheProperties() throws Exception {
        final CountDownLatch loadStarted = new CountDownLatch(1);
        final CountDownLatch releaseLoad = new CountDownLatch(1);
        final ClassLoader loader =
                new BlockingPropertiesClassLoader(
                        readResource("test.properties"), loadStarted, releaseLoad);
        final Method getProperty =
                Class.forName(PROPERTIES_MANAGER, true, loader)
                        .getMethod("getProperty", String.class);

        final FutureTask<String> first = new FutureTask<>(() -> call(getProperty));
        final Thread firstThread = new Thread(first, "first-caller");
        firstThread.start();
        assertTrue("The first caller never started loading", loadStarted.await(30, SECONDS));

        final FutureTask<String> second = new FutureTask<>(() -> call(getProperty));
        final Thread secondThread = new Thread(second, "second-caller");
        secondThread.start();
        // Let the second caller either finish or wait for the load in progress
        while (secondThread.isAlive()
                && secondThread.getState() != Thread.State.BLOCKED
                && secondThread.getState() != Thread.State.WAITING) {
            Thread.sleep(1L);
        }
        releaseLoad.countDown();

        assertEquals("splat", first.get(30, SECONDS));
        assertEquals("splat", second.get(30, SECONDS));
    }

    /** Returns the property value, or the exception the call threw. */
    private static String call(Method getProperty) throws IllegalAccessException {
        try {
            return (String) getProperty.invoke(null, "simpleProperty");
        } catch (InvocationTargetException e) {
            return e.getCause().toString();
        }
    }

    private static byte[] readResource(String name) {
        try (InputStream in = PropertiesManagerConcurrencyTest.class.getResourceAsStream(name)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Defines its own PropertiesManager class and serves portal.properties from a stream whose
     * first read blocks until {@code releaseLoad} opens.
     */
    private static final class BlockingPropertiesClassLoader extends ClassLoader {
        private final byte[] properties;
        private final CountDownLatch loadStarted;
        private final CountDownLatch releaseLoad;

        BlockingPropertiesClassLoader(
                byte[] properties, CountDownLatch loadStarted, CountDownLatch releaseLoad) {
            super(PropertiesManagerConcurrencyTest.class.getClassLoader());
            this.properties = properties;
            this.loadStarted = loadStarted;
            this.releaseLoad = releaseLoad;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!PROPERTIES_MANAGER.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    final byte[] bytes = readResource("PropertiesManager.class");
                    result = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!PORTAL_PROPERTIES.equals(name)) {
                return super.getResourceAsStream(name);
            }
            return new FilterInputStream(new ByteArrayInputStream(properties)) {
                private boolean started;

                @Override
                public int read() throws IOException {
                    awaitRelease();
                    return super.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    awaitRelease();
                    return super.read(b, off, len);
                }

                private void awaitRelease() throws IOException {
                    if (started) {
                        return;
                    }
                    started = true;
                    loadStarted.countDown();
                    try {
                        if (!releaseLoad.await(30, SECONDS)) {
                            throw new IOException("The test never released the load");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
            };
        }
    }
}
