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
package org.apereo.portal.dbmigrate.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apereo.portal.dbmigrate.schema.Names;

/**
 * Declarative mapping rules. One rule per line; {@code #} starts a comment. Table names are source
 * table names, matched case-insensitively.
 *
 * <pre>
 * exclude          UP_RAW_EVENTS              # glob; the table is deliberately not copied
 * rename-table     UP_OLD UP_NEW
 * rename-column    UP_USER.OLD_NAME NEW_NAME
 * skip-column      UP_USER.DROPPED            # source column deliberately not copied
 * transform        UP_EVENT.WHEN timestamp-at-zone zone=America/Chicago
 * derive-column    UP_USER.NEW_COL constant value=x   # target-only column, filled by a transform
 * lob-oid          UP_RAW_EVENTS.EVENT_DATA   # PostgreSQL text column holding large-object OIDs
 * generator        HIBERNATE_SEQUENCE allocation=1 serves=T1.ID,T2.ID
 * rename-generator UP_FOO_SEQ UP_FOO_ID_SEQ
 * </pre>
 *
 * Anything a rule does not mention is mapped by name; anything that cannot be mapped fails the plan
 * (no silent data loss).
 */
public final class RuleSet {

    private final List<String> excludes = new ArrayList<>();
    private final Set<String> lobOid = new TreeSet<>();
    private final Map<String, String> tableRenames = new TreeMap<>();
    private final Map<String, String> columnRenames = new TreeMap<>();
    private final Set<String> skipColumns = new TreeSet<>();
    private final Map<String, TransformRule> transforms = new TreeMap<>();
    private final Map<String, TransformRule> derived = new TreeMap<>();
    private final List<GeneratorRule> generators = new ArrayList<>();
    private final Map<String, String> generatorRenames = new TreeMap<>();
    private final List<String> sources = new ArrayList<>();

    public static RuleSet empty() {
        return new RuleSet();
    }

    public static RuleSet parse(String text, String origin) {
        RuleSet r = new RuleSet();
        r.add(new StringReader(text), origin);
        return r;
    }

    public static RuleSet fromResource(String resource) {
        RuleSet r = new RuleSet();
        r.addResource(resource);
        return r;
    }

