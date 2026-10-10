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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.catalog.GeneratorCatalog;
import org.apereo.portal.dbmigrate.rules.RuleSet;
import org.apereo.portal.dbmigrate.schema.Column;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.ForeignKey;
import org.apereo.portal.dbmigrate.schema.SchemaModel;
import org.apereo.portal.dbmigrate.schema.Table;
import org.junit.Test;

/** Planning on metadata alone: PostgreSQL 5.17 shape to MariaDB 5.17 shape and back. */
public class PlannerTest {

    private static final SchemaProfile PROFILE =
            new SchemaProfile(
                    "test",
                    RuleSet.parse("lob-oid UP_RAW_EVENTS.EVENT_DATA", "profile"),
                    GeneratorCatalog.empty()
                            .add("UP_USER_SEQ", 5, "UP_USER.USER_ID", "User")
                            .add("UP_RAW_EVENTS_SEQ", 1000, "UP_RAW_EVENTS.EVENT_ID", "Event"));

    private static Column col(String name, int type, String typeName, boolean nullable) {
        return new Column(name, type, typeName, 19, 0, nullable, false, false, 1);
    }

    private static SchemaModel postgres() {
        SchemaModel m =
                new SchemaModel(
                        Dialect.forVendor(Vendor.POSTGRESQL, null, "public"),
                        "PostgreSQL",
                        "16",
                        "pgjdbc");
        m.addTable(
                new Table(null, "public", "up_user")
                        .addColumn(col("user_id", Types.BIGINT, "int8", false))
                        .addColumn(col("user_name", Types.VARCHAR, "varchar", false))
                        .addColumn(col("parent_id", Types.BIGINT, "int8", true))
                        .primaryKey(Collections.singletonList("user_id"))
                        .addForeignKey(
                                new ForeignKey(
                                        "fk_parent",
                                        "up_user",
                                        Arrays.asList("parent_id"),
                                        "up_user",
                                        Arrays.asList("user_id"))));
        m.addTable(
                new Table(null, "public", "up_raw_events")
                        .addColumn(col("event_id", Types.BIGINT, "int8", false))
                        .addColumn(col("event_data", Types.VARCHAR, "text", false))
                        .primaryKey(Collections.singletonList("event_id")));
        m.addSequence("up_user_seq").addSequence("up_raw_events_seq");
        return m;
    }

    private static SchemaModel mariadb() {
        SchemaModel m =
                new SchemaModel(
                        Dialect.forVendor(Vendor.MARIADB, "uportal", null),
                        "MySQL",
                        "11.4.13-MariaDB",
                        "MariaDB Connector/J");
        m.addTable(
                new Table("uportal", null, "up_user")
                        .addColumn(col("USER_ID", Types.BIGINT, "BIGINT", false))
                        .addColumn(col("USER_NAME", Types.VARCHAR, "VARCHAR", false))
                        .addColumn(col("PARENT_ID", Types.BIGINT, "BIGINT", true))
                        .primaryKey(Collections.singletonList("USER_ID"))
                        .addForeignKey(
                                new ForeignKey(
                                        "FK_PARENT",
                                        "up_user",
                                        Arrays.asList("PARENT_ID"),
                                        "up_user",
                                        Arrays.asList("USER_ID"))));
        m.addTable(
                new Table("uportal", null, "up_raw_events")
                        .addColumn(col("EVENT_ID", Types.BIGINT, "BIGINT", false))
                        .addColumn(col("EVENT_DATA", Types.LONGVARCHAR, "LONGTEXT", false))
                        .primaryKey(Collections.singletonList("EVENT_ID")));
        for (String seq : Arrays.asList("up_user_seq", "up_raw_events_seq")) {
            m.addTable(
                    new Table("uportal", null, seq)
                            .addColumn(col("next_val", Types.BIGINT, "BIGINT", true)));
        }
        return m;
    }

    private static Planner planner(String rules) {
        return new Planner(
                PROFILE, PROFILE, RuleSet.parse(rules, "rules"), Collections.emptyList());
    }

