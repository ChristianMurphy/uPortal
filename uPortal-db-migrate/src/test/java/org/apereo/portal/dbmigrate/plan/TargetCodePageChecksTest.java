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
package org.apereo.portal.dbmigrate.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apereo.portal.dbmigrate.Fixture;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.SchemaReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The plan refuses source text that a target column's character set can't hold (MOD-I202). HSQLDB
 * holds every character, so the target encodings are given here; CopyFaultIT reads them from the
 * real servers.
 */
public class TargetCodePageChecksTest {

    private static final AtomicInteger RUN = new AtomicInteger();
    private static final TextEncoding CP1252 = TextEncoding.forSqlServerCodePage(1252);

    private SingleConnectionDataSource source;
    private SingleConnectionDataSource target;

    @Before
    public void setUp() throws Exception {
        int n = RUN.incrementAndGet();
        source =
                new SingleConnectionDataSource("jdbc:hsqldb:mem:dbmig_cp_src_" + n, "SA", "", true);
        target =
                new SingleConnectionDataSource("jdbc:hsqldb:mem:dbmig_cp_tgt_" + n, "SA", "", true);
        try (Connection s = source.getConnection();
                Connection t = target.getConnection()) {
            Fixture.createSchema(s, Vendor.HSQLDB);
            Fixture.insertRows(s, Vendor.HSQLDB);
            Fixture.createSchema(t, Vendor.HSQLDB);
        }
    }

    @After
    public void tearDown() throws Exception {
        for (SingleConnectionDataSource ds : List.of(source, target)) {
            try (Statement st = ds.getConnection().createStatement()) {
                st.execute("SHUTDOWN");
            }
            ds.destroy();
        }
    }

    private MigrationPlan plan() {
        SchemaProfile profile = SchemaProfile.load("5.17");
        org.apereo.portal.dbmigrate.rules.RuleSet rules =
                org.apereo.portal.dbmigrate.rules.RuleSet.parse(Fixture.RULES, "fixture");
        profile.generators().addRules(rules);
        return new Planner(profile, profile, rules, Collections.emptyList())
                .plan(SchemaReader.read(source, null), SchemaReader.read(target, null));
    }

    /** TITLE in windows-1252 on the target, everything else Unicode; the source all Unicode. */
    private MigrationPlan check() {
        MigrationPlan plan = plan();
        TargetCodePageChecks.check(
                plan,
                new JdbcTemplate(source),
                (table, column) ->
                        "UP_THING".equals(table) && "TITLE".equals(column.name()) ? CP1252 : null,
                (table, column) -> null);
        return plan;
    }

    @Test
    public void textTheTargetHoldsPasses() {
        // The fixture's titles are "Thing <n> é" and one empty string: all windows-1252
        MigrationPlan plan = check();
        assertEquals(plan.errors().toString(), 0, plan.errors().size());
    }

    @Test
    public void anEmojiTheTargetCannotHoldFailsThePlanWithTheRowKey() throws Exception {
        new JdbcTemplate(source)
                .update("UPDATE UP_THING SET TITLE = 'Thing 9 🌱' WHERE THING_ID = 9");
        new JdbcTemplate(source)
                .update("UPDATE UP_THING SET TITLE = 'Thing 12 研究' WHERE THING_ID = 12");
        MigrationPlan plan = check();
        assertEquals(plan.errors().toString(), 1, plan.errors().size());
        String error = plan.errors().get(0);
        assertTrue(error, error.contains("UP_THING.TITLE holds windows-1252 only"));
        assertTrue(error, error.contains("2 source rows of UP_THING.TITLE"));
        assertTrue(error, error.contains("THING_ID = 9 has U+1F331"));
    }

    @Test
    public void aSourceColumnInTheSameCodePageIsNotRead() throws Exception {
        new JdbcTemplate(source)
                .update("UPDATE UP_THING SET TITLE = 'Thing 9 🌱' WHERE THING_ID = 9");
        MigrationPlan plan = plan();
        TargetCodePageChecks.check(
                plan,
                new JdbcTemplate(source),
                (table, column) -> "TITLE".equals(column.name()) ? CP1252 : null,
                (table, column) -> "TITLE".equals(column.name()) ? CP1252 : null);
        // A source in windows-1252 can't hold the emoji either; the check trusts its catalog
        assertEquals(plan.errors().toString(), 0, plan.errors().size());
    }

    @Test
    public void hsqldbHoldsEveryCharacter() throws Exception {
        new JdbcTemplate(source)
                .update("UPDATE UP_THING SET TITLE = 'Thing 9 🌱' WHERE THING_ID = 9");
        MigrationPlan plan = plan();
        TargetCodePageChecks.check(plan, new JdbcTemplate(source), new JdbcTemplate(target));
        assertEquals(plan.errors().toString(), 0, plan.errors().size());
        assertTrue(
                plan.warnings().toString(),
                plan.warnings().stream().noneMatch(w -> w.contains("character set")));
    }
}
