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
package org.apereo.portal.dbmigrate;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Command line: {@code java -cp uPortal-db-migrate.jar:<jdbc drivers> \
 * org.apereo.portal.dbmigrate.DbMigrateCli --source-url ... --target-url ...}.
 */
public final class DbMigrateCli {

    static final String USAGE =
            String.join(
                    "\n",
                    "Usage: db-migrate [options]",
                    "",
                    "Copies a uPortal database into an empty uPortal schema, on the same or another",
                    "vendor (HSQLDB, PostgreSQL, MySQL, MariaDB, SQL Server, Oracle), then positions",
                    "the ID generators and verifies every table. The source is only read.",
                    "",
                    "Connections (a properties file has url, user, password and optionally schema;",
                    "the hibernate.connection.* keys of global.properties work too):",
                    "  --source-url URL --source-user USER --source-password PASSWORD [--source-schema S]",
                    "  --source-properties FILE",
                    "  --target-url URL --target-user USER --target-password PASSWORD [--target-schema S]",
                    "  --target-properties FILE",
                    "",
                    "What to copy:",
                    "  --include GLOB[,GLOB]   copy only matching tables (repeatable)",
                    "  --exclude GLOB[,GLOB]   do not copy matching tables (repeatable)",
                    "  --skip-raw-events       do not copy UP_RAW_EVENTS (raw portal event history)",
                    "  --rules FILE            extra mapping rules (repeatable; see RuleSet)",
                    "  --source-profile NAME   schema version of the source (default 5.17)",
                    "  --target-profile NAME   schema version of the target (default 6.0); a copy",
                    "                          between two 5.17 databases uses --target-profile 5.17",
                    "  --source-zone ZONE      time zone of the 5.17 portal's JVM (an IANA id such as",
                    "                          America/New_York); needed for --target-profile 6.0",
                    "                          when the source has local-time timestamps to convert",
                    "  --missing-lob fail|skip-row  PostgreSQL rows whose large object is gone",
                    "                          (default fail)",
                    "",
                    "How:",
                    "  --clear-target          delete the rows of the target tables first",
                    "  --batch-size N          rows per INSERT batch and commit (default 1000)",
                    "  --fetch-size N          rows per read round trip (default: batch size)",
                    "  --threads N             tables copied and verified in parallel (default 4)",
                    "  --dry-run               plan and row counts only; write nothing",
                    "  --verify-only           compare an already copied target with the source",
                    "  --no-verify             skip the verification after copying",
                    "  --report FILE           write a JSON report there, and a summary as .txt",
                    "",
                    "Exit status: 0 copied and verified, 1 copy or verification failed,",
                    "2 plan errors or bad arguments (nothing written).",
                    "",
                    "A copy that stops part-way (status 1, or a killed process) leaves the chunks it",
                    "committed in the target and the ID generators unpositioned; it cannot resume.",
                    "Run it again with --clear-target.");

