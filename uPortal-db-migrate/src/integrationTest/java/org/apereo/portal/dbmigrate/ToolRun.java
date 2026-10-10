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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apereo.portal.dbmigrate.it.TestDatabase;
import org.apereo.portal.dbmigrate.report.MigrationReport;

/** One run of the copy tool, in this JVM, with its exit status, console output and report. */
public final class ToolRun {

    public final int status;
    public final String output;

    /** Null when the arguments were rejected. */
    public final MigrationReport report;

    private ToolRun(int status, String output, MigrationReport report) {
        this.status = status;
        this.output = output;
        this.report = report;
    }

    /** Runs the tool from {@code source} to {@code target} with the rules text and options. */
    public static ToolRun run(
            TestDatabase source, TestDatabase target, String rules, String... options)
            throws IOException {
        List<String> args = new ArrayList<>(source.args("source"));
        args.addAll(target.args("target"));
        Path rulesFile = null;
        try {
            if (rules != null) {
                rulesFile = Files.createTempFile("dbmig-it", ".rules");
                Files.write(rulesFile, rules.getBytes(StandardCharsets.UTF_8));
                args.add("--rules");
                args.add(rulesFile.toString());
            }
            args.addAll(Arrays.asList(options));
            return run(args.toArray(new String[0]));
        } finally {
            if (rulesFile != null) {
                Files.deleteIfExists(rulesFile);
            }
        }
    }

    /** Runs the tool with a full command line. */
    public static ToolRun run(String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        MigrationOptions options;
        try {
            options = DbMigrateCli.parse(args);
        } catch (IllegalArgumentException | IOException e) {
            return new ToolRun(DbMigrate.PLAN_ERROR, e.getMessage(), null);
        }
        DbMigrate tool = new DbMigrate(options, out);
        int status = tool.run();
        ToolRun run = new ToolRun(status, bytes.toString(StandardCharsets.UTF_8), tool.report());
        // Into the test report, for whoever reads a failure
        System.out.println(run);
        return run;
    }

    /** The report entry of a table (by source name), or null. */
    public MigrationReport.TableEntry table(String sourceName) {
        if (report == null) {
            return null;
        }
        for (MigrationReport.TableEntry e : report.tables) {
            if (e.source.equalsIgnoreCase(sourceName)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "exit " + status + "\n" + output;
    }
}
