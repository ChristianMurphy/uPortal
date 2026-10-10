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
package org.apereo.portal.dbmigrate.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apereo.portal.dbmigrate.rules.RuleSet;
import org.apereo.portal.dbmigrate.schema.Names;

/**
 * Which ID generator serves which table and column, with the allocationSize Hibernate allocates in.
 * Built from the uPortal entity annotations at build time ({@link EntityGeneratorScanner}), plus
 * {@code generator} rules for tables the annotations do not cover.
 */
public final class GeneratorCatalog {

    private final Map<String, Generator> generators = new LinkedHashMap<>();

    public static GeneratorCatalog empty() {
        return new GeneratorCatalog();
    }

    /** Loads {@code generators.tsv} from the classpath; empty when the resource is absent. */
    public static GeneratorCatalog fromResource(String resource) {
        GeneratorCatalog catalog = new GeneratorCatalog();
        InputStream in = GeneratorCatalog.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            return catalog;
        }
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                if (f.length < 5) {
                    throw new IllegalStateException("Bad line in " + resource + ": " + line);
                }
                catalog.add(
                        f[0],
                        Integer.parseInt(f[2]),
                        f[3] + "." + f[4],
                        f.length > 5 ? f[5] : null);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
        return catalog;
    }

    public GeneratorCatalog add(
            String name, int allocationSize, String tableDotColumn, String entity) {
        Generator g =
                generators.computeIfAbsent(
                        Names.key(name), k -> new Generator(name, allocationSize));
        if (g.allocationSize != allocationSize) {
            throw new IllegalStateException(
                    "Generator "
                            + name
                            + " is declared with allocationSize "
                            + g.allocationSize
                            + " and "
                            + allocationSize);
        }
        g.serves.add(Names.key(tableDotColumn));
        if (entity != null) {
            g.entities.add(entity);
        }
        return this;
    }

    public GeneratorCatalog addRules(RuleSet rules) {
        for (RuleSet.GeneratorRule r : rules.generators()) {
            if (r.serves().isEmpty()) {
                generators.computeIfAbsent(
                        Names.key(r.name()), k -> new Generator(r.name(), r.allocationSize()));
            }
            for (String ref : r.serves()) {
                add(r.name(), r.allocationSize(), ref, null);
            }
        }
        return this;
    }

    public Generator generator(String name) {
        return generators.get(Names.key(name));
    }

    public Collection<Generator> generators() {
        return Collections.unmodifiableCollection(generators.values());
    }

    public int size() {
        return generators.size();
    }

    /** One generator: its database name, allocationSize and the id columns it fills. */
    public static final class Generator {
        private final String name;
        private final int allocationSize;
        private final List<String> serves = new ArrayList<>();
        private final List<String> entities = new ArrayList<>();

        Generator(String name, int allocationSize) {
            this.name = name;
            this.allocationSize = allocationSize;
        }

        public String name() {
            return name;
        }

        public int allocationSize() {
            return allocationSize;
        }

        /** Upper-case {@code TABLE.COLUMN} references. */
        public List<String> serves() {
            return Collections.unmodifiableList(serves);
        }

        public List<String> entities() {
            return Collections.unmodifiableList(entities);
        }
    }
}
