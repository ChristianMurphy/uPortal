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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Every cache name that uPortal's own code asks for must be configured in both ehcache.xml and
 * ehcache-no-jgroups.xml. This is the spirit of Hibernate's {@code missing_cache_strategy=fail} for
 * the application caches: a name with no configuration would otherwise fail at startup
 * (CacheLookup) or silently get the defaultCache settings (CacheFactory).
 *
 * <p>The names are collected from the main sources of every module: {@code
 * CacheLookup.requireCache(...)} arguments (string literals or constants), {@code @Qualifier} or
 * {@code @Resource} on Ehcache or Spring Cache injection points, {@code getCache("...")} calls,
 * Spring {@code @Cacheable}/{@code @CachePut}/{@code @CacheEvict} cache names, and {@code
 * cacheName} properties in Spring XML. Hibernate second-level cache regions are not covered here.
 */
public class CacheConfigurationCoverageTest {

    /**
     * Names that are knowingly left to the defaultCache, with the reason. Adding to this list is a
     * decision that belongs in the cache inventory (docs/developer/modernization/notes).
     */
    private static final Map<String, String> ALLOWED_UNCONFIGURED = new HashMap<>();

    static {
        ALLOWED_UNCONFIGURED.put(
                CacheFactory.DEFAULT,
                "CacheFactory.getCache() without a name; uses the defaultCache by design");
    }

