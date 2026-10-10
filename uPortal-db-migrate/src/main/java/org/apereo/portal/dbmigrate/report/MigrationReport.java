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
package org.apereo.portal.dbmigrate.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apereo.portal.dbmigrate.copy.GeneratorPositioner;

/** What a run did, as JSON (for tools) and as a human summary. */
public final class MigrationReport {

    /** One table's copy and verification. */
    public static final class TableEntry {
        public String source;
        public String target;
        public long sourceRows = -1;
        public long targetRowsBefore = -1;
        public long copiedRows = -1;
        public long copyMillis;
        public long deferredRows;
        public long batchCommits;
        public String verify = "NOT RUN";
        public long verifiedSourceRows = -1;
        public long verifiedTargetRows = -1;
        public String sourceHash;
        public String targetHash;
        public long verifyMillis;
        public String error;
        public List<String> notes = new ArrayList<>();

        double rowsPerSecond() {
            return copyMillis <= 0 ? copiedRows : copiedRows * 1000.0 / copyMillis;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", source);
            m.put("target", target);
            m.put("sourceRows", sourceRows);
            m.put("targetRowsBefore", targetRowsBefore);
            m.put("copiedRows", copiedRows);
            m.put("copyMillis", copyMillis);
            m.put("rowsPerSecond", Math.round(rowsPerSecond()));
            m.put("deferredForeignKeyRows", deferredRows);
            m.put("batchCommits", batchCommits);
            m.put("verify", verify);
            m.put("verifiedSourceRows", verifiedSourceRows);
            m.put("verifiedTargetRows", verifiedTargetRows);
            m.put("sourceHash", sourceHash);
            m.put("targetHash", targetHash);
            m.put("verifyMillis", verifyMillis);
            m.put("error", error);
            m.put("notes", notes);
            return m;
        }
    }

    public String mode;
    public final Instant started = Instant.now();
    public Instant finished;
    public final Map<String, Object> source = new LinkedHashMap<>();
    public final Map<String, Object> target = new LinkedHashMap<>();
    public final Map<String, Object> options = new LinkedHashMap<>();
    public final Map<String, Object> plan = new LinkedHashMap<>();
    public final List<String> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<TableEntry> tables = new ArrayList<>();
    public final List<GeneratorPositioner.Result> generators = new ArrayList<>();
    public final List<String> identities = new ArrayList<>();
    public long clearedRows = -1;
    public long copyMillis;
    public long verifyMillis;
    public final Map<String, Long> memory = new LinkedHashMap<>();

    public TableEntry table(String sourceName, String targetName) {
        TableEntry e = new TableEntry();
        e.source = sourceName;
        e.target = targetName;
        tables.add(e);
        return e;
    }

    public boolean success() {
        if (!errors.isEmpty()) {
            return false;
        }
        for (TableEntry t : tables) {
            if (t.error != null || "MISMATCH".equals(t.verify) || "ERROR".equals(t.verify)) {
                return false;
            }
        }
        for (GeneratorPositioner.Result g : generators) {
            if (!g.ok) {
                return false;
            }
        }
        return true;
    }

