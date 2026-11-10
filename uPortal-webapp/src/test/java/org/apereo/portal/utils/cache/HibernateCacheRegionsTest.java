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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.junit.Test;

/**
 * The names of the second-level cache regions that every cached query of the two cached persistence
 * units uses, in ehcache.xml, ehcache-no-jgroups.xml and cacheContext.xml.
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

    private static final Pattern EXCLUDED_CACHE_NAME = Pattern.compile("<value>([^<]+)</value>");

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

    private Configuration configuration(String file) {
        return ConfigurationFactory.parseConfiguration(
                getClass().getResource("/properties/" + file));
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
}