    public RuleSet addResource(String resource) {
        InputStream in = RuleSet.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("No rule resource " + resource);
        }
        add(new InputStreamReader(in, StandardCharsets.UTF_8), resource);
        return this;
    }

    public RuleSet add(Reader reader, String origin) {
        sources.add(origin);
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                n++;
                int hash = line.indexOf('#');
                String content = (hash >= 0 ? line.substring(0, hash) : line).trim();
                if (!content.isEmpty()) {
                    try {
                        parseLine(content.split("\\s+"));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                origin + ":" + n + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read rules from " + origin, e);
        }
        return this;
    }

    private void parseLine(String[] t) {
        String directive = t[0].toLowerCase(java.util.Locale.ROOT);
        switch (directive) {
            case "exclude":
                expect(t, 2, "exclude <table-glob>");
                excludes.add(t[1]);
                break;
            case "lob-oid":
                expect(t, 2, "lob-oid <TABLE>.<COLUMN>");
                lobOid.add(columnRef(t[1]));
                break;
            case "rename-table":
                expect(t, 3, "rename-table <source> <target>");
                tableRenames.put(Names.key(t[1]), t[2]);
                break;
            case "rename-column":
                expect(t, 3, "rename-column <TABLE>.<source-column> <target-column>");
                columnRenames.put(columnRef(t[1]), t[2]);
                break;
            case "skip-column":
                expect(t, 2, "skip-column <TABLE>.<COLUMN>");
                skipColumns.add(columnRef(t[1]));
                break;
            case "transform":
            case "derive-column":
                if (t.length < 3) {
                    throw new IllegalArgumentException(
                            "expected: "
                                    + directive
                                    + " <TABLE>.<COLUMN> <transform> [key=value...]");
                }
                {
                    TransformRule rule =
                            new TransformRule(t[2], arguments(Arrays.copyOfRange(t, 3, t.length)));
                    (directive.equals("transform") ? transforms : derived)
                            .put(columnRef(t[1]), rule);
                }
                break;
            case "generator":
                {
                    if (t.length < 2) {
                        throw new IllegalArgumentException(
                                "expected: generator <name> allocation=<n> serves=<T.C>[,<T.C>...]");
                    }
                    Map<String, String> args = arguments(Arrays.copyOfRange(t, 2, t.length));
                    int allocation = Integer.parseInt(args.getOrDefault("allocation", "1"));
                    List<String> serves = new ArrayList<>();
                    String s = args.get("serves");
                    if (s != null) {
                        for (String ref : s.split(",", -1)) {
                            serves.add(columnRef(ref));
                        }
                    }
                    generators.add(new GeneratorRule(t[1], allocation, serves));
                }
                break;
            case "rename-generator":
                expect(t, 3, "rename-generator <source> <target>");
                generatorRenames.put(Names.key(t[1]), t[2]);
                break;
            default:
                throw new IllegalArgumentException("unknown rule '" + t[0] + "'");
        }
    }

    private static void expect(String[] t, int n, String usage) {
        if (t.length != n) {
            throw new IllegalArgumentException("expected: " + usage);
        }
    }

    private static String columnRef(String ref) {
        int dot = ref.lastIndexOf('.');
        if (dot <= 0 || dot == ref.length() - 1) {
            throw new IllegalArgumentException("expected TABLE.COLUMN, got '" + ref + "'");
        }
        return Names.key(ref);
    }

    private static Map<String, String> arguments(String[] tokens) {
        Map<String, String> args = new LinkedHashMap<>();
        for (String tok : tokens) {
            int eq = tok.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("expected key=value, got '" + tok + "'");
            }
            args.put(tok.substring(0, eq), tok.substring(eq + 1));
        }
        return args;
    }

    static String required(Map<String, String> args, String key, String transform) {
        String v = args.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(transform + " needs " + key + "=...");
        }
        return v;
    }

    /** Adds the rules of {@code other} to this set (later rules win for the same column). */
    public RuleSet merge(RuleSet other) {
        excludes.addAll(other.excludes);
        lobOid.addAll(other.lobOid);
        tableRenames.putAll(other.tableRenames);
        columnRenames.putAll(other.columnRenames);
        skipColumns.addAll(other.skipColumns);
        transforms.putAll(other.transforms);
        derived.putAll(other.derived);
        generators.addAll(other.generators);
        generatorRenames.putAll(other.generatorRenames);
        sources.addAll(other.sources);
        return this;
    }

    public RuleSet exclude(String glob) {
        excludes.add(glob);
        return this;
    }

    // ------------------------------------------------------------------ queries

    public boolean isExcluded(String table) {
        for (String glob : excludes) {
            if (Names.globMatches(glob, table)) {
                return true;
            }
        }
        return false;
    }

    public List<String> excludes() {
        return Collections.unmodifiableList(excludes);
    }

    public boolean isLobOid(String table, String column) {
        return lobOid.contains(Names.key(table + "." + column));
    }

    public Set<String> lobOidColumns() {
        return Collections.unmodifiableSet(lobOid);
    }

    public String targetTableName(String sourceTable) {
        return tableRenames.getOrDefault(Names.key(sourceTable), sourceTable);
    }

    public String targetColumnName(String sourceTable, String sourceColumn) {
        return columnRenames.getOrDefault(
                Names.key(sourceTable + "." + sourceColumn), sourceColumn);
    }

    public boolean isSkipped(String sourceTable, String sourceColumn) {
        return skipColumns.contains(Names.key(sourceTable + "." + sourceColumn));
    }

    public TransformRule transform(String sourceTable, String sourceColumn) {
        return transforms.get(Names.key(sourceTable + "." + sourceColumn));
    }

    /** Derived target columns of a source table, keyed by upper-case target column name. */
    public Map<String, TransformRule> derivedColumns(String sourceTable) {
        Map<String, TransformRule> out = new TreeMap<>();
        String prefix = Names.key(sourceTable) + ".";
        for (Map.Entry<String, TransformRule> e : derived.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }

    public List<GeneratorRule> generators() {
        return Collections.unmodifiableList(generators);
    }

    public String targetGeneratorName(String sourceGenerator) {
        return generatorRenames.getOrDefault(Names.key(sourceGenerator), sourceGenerator);
    }

    public List<String> sources() {
        return Collections.unmodifiableList(sources);
    }

    /** Resolves a transform by name: built-in first, then {@link ServiceLoader} providers. */
    public static ValueTransform.Function bind(TransformRule rule) {
        for (ValueTransform t : BuiltInTransforms.all()) {
            if (t.name().equals(rule.name())) {
                return t.bind(rule.arguments());
            }
        }
        for (ValueTransform t : ServiceLoader.load(ValueTransform.class)) {
            if (t.name().equals(rule.name())) {
                return t.bind(rule.arguments());
            }
        }
        throw new IllegalArgumentException("Unknown transform '" + rule.name() + "'");
    }

    /** A transform name with its arguments. */
    public static final class TransformRule {
        private final String name;
        private final Map<String, String> arguments;

        TransformRule(String name, Map<String, String> arguments) {
            this.name = name;
            this.arguments = Collections.unmodifiableMap(arguments);
        }

        public String name() {
            return name;
        }

        public Map<String, String> arguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return name + (arguments.isEmpty() ? "" : " " + arguments);
        }
    }

    /** An ID generator that rules (rather than entity annotations) describe. */
    public static final class GeneratorRule {
        private final String name;
        private final int allocationSize;
        private final List<String> serves;

        GeneratorRule(String name, int allocationSize, List<String> serves) {
            this.name = name;
            this.allocationSize = allocationSize;
            this.serves = Collections.unmodifiableList(serves);
        }

        public String name() {
            return name;
        }

        public int allocationSize() {
            return allocationSize;
        }

        /** Upper-case {@code TABLE.COLUMN} references. */
        public List<String> serves() {
            return serves;
        }
    }
}