    public long totalRows() {
        long n = 0;
        for (TableEntry t : tables) {
            n += Math.max(0, t.copiedRows);
        }
        return n;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool", "uPortal-db-migrate");
        m.put("version", version());
        m.put("mode", mode);
        m.put("result", success() ? "SUCCESS" : "FAILED");
        m.put("started", started.toString());
        m.put("finished", finished == null ? null : finished.toString());
        m.put(
                "durationMillis",
                finished == null ? null : Duration.between(started, finished).toMillis());
        m.put("copyMillis", copyMillis);
        m.put("verifyMillis", verifyMillis);
        m.put("totalCopiedRows", totalRows());
        m.put("source", source);
        m.put("target", target);
        m.put("options", options);
        m.put("plan", plan);
        m.put("errors", errors);
        m.put("warnings", warnings);
        List<Object> t = new ArrayList<>();
        tables.forEach(e -> t.add(e.toMap()));
        m.put("tables", t);
        List<Object> g = new ArrayList<>();
        for (GeneratorPositioner.Result r : generators) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("name", r.name);
            gm.put("targetStorage", r.targetStorage);
            gm.put("source", r.sourceStorage);
            gm.put("mappingFrom", r.origin);
            gm.put("allocationSize", r.allocationSize);
            gm.put("serves", r.served);
            gm.put("maxId", r.maxId);
            gm.put("sourceNext", r.sourceNext);
            gm.put("targetNextBefore", r.targetBefore);
            gm.put("targetNextAfter", r.targetAfter);
            gm.put("requiredAtLeast", r.required);
            gm.put("changed", r.changed);
            gm.put("ok", r.ok);
            gm.put("message", r.message);
            g.add(gm);
        }
        m.put("generators", g);
        m.put("identityColumns", identities);
        m.put("clearedTargetRows", clearedRows);
        m.put("memory", memory);
        return m;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("uPortal database copy (")
                .append(mode)
                .append("): ")
                .append(success() ? "SUCCESS" : "FAILED")
                .append('\n');
        sb.append("  source: ").append(describe(source)).append('\n');
        sb.append("  target: ").append(describe(target)).append('\n');
        if (finished != null) {
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "  %d tables, %,d rows copied in %.1f s, verified in %.1f s (total %.1f s)%n",
                            tables.size(),
                            totalRows(),
                            copyMillis / 1000.0,
                            verifyMillis / 1000.0,
                            Duration.between(started, finished).toMillis() / 1000.0));
        }
        if (!memory.isEmpty()) {
            sb.append("  JVM heap: max used ")
                    .append(memory.getOrDefault("maxUsedHeapMiB", 0L))
                    .append(" MiB of ")
                    .append(memory.getOrDefault("maxHeapMiB", 0L))
                    .append(" MiB\n");
        }
        if (!tables.isEmpty()) {
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "%n  %-34s %12s %12s %9s %10s  %s%n",
                            "table",
                            "source rows",
                            "target rows",
                            "copy s",
                            "rows/s",
                            "verify"));
            for (TableEntry t : tables) {
                sb.append(
                        String.format(
                                Locale.ROOT,
                                "  %-34s %12s %12s %9.2f %10s  %s%n",
                                t.source.equalsIgnoreCase(t.target)
                                        ? t.source
                                        : t.source + "->" + t.target,
                                count(
                                        t.verifiedSourceRows >= 0
                                                ? t.verifiedSourceRows
                                                : t.sourceRows),
                                count(
                                        t.verifiedTargetRows >= 0
                                                ? t.verifiedTargetRows
                                                : t.copiedRows >= 0
                                                        ? t.copiedRows
                                                        : t.targetRowsBefore),
                                t.copyMillis / 1000.0,
                                t.copiedRows > 0
                                        ? String.format(Locale.ROOT, "%,.0f", t.rowsPerSecond())
                                        : "",
                                t.verify + (t.error == null ? "" : " " + t.error)));
            }
        }
        if (!generators.isEmpty()) {
            int ok = 0;
            int changed = 0;
            for (GeneratorPositioner.Result g : generators) {
                ok += g.ok ? 1 : 0;
                changed += g.changed ? 1 : 0;
            }
            sb.append(
                    String.format(
                            Locale.ROOT,
                            "%n  generators: %d checked, %d moved, %d OK%n",
                            generators.size(),
                            changed,
                            ok));
            for (GeneratorPositioner.Result g : generators) {
                if (!g.ok || g.maxId != null) {
                    sb.append(
                            String.format(
                                    Locale.ROOT,
                                    "    %-30s %-8s alloc %-5d max id %-10s source next %-10s next %s -> %s %s%n",
                                    g.name,
                                    g.targetStorage,
                                    g.allocationSize,
                                    g.maxId,
                                    g.sourceNext,
                                    g.targetBefore,
                                    g.targetAfter,
                                    g.ok ? "OK" : "FAILED: " + g.message));
                }
            }
        }
        for (String w : warnings) {
            sb.append("  WARNING: ").append(w).append('\n');
        }
        for (String e : errors) {
            sb.append("  ERROR: ").append(e).append('\n');
        }
        return sb.toString();
    }

    private static String count(long n) {
        return n < 0 ? "" : String.format(Locale.ROOT, "%,d", n);
    }

    private static String describe(Map<String, Object> side) {
        return side.getOrDefault("vendor", "?")
                + " "
                + side.getOrDefault("productVersion", "")
                + " "
                + side.getOrDefault("url", "")
                + (side.get("schema") == null ? "" : " schema " + side.get("schema"));
    }

    /** Writes the JSON report to {@code file} and the summary next to it ({@code .txt}). */
    public void write(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, Json.write(toMap()).getBytes(StandardCharsets.UTF_8));
        String name = file.getFileName().toString();
        String txt =
                (name.endsWith(".json") ? name.substring(0, name.length() - 5) : name) + ".txt";
        Files.write(file.resolveSibling(txt), summary().getBytes(StandardCharsets.UTF_8));
    }

    static String version() {
        String v = MigrationReport.class.getPackage().getImplementationVersion();
        return v == null ? "development" : v;
    }
}