    private DbMigrateCli() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        MigrationOptions options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException | IOException e) {
            err.println("db-migrate: " + e.getMessage());
            err.println();
            err.println(USAGE);
            return DbMigrate.PLAN_ERROR;
        }
        if (options == null) {
            out.println(USAGE);
            return DbMigrate.OK;
        }
        return new DbMigrate(options, out).run();
    }

    /** Returns the options of a command line, or null when help was asked for. */
    static MigrationOptions parse(String[] args) throws IOException {
        MigrationOptions o = new MigrationOptions();
        String[] source = new String[4];
        String[] target = new String[4];
        Path sourceProps = null;
        Path targetProps = null;
        boolean dryRun = false;
        boolean verifyOnly = false;
        List<String> list = new ArrayList<>(Arrays.asList(args));
        for (int i = 0; i < list.size(); i++) {
            String arg = list.get(i);
            String value = null;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                value = arg.substring(eq + 1);
                arg = arg.substring(0, eq);
            }
            switch (arg) {
                case "-h":
                case "--help":
                    return null;
                case "--skip-raw-events":
                    o.skipRawEvents(true);
                    continue;
                case "--dry-run":
                    dryRun = true;
                    continue;
                case "--verify-only":
                    verifyOnly = true;
                    continue;
                case "--no-verify":
                    o.verify(false);
                    continue;
                case "--clear-target":
                    o.clearTarget(true);
                    continue;
                default:
                    break;
            }
            if (value == null) {
                if (i + 1 >= list.size()) {
                    throw new IllegalArgumentException(arg + " needs a value");
                }
                value = list.get(++i);
            }
            switch (arg) {
                case "--source-url":
                    source[0] = value;
                    break;
                case "--source-user":
                    source[1] = value;
                    break;
                case "--source-password":
                    source[2] = value;
                    break;
                case "--source-schema":
                    source[3] = value;
                    break;
                case "--source-properties":
                    sourceProps = Paths.get(value);
                    break;
                case "--target-url":
                    target[0] = value;
                    break;
                case "--target-user":
                    target[1] = value;
                    break;
                case "--target-password":
                    target[2] = value;
                    break;
                case "--target-schema":
                    target[3] = value;
                    break;
                case "--target-properties":
                    targetProps = Paths.get(value);
                    break;
                case "--include":
                    o.includes().addAll(globs(value));
                    break;
                case "--exclude":
                    o.excludes().addAll(globs(value));
                    break;
                case "--rules":
                    o.ruleFiles().add(Paths.get(value));
                    break;
                case "--source-profile":
                    o.sourceProfile(value);
                    break;
                case "--target-profile":
                    o.targetProfile(value);
                    break;
                case "--source-zone":
                    o.sourceZone(ZoneId.of(value));
                    break;
                case "--missing-lob":
                    o.missingLobPolicy(
                            MigrationOptions.MissingLobPolicy.valueOf(
                                    value.toUpperCase(Locale.ROOT).replace('-', '_')));
                    break;
                case "--batch-size":
                    o.batchSize(Integer.parseInt(value));
                    break;
                case "--fetch-size":
                    o.fetchSize(Integer.parseInt(value));
                    break;
                case "--threads":
                    o.threads(Integer.parseInt(value));
                    break;
                case "--report":
                    o.report(Paths.get(value));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option " + arg);
            }
        }
        if (dryRun && verifyOnly) {
            throw new IllegalArgumentException("--dry-run and --verify-only exclude each other");
        }
        o.mode(
                dryRun
                        ? MigrationOptions.Mode.DRY_RUN
                        : verifyOnly
                                ? MigrationOptions.Mode.VERIFY_ONLY
                                : MigrationOptions.Mode.COPY);
        o.source(connection("source", source, sourceProps));
        o.target(connection("target", target, targetProps));
        if (o.source().url().equals(o.target().url())
                && String.valueOf(o.source().schema())
                        .equals(String.valueOf(o.target().schema()))) {
            throw new IllegalArgumentException("Source and target are the same database");
        }
        return o;
    }

    private static List<String> globs(String value) {
        List<String> out = new ArrayList<>();
        for (String g : value.split(",", -1)) {
            if (!g.trim().isEmpty()) {
                out.add(g.trim());
            }
        }
        return out;
    }

    private static ConnectionSpec connection(String label, String[] v, Path props)
            throws IOException {
        ConnectionSpec spec;
        if (props != null) {
            spec = ConnectionSpec.fromProperties(label, props);
            if (v[0] != null) {
                spec = new ConnectionSpec(label, v[0], spec.user(), spec.password(), spec.schema());
            }
            return spec.withOverrides(v[1], v[2], v[3]);
        }
        if (v[0] == null) {
            throw new IllegalArgumentException(
                    "Give --" + label + "-url (or --" + label + "-properties)");
        }
        return new ConnectionSpec(label, v[0], v[1], v[2], v[3]);
    }
}
