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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class DbMigrateCliTest {

    @Test
    public void parsesEveryOption() throws Exception {
        MigrationOptions o =
                DbMigrateCli.parse(
                        new String[] {
                            "--source-url",
                            "jdbc:postgresql://h/a",
                            "--source-user",
                            "u",
                            "--source-password=p",
                            "--target-url",
                            "jdbc:mariadb://h/b",
                            "--include",
                            "UP_*,X",
                            "--exclude",
                            "UP_RAW_EVENTS",
                            "--skip-raw-events",
                            "--batch-size",
                            "500",
                            "--fetch-size",
                            "2000",
                            "--threads",
                            "2",
                            "--dry-run",
                            "--clear-target",
                            "--no-verify",
                            "--missing-lob",
                            "skip-row",
                            "--report",
                            "r.json",
                            "--source-profile",
                            "5.17",
                            "--target-profile",
                            "5.17"
                        });
        assertEquals("jdbc:postgresql://h/a", o.source().url());
        assertEquals("u", o.source().user());
        assertEquals("[UP_*, X]", o.includes().toString());
        assertEquals("[UP_RAW_EVENTS]", o.excludes().toString());
        assertTrue(o.skipRawEvents());
        assertEquals(500, o.batchSize());
        assertEquals(2000, o.fetchSize());
        assertEquals(2, o.threads());
        assertEquals(MigrationOptions.Mode.DRY_RUN, o.mode());
        assertTrue(o.clearTarget());
        assertEquals(false, o.verify());
        assertEquals(MigrationOptions.MissingLobPolicy.SKIP_ROW, o.missingLobPolicy());
    }

    @Test
    public void readsPortalStylePropertiesFiles() throws Exception {
        Path p = Files.createTempFile("global", ".properties");
        Files.write(
                p,
                ("hibernate.connection.url=jdbc:hsqldb:hsql://localhost:8887/uPortal\n"
                                + "hibernate.connection.username=sa\nhibernate.connection.password=secret\n")
                        .getBytes(StandardCharsets.ISO_8859_1));
        try {
            MigrationOptions o =
                    DbMigrateCli.parse(
                            new String[] {
                                "--source-properties",
                                p.toString(),
                                "--target-url",
                                "jdbc:hsqldb:mem:t"
                            });
            assertEquals("jdbc:hsqldb:hsql://localhost:8887/uPortal", o.source().url());
            assertEquals("sa", o.source().user());
            assertEquals("secret", o.source().password());
            assertEquals(MigrationOptions.Mode.COPY, o.mode());
        } finally {
            Files.delete(p);
        }
    }

    @Test
    public void badArgumentsExitWithTwo() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(err, true);
        assertEquals(2, DbMigrateCli.run(new String[] {"--target-url", "x"}, ps, ps));
        assertTrue(err.toString().contains("--source-url"));
        assertEquals(
                2,
                DbMigrateCli.run(new String[] {"--source-url", "x", "--target-url", "x"}, ps, ps));
        assertEquals(2, DbMigrateCli.run(new String[] {"--bogus"}, ps, ps));
        assertEquals(
                2,
                DbMigrateCli.run(
                        new String[] {
                            "--source-url", "a", "--target-url", "b", "--dry-run", "--verify-only"
                        },
                        ps,
                        ps));
        assertEquals(0, DbMigrateCli.run(new String[] {"--help"}, ps, ps));
    }

    @Test
    public void reportUrlsHidePasswords() {
        ConnectionSpec s =
                new ConnectionSpec(
                        "t", "jdbc:sqlserver://h;databaseName=u;password=x1", "sa", "x1", null);
        assertEquals("jdbc:sqlserver://h;databaseName=u;password=***", s.safeUrl());
    }
}
