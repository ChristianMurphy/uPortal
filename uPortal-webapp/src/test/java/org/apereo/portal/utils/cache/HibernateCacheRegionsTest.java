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
package org.apereo.portal.utils.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.ehcache.SpringBeanEhCacheRegionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.Test;

/**
 * Every second-level cache region of the two cached persistence units must have its own entry in
 * ehcache.xml and in ehcache-no-jgroups.xml.
 *
 * <p>A region without an entry is built from the defaultCache: 1000 entries, ten minutes, and no
 * replication, so other nodes of a cluster keep what they cached. The portal only warns about it
 * (HHH90001006, {@code hibernate.cache.ehcache.missing_cache_strategy=create-warn}). This test
 * boots both units with {@code fail}, as every test JVM runs, so a missing entry stops the build.
 */
public class HibernateCacheRegionsTest {

    private static final String[] FILES = {"ehcache.xml", "ehcache-no-jgroups.xml"};

    /** Persistence unit -> hibernate.cache.region_prefix, as the persistence contexts set it */
    private static final Map<String, String> UNITS = new TreeMap<>();

    static {
        UNITS.put("AggrEventsDb", "AggrEvents");
        UNITS.put("PortalDb", "");
    }

    /** The names Hibernate 5.3 and later give the regions that every cached query uses */
    private static final List<String> DEFAULT_REGIONS =
            Arrays.asList("default-query-results-region", "default-update-timestamps-region");

    /** The Hibernate 4 and 5.2 names, which 5.6 still accepts with warning HHH90001007 */
    private static final List<String> LEGACY_DEFAULT_REGIONS =
            Arrays.asList(
                    "org.hibernate.cache.internal.StandardQueryCache",
                    "org.hibernate.cache.spi.UpdateTimestampsCache");

    private static final Pattern QUERY_ROOT =
            Pattern.compile("\\.from\\(\\s*(\\w+)\\.class\\s*\\)");
    private static final Pattern EXCLUDED_CACHE_NAME = Pattern.compile("<value>([^<]+)</value>");

    @Test
    public void everyRegionHasAnEntry() throws IOException {
        final Set<String> queryRoots = rootsOfCachedQueries();
        for (final String file : FILES) {
            final CacheManager cacheManager = cacheManager(file);
            try {
                final Set<String> queryRegions = new TreeSet<>();
                final Set<String> missing = new TreeSet<>();
                for (final Map.Entry<String, String> unit : UNITS.entrySet()) {
                    // Fails here for an entity, collection or natural-id region without an entry
                    final EntityManagerFactory entityManagerFactory;
                    try {
                        entityManagerFactory = boot(unit.getKey(), unit.getValue(), cacheManager);
                    } catch (RuntimeException e) {
                        throw new AssertionError(
                                unit.getKey()
                                        + " doesn't start with the caches of "
                                        + file
                                        + ". Give the region its own <cache> entry in both"
                                        + " Ehcache files: "
                                        + rootMessage(e),
                                e);
                    }
                    try {
                        final SessionFactoryImplementor sessionFactory =
                                entityManagerFactory.unwrap(SessionFactoryImplementor.class);
                        for (final String entity :
                                sessionFactory.getMetamodel().entityPersisters().keySet()) {
                            if (!queryRoots.contains(
                                    entity.substring(entity.lastIndexOf('.') + 1))) {
                                continue;
                            }
                            // BaseJpaDao.createCachedQuery names the region after the query's root
                            final String region = entity + ".Query";
                            queryRegions.add(region);
                            try {
                                sessionFactory.getCache().getQueryResultsCache(region);
                            } catch (CacheException e) {
                                missing.add(qualified(unit.getValue(), region));
                            }
                        }
                    } finally {
                        entityManagerFactory.close();
                    }
                }
                assertEquals(
                        "Cached queries read these regions, and "
                                + file
                                + " has no <cache> entry for them",
                        new TreeSet<String>(),
                        missing);
                // Guard against a scan that finds nothing, such as after a changed source layout
                assertTrue(
                        "Found only these query regions: " + queryRegions,
                        queryRegions.size() >= 20);
            } finally {
                cacheManager.shutdown();
            }
        }
    }

