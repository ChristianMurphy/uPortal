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
package org.apereo.portal.test.golden;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Golden files of the migration-confidence tests (MOD-D29): what uPortal 5.17 (Hibernate 4.3,
 * Spring 4.3, Joda-Time) stores and returns, recorded once on HSQLDB and checked in under {@code
 * uPortal-webapp/src/test/resources/golden}. Every later step must reproduce them on every
 * database, or document why a difference is intended.
 *
 * <p>To re-record after an intended change, run on HSQLDB (the default database):
 *
 * <pre>
 * ./gradlew :uPortal-webapp:test --tests 'org.apereo.portal.*Golden*' -Duportal.golden.record=true
 * </pre>
 *
 * and review the diff of the golden files. CI never sets the property, and recording on another
 * database is refused, so a vendor's behaviour can only enter a golden through a documented
 * difference in the test code.
 *
 * <p>A mismatch fails with the differing lines, also appended to the GitHub job summary (artifacts
 * cannot always be downloaded), and the full actual output is written to {@code
 * build/golden-actual/}.
 */
public final class GoldenFiles {
    public static final String RECORD_PROPERTY = "uportal.golden.record";

    private static final Logger LOGGER = LoggerFactory.getLogger(GoldenFiles.class);
    private static final Path SOURCE_DIR = Paths.get("src", "test", "resources", "golden");
    private static final Path ACTUAL_DIR = Paths.get("build", "golden-actual");
    private static final int MAX_REPORTED_LINES = 80;

    private GoldenFiles() {}

    public static boolean isRecording() {
        return Boolean.getBoolean(RECORD_PROPERTY);
    }

    /**
     * Compares {@code actual} with the golden file {@code name}, after applying the documented
     * differences of {@code vendor} to each expected line. In record mode, writes the golden
     * instead (HSQLDB only).
     *
     * @param context shown in the failure message, e.g. the JVM time zone
     * @param expectedLine maps a golden line to what the vendor is documented to return instead;
     *     the identity on HSQLDB
     */
    public static void verify(
            String name,
            List<String> actual,
            DatabaseVendor vendor,
            String context,
            UnaryOperator<String> expectedLine) {
        final Path golden = SOURCE_DIR.resolve(name);
        writeActual(name, actual);
        if (isRecording()) {
            if (vendor != DatabaseVendor.HSQLDB) {
                fail(
                        "Goldens are recorded on HSQLDB only; "
                                + RECORD_PROPERTY
                                + " was set while running on "
                                + vendor.id());
            }
            write(golden, actual);
            LOGGER.warn(
                    "Recorded golden file {} ({} lines)", golden.toAbsolutePath(), actual.size());
            return;
        }
        if (!Files.isRegularFile(golden)) {
            fail(
                    "Golden file "
                            + golden.toAbsolutePath()
                            + " is missing; record it on HSQLDB with -D"
                            + RECORD_PROPERTY
                            + "=true");
        }
        final List<String> expected = new ArrayList<>();
        for (final String line : read(golden)) {
            final String mapped = expectedLine.apply(line);
            if (mapped != null && !mapped.isEmpty()) {
                // A vendor difference may stand for several lines
                expected.addAll(Arrays.asList(mapped.split("\n", -1)));
            }
        }
        final List<String> diff = diff(expected, actual);
        if (!diff.isEmpty()) {
            final StringBuilder message = new StringBuilder();
            message.append(name)
                    .append(": ")
                    .append(diff.size())
                    .append(" lines differ from the golden recorded on HSQLDB (database ")
                    .append(vendor.id())
                    .append(", ")
                    .append(context)
                    .append("; - golden, + actual)\n");
            for (final String line : diff.subList(0, Math.min(diff.size(), MAX_REPORTED_LINES))) {
                message.append(line).append('\n');
            }
            if (diff.size() > MAX_REPORTED_LINES) {
                message.append("... ").append(diff.size() - MAX_REPORTED_LINES).append(" more\n");
            }
            message.append("Full output: ")
                    .append(ACTUAL_DIR.resolve(name).toAbsolutePath())
                    .append(". If the change is intended, re-record on HSQLDB with -D")
                    .append(RECORD_PROPERTY)
                    .append("=true; if it is a vendor difference, document it in the test.");
            jobSummary(message.toString());
            fail(message.toString());
        }
    }

    /**
     * The lines of golden file {@code name}, or an empty list when it does not exist yet or is
     * being recorded; for tests that need the golden to decide how to read their actual values.
     */
    public static List<String> expectedLines(String name) {
        final Path golden = SOURCE_DIR.resolve(name);
        if (isRecording() || !Files.isRegularFile(golden)) {
            return Collections.emptyList();
        }
        return read(golden);
    }

    /** Appends a failure report to the GitHub Actions job summary, when running there. */
    public static void jobSummary(String report) {
        final String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary == null || summary.isEmpty()) {
            return;
        }
        final String text =
                "\n<details open><summary>"
                        + escapeHtml(report.substring(0, Math.max(0, report.indexOf('\n'))))
                        + "</summary>\n\n```diff\n"
                        + report
                        + "\n```\n</details>\n";
        try {
            Files.write(
                    Paths.get(summary),
                    text.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Could not write the job summary", e);
        }
    }

    /**
     * Lines only in {@code expected} (prefixed "- ") or only in {@code actual} ("+ "), in order.
     * Golden files are sorted and line oriented, so a multiset difference is a readable diff.
     */
    static List<String> diff(List<String> expected, List<String> actual) {
        final Map<String, Integer> remaining = new HashMap<>();
        for (final String line : actual) {
            remaining.merge(line, 1, Integer::sum);
        }
        final List<String> result = new ArrayList<>();
        final Map<String, Integer> matched = new HashMap<>();
        for (final String line : expected) {
            final Integer count = remaining.get(line);
            if (count == null || count == 0) {
                result.add("- " + line);
            } else {
                remaining.put(line, count - 1);
                matched.merge(line, 1, Integer::sum);
            }
        }
        for (final String line : actual) {
            final Integer count = matched.get(line);
            if (count == null || count == 0) {
                result.add("+ " + line);
            } else {
                matched.put(line, count - 1);
            }
        }
        return result;
    }

    private static void writeActual(String name, List<String> actual) {
        write(ACTUAL_DIR.resolve(name), actual);
    }

    private static List<String> read(Path file) {
        try {
            final String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            if (text.isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.asList(text.split("\n", -1)).subList(0, text.split("\n", -1).length - 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, List<String> lines) {
        try {
            Files.createDirectories(file.getParent());
            final StringBuilder text = new StringBuilder();
            for (final String line : lines) {
                text.append(line).append('\n');
            }
            Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