    private static final Pattern REQUIRE_CACHE =
            Pattern.compile("requireCache\\(\\s*[\\w.()]+\\s*,\\s*(\"([^\"]+)\"|([\\w.]+))\\s*\\)");
    private static final Pattern QUALIFIED_CACHE =
            Pattern.compile(
                    "@(?:Qualifier|Resource)\\(\\s*(?:value\\s*=\\s*|name\\s*=\\s*)?\"([^\"]+)\"\\s*\\)\\s*"
                            + "(?:public\\s+void\\s+\\w+\\(\\s*)?(?:final\\s+)?(?:private\\s+)?"
                            + "(?:net\\.sf\\.ehcache\\.)?(?:Ehcache|Cache)\\s");
    private static final Pattern GET_CACHE = Pattern.compile("getCache\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern SPRING_CACHE_ANNOTATION =
            Pattern.compile("@(?:Cacheable|CachePut|CacheEvict)\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern ANNOTATION_CACHE_NAMES =
            Pattern.compile("(?:cacheNames|value)\\s*=\\s*(\\{[^}]*\\}|\"[^\"]*\")");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern XML_CACHE_NAME =
            Pattern.compile("name=\"cacheName\"\\s+value=\"([^\"]+)\"");

    private static Path root;
    private static Map<Path, String> mainJava;
    private static Map<Path, String> mainXml;

    @BeforeClass
    public static void readSources() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue("Could not find the root of the uPortal build", dir != null);
        root = dir;
        mainJava = read(".java");
        mainXml = read(".xml");
    }

    /** The path with '/' separators, so the filters and messages match on Windows too. */
    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Map<Path, String> read(String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            final List<Path> files =
                    paths.filter(p -> p.toString().endsWith(suffix))
                            .filter(p -> slashed(p).contains("/src/main/"))
                            .filter(p -> !slashed(p).contains("/build/"))
                            .filter(p -> !slashed(p).contains("/node_modules/"))
                            .collect(Collectors.toList());
            final Map<Path, String> result = new HashMap<>();
            for (Path file : files) {
                result.put(file, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
            return result;
        }
    }

    /** cache name -> files that use it */
    static Map<String, Set<String>> usedCacheNames() {
        final Map<String, Set<String>> used = new TreeMap<>();
        for (Map.Entry<Path, String> e : mainJava.entrySet()) {
            final String source = e.getValue();
            final String file = slashed(root.relativize(e.getKey()));

            Matcher m = REQUIRE_CACHE.matcher(source);
            while (m.find()) {
                if ("String".equals(m.group(3))) {
                    continue; // the {@link requireCache(CacheManager, String)} Javadoc
                }
                final String name =
                        m.group(2) != null ? m.group(2) : resolveConstant(source, m.group(3));
                add(used, name, file);
            }
            m = QUALIFIED_CACHE.matcher(source);
            while (m.find()) {
                add(used, m.group(1), file);
            }
            m = GET_CACHE.matcher(source);
            while (m.find()) {
                add(used, m.group(1), file);
            }
            if (source.contains("import org.springframework.cache.annotation.")) {
                m = SPRING_CACHE_ANNOTATION.matcher(source);
                while (m.find()) {
                    final String args = m.group(1).trim();
                    final Matcher names = ANNOTATION_CACHE_NAMES.matcher(args);
                    final String nameList;
                    if (names.find()) {
                        nameList = names.group(1);
                    } else if (args.startsWith("\"")) {
                        nameList = args;
                    } else {
                        continue;
                    }
                    final Matcher literal = STRING_LITERAL.matcher(nameList);
                    while (literal.find()) {
                        add(used, literal.group(1), file);
                    }
                }
            }
        }
        for (Map.Entry<Path, String> e : mainXml.entrySet()) {
            final Matcher m = XML_CACHE_NAME.matcher(e.getValue());
            while (m.find()) {
                add(used, m.group(1), slashed(root.relativize(e.getKey())));
            }
        }
        return used;
    }

    private static void add(Map<String, Set<String>> used, String name, String file) {
        used.computeIfAbsent(name, k -> new TreeSet<>()).add(file);
    }

    /** Resolves NAME or Type.NAME to the string value of a static final String constant. */
    private static String resolveConstant(String source, String reference) {
        final String constant = reference.substring(reference.lastIndexOf('.') + 1);
        final Pattern declaration =
                Pattern.compile(
                        "static\\s+final\\s+String\\s+"
                                + Pattern.quote(constant)
                                + "\\s*=\\s*\"([^\"]+)\"\\s*;");
        Matcher m = declaration.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        for (String other : mainJava.values()) {
            m = declaration.matcher(other);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("Cannot resolve the cache name constant " + reference);
    }

    private static Set<String> configuredCacheNames(String file) throws Exception {
        final Path config = root.resolve("uPortal-webapp/src/main/resources/properties/" + file);
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        final Document document = factory.newDocumentBuilder().parse(config.toFile());
        final NodeList caches = document.getElementsByTagName("cache");
        final Set<String> names = new TreeSet<>();
        for (int i = 0; i < caches.getLength(); i++) {
            names.add(((Element) caches.item(i)).getAttribute("name"));
        }
        return names;
    }

    @Test
    public void everyUsedCacheNameIsConfigured() throws Exception {
        final Map<String, Set<String>> used = usedCacheNames();
        // Guard against a scan that silently finds nothing (e.g. a changed source layout)
        assertTrue("Found only " + used.size() + " cache names: " + used, used.size() >= 30);

        for (String file : new String[] {"ehcache.xml", "ehcache-no-jgroups.xml"}) {
            final Set<String> configured = configuredCacheNames(file);
            final Map<String, Set<String>> missing = new TreeMap<>(used);
            missing.keySet().removeAll(configured);
            missing.keySet().removeAll(ALLOWED_UNCONFIGURED.keySet());
            assertTrue(
                    "Cache names used in code but not configured in "
                            + file
                            + " (name=[files]): "
                            + missing,
                    missing.isEmpty());
        }
    }

    @Test
    public void allowedUnconfiguredNamesAreStillUnconfigured() throws Exception {
        final Set<String> configured = configuredCacheNames("ehcache.xml");
        assertTrue(
                "Remove configured names from ALLOWED_UNCONFIGURED",
                Collections.disjoint(configured, ALLOWED_UNCONFIGURED.keySet()));
    }
}