    @Test
    public void theDefaultRegionsUseTheCurrentNames() throws IOException {
        final Set<String> expected = new TreeSet<>();
        final Set<String> legacy = new TreeSet<>();
        for (final String prefix : UNITS.values()) {
            for (final String region : DEFAULT_REGIONS) {
                expected.add(qualified(prefix, region));
            }
            for (final String region : LEGACY_DEFAULT_REGIONS) {
                legacy.add(qualified(prefix, region));
            }
        }

        for (final String file : FILES) {
            final Set<String> configured =
                    new TreeSet<>(
                            Arrays.asList(
                                    configuration(file)
                                            .getCacheConfigurationsKeySet()
                                            .toArray(new String[0])));
            final Set<String> absent = new TreeSet<>(expected);
            absent.removeAll(configured);
            assertEquals(file + " lacks default regions", new TreeSet<String>(), absent);
            configured.retainAll(legacy);
            assertEquals(
                    file + " still has default regions under their Hibernate 4 names",
                    new TreeSet<String>(),
                    configured);
        }
    }

    /**
     * MergeViewCacheClearer must leave the timestamps regions alone. Without a timestamp, Hibernate
     * takes a cached query result for current.
     */
    @Test
    public void aMergeKeepsTheTimestampsRegions() throws IOException {
        final String context =
                read(
                        root().resolve(
                                        "uPortal-webapp/src/main/resources/properties/contexts/cacheContext.xml"));
        final Set<String> excluded = new TreeSet<>();
        final Matcher m = EXCLUDED_CACHE_NAME.matcher(context);
        while (m.find()) {
            excluded.add(m.group(1).trim());
        }
        for (final String prefix : UNITS.values()) {
            final String region = qualified(prefix, "default-update-timestamps-region");
            assertTrue(
                    "cacheContext.xml must list " + region + " in excludedCacheNames: " + excluded,
                    excluded.contains(region));
        }
    }

    private static String qualified(String prefix, String region) {
        return prefix.isEmpty() ? region : prefix + "." + region;
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private Configuration configuration(String file) {
        return ConfigurationFactory.parseConfiguration(
                getClass().getResource("/properties/" + file));
    }

    /**
     * The caches of the file, each with its own settings and listeners. The peer provider is left
     * out, so the test doesn't open a JGroups channel.
     */
    private CacheManager cacheManager(String file) {
        final Configuration configuration = configuration(file);
        configuration.getCacheManagerPeerProviderFactoryConfiguration().clear();
        configuration.setName("HibernateCacheRegionsTest." + file);
        return CacheManager.newInstance(configuration);
    }

    private static EntityManagerFactory boot(
            String unit, String prefix, CacheManager cacheManager) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("javax.persistence.jdbc.driver", "org.hsqldb.jdbcDriver");
        properties.put("javax.persistence.jdbc.url", "jdbc:hsqldb:mem:HibernateCacheRegionsTest");
        properties.put("javax.persistence.jdbc.user", "sa");
        properties.put("javax.persistence.jdbc.password", "");
        properties.put(
                "hibernate.cache.region.factory_class",
                SpringBeanEhCacheRegionFactory.class.getName());
        properties.put(SpringBeanEhCacheRegionFactory.CACHE_MANAGER_NAME, cacheManager.getName());
        properties.put("hibernate.cache.region_prefix", prefix);
        properties.put("hibernate.cache.ehcache.missing_cache_strategy", "fail");
        return Persistence.createEntityManagerFactory(unit, properties);
    }

    /**
     * The simple class names of the query roots in every DAO that calls createCachedQuery. A DAO's
     * queries that aren't cached count too, so this can ask for a region more than the portal
     * reads, never for one less.
     */
    private static Set<String> rootsOfCachedQueries() throws IOException {
        final List<Path> sources;
        try (Stream<Path> paths = Files.walk(root())) {
            sources =
                    paths.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> slashed(p).contains("/src/main/"))
                            .filter(p -> !slashed(p).contains("/build/"))
                            .filter(p -> !slashed(p).contains("/node_modules/"))
                            .collect(Collectors.toList());
        }
        final Set<String> roots = new TreeSet<>();
        for (final Path file : sources) {
            final String source = read(file);
            if (!source.contains("createCachedQuery(")) {
                continue;
            }
            final Matcher m = QUERY_ROOT.matcher(source);
            while (m.find()) {
                roots.add(m.group(1));
            }
        }
        return roots;
    }

    private static Path root() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue("Could not find the root of the uPortal build", dir != null);
        return dir;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** The path with '/' separators, so the filters match on Windows too. */
    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }
}