    @Test
    public void postgresToMariadb() {
        MigrationPlan p = planner("").plan(postgres(), mariadb());
        assertEquals(p.errors().toString(), 0, p.errors().size());
        assertEquals(2, p.tables().size());
        TableMapping events = p.table("UP_RAW_EVENTS");
        assertTrue(events.columns().get(1).pair().source().postgresOidText());
        assertFalse(events.columns().get(1).pair().target().postgresLargeObject());
        // The self-reference is loaded in a second pass
        assertEquals(1, p.table("UP_USER").deferredForeignKeys().size());
        // Generators: sequences on the source, one-row tables on the target
        assertEquals(2, p.generators().size());
        GeneratorTarget users = p.generators().get(1);
        assertEquals("up_user_seq", users.name());
        assertEquals(GeneratorTarget.Storage.TABLE, users.targetStorage());
        assertEquals(GeneratorTarget.Storage.SEQUENCE, users.sourceStorage());
        assertEquals(5, users.allocationSize());
        assertEquals("[up_user.USER_ID]", users.served().toString());
        assertTrue(p.targetOnlyTables().isEmpty());
    }

    @Test
    public void mariadbToPostgresTreatsSequenceTablesAsGenerators() {
        MigrationPlan p = planner("").plan(mariadb(), postgres());
        assertEquals(p.errors().toString(), 0, p.errors().size());
        assertEquals(Arrays.asList("up_raw_events_seq", "up_user_seq"), p.generatorTables());
        assertEquals(GeneratorTarget.Storage.SEQUENCE, p.generators().get(0).targetStorage());
        assertEquals(GeneratorTarget.Storage.TABLE, p.generators().get(0).sourceStorage());
        assertTrue(p.table("UP_RAW_EVENTS").columns().get(1).pair().target().postgresOidText());
    }

    @Test
    public void unmappedTablesAndColumnsFailTheplan() {
        SchemaModel source = postgres();
        source.addTable(
                new Table(null, "public", "up_extra")
                        .addColumn(col("id", Types.BIGINT, "int8", false)));
        source.table("up_user").addColumn(col("dropped", Types.VARCHAR, "varchar", true));
        MigrationPlan p = planner("").plan(source, mariadb());
        assertEquals(p.errors().toString(), 2, p.errors().size());
        assertTrue(p.errors().get(0).contains("up_extra"));
        assertTrue(p.errors().get(1).contains("up_user.dropped"));

        MigrationPlan ok =
                planner("exclude up_ext*\nskip-column UP_USER.DROPPED").plan(source, mariadb());
        assertEquals(ok.errors().toString(), 0, ok.errors().size());
        assertEquals(Collections.singletonList("up_extra"), ok.excludedTables());
    }

    @Test
    public void targetColumnsNobodyFillsMustBeNullable() {
        SchemaModel target = mariadb();
        target.table("up_user").addColumn(col("NEW_REQUIRED", Types.VARCHAR, "VARCHAR", false));
        MigrationPlan p = planner("").plan(postgres(), target);
        assertEquals(1, p.errors().size());
        assertTrue(p.errors().get(0).contains("NEW_REQUIRED"));
        MigrationPlan derived =
                planner("derive-column UP_USER.NEW_REQUIRED constant value=x")
                        .plan(postgres(), target);
        assertEquals(derived.errors().toString(), 0, derived.errors().size());
    }

    @Test
    public void renamesApply() {
        SchemaModel target = mariadb();
        Table renamed = new Table("uportal", null, "UP_PERSON");
        for (Column c : target.table("up_user").columns()) {
            renamed.addColumn(
                    c.name().equals("USER_NAME")
                            ? col("LOGIN", Types.VARCHAR, "VARCHAR", false)
                            : c);
        }
        renamed.primaryKey(Collections.singletonList("USER_ID"));
        SchemaModel t = new SchemaModel(target.dialect(), "MySQL", "11.4", "x");
        t.addTable(renamed)
                .addTable(target.table("up_raw_events"))
                .addTable(target.table("up_user_seq"))
                .addTable(target.table("up_raw_events_seq"));
        MigrationPlan p =
                planner("rename-table up_user UP_PERSON\nrename-column UP_USER.USER_NAME LOGIN")
                        .plan(postgres(), t);
        assertEquals(p.errors().toString(), 0, p.errors().size());
        assertEquals("UP_PERSON", p.table("UP_USER").target().name());
    }
}
