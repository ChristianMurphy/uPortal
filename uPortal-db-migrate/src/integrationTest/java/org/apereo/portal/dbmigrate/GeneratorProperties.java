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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.RandomDistribution;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apereo.portal.dbmigrate.it.DbServer;
import org.apereo.portal.dbmigrate.it.DbServer.Kind;
import org.apereo.portal.dbmigrate.it.TestDatabase;

/**
 * After a copy, the target's ID generators hand out only new ids, whatever ids the source holds and
 * wherever the source's generators stood (behind the data after imports with explicit ids, or far
 * ahead): Hibernate's next allocations, under the POOLED optimizer (5.17's entity sequences) as
 * under POOLED_LO (the UP_SEQUENCE counters since 5.17.10, MOD-I72), insert without a duplicate key
 * on every target server of the run, through a sequence or a one-row table.
 */
@AddLifecycleHook(PropertySettings.class)
class GeneratorProperties {

    /** Ids of one table and the source generator's next value. */
    static final class Ids {
        final Set<Long> ids;
        final long sourceNext;

        Ids(Set<Long> ids, long sourceNext) {
            this.ids = ids;
            this.sourceNext = sourceNext;
        }

        long max() {
            return ids.isEmpty() ? 0 : ((TreeSet<Long>) ids).last();
        }

        @Override
        public String toString() {
            return "ids " + ids + ", source next " + sourceNext;
        }
    }

    @Provide
    Arbitrary<Ids> ids() {
        Arbitrary<Long> id =
                Arbitraries.frequencyOf(
                        net.jqwik.api.Tuple.of(3, Arbitraries.longs().between(1, 50)),
                        net.jqwik.api.Tuple.of(
                                1,
                                Arbitraries.longs()
                                        .between(1, 1L << 40)
                                        .withDistribution(RandomDistribution.uniform())));
        // The source is HSQLDB, whose sequences (as 5.17 creates them) are INTEGERs
        Arbitrary<Long> next =
                Arbitraries.frequencyOf(
                        net.jqwik.api.Tuple.of(2, Arbitraries.longs().between(1, 60)),
                        net.jqwik.api.Tuple.of(
                                1,
                                Arbitraries.longs()
                                        .between(1, Integer.MAX_VALUE - 10_000)
                                        .withDistribution(RandomDistribution.uniform())));
        return Combinators.combine(id.set().ofMinSize(1).ofMaxSize(6), next)
                .as((s, n) -> new Ids(new TreeSet<>(s), n));
    }

    @Property(tries = 4, shrinking = ShrinkingMode.BOUNDED)
    void theNextAllocationsAfterACopyNeverCollide(
            @ForAll("ids") Ids types, @ForAll("ids") Ids things, @ForAll("ids") Ids events)
            throws Exception {
        List<String> problems = new ArrayList<>();
        for (Kind kind : DbServer.enabled()) {
            if (kind != Kind.HSQLDB) {
                problems.addAll(check(kind, types, things, events));
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private static List<String> check(Kind kind, Ids types, Ids things, Ids events)
            throws Exception {
        List<String> problems = new ArrayList<>();
        try (TestDatabase s = DbServer.shared(Kind.HSQLDB).create("gsrc");
                TestDatabase t = DbServer.shared(kind).create("gtgt")) {
            try (Connection c = s.connect()) {
                Fixture.createSchema(c, Vendor.HSQLDB);
            }
            try (Connection c = t.connect()) {
                Fixture.createSchema(c, kind.vendor());
            }
            long type = types.ids.iterator().next();
            for (long id : types.ids) {
                insertType(s, id);
            }
            for (long id : things.ids) {
                insertThing(s, id, type);
            }
            for (long id : events.ids) {
                insertEvent(s, id);
            }
            s.execute(
                    "ALTER SEQUENCE UP_THING_TYPE_SEQ RESTART WITH " + types.sourceNext,
                    "ALTER SEQUENCE UP_THING_SEQ RESTART WITH " + things.sourceNext,
                    "ALTER SEQUENCE UP_RAW_EVENTS_SEQ RESTART WITH " + events.sourceNext);
            ToolRun run = ToolRun.run(s, t, Fixture.RULES);
            assertEquals(run.toString(), DbMigrate.OK, run.status);

            problems.addAll(
                    allocate(t, kind, "UP_THING_TYPE_SEQ", 1, types, id -> insertType(t, id)));
            problems.addAll(
                    allocate(t, kind, "UP_THING_SEQ", 5, things, id -> insertThing(t, id, type)));
            problems.addAll(
                    allocate(t, kind, "UP_RAW_EVENTS_SEQ", 1000, events, id -> insertEvent(t, id)));
        }
        return problems;
    }

    interface Insert {
        void id(long id) throws SQLException;
    }

    /**
     * Takes two values from a target generator the way Hibernate does, and inserts the lowest and
     * highest id each value hands out under POOLED (v-allocation+1 .. v) and POOLED_LO (v ..
     * v+allocation-1).
     */
    private static List<String> allocate(
            TestDatabase t, Kind kind, String generator, int allocation, Ids copied, Insert insert)
            throws SQLException {
        List<String> problems = new ArrayList<>();
        Set<Long> tried = new TreeSet<>();
        for (int draw = 0; draw < 2; draw++) {
            long v = next(t, kind, generator, allocation);
            long[] candidates = {v - allocation + 1, v, v + allocation - 1};
            for (long id : candidates) {
                if (id <= copied.max()) {
                    problems.add(
                            kind.id()
                                    + " "
                                    + generator
                                    + ": value "
                                    + v
                                    + " (allocation "
                                    + allocation
                                    + ") hands out id "
                                    + id
                                    + ", not above the copied "
                                    + copied);
                    continue;
                }
                if (tried.add(id)) {
                    try {
                        insert.id(id);
                    } catch (SQLException e) {
                        problems.add(
                                kind.id() + " " + generator + ": inserting id " + id + ": " + e);
                    }
                }
            }
        }
        return problems;
    }

    /** One value from the generator, as Hibernate gets it on each vendor. */
    private static long next(TestDatabase t, Kind kind, String generator, int allocation)
            throws SQLException {
        if (Fixture.usesSequenceTables(kind.vendor())) {
            // Hibernate's TableStructure: read the row, then move it on by the increment
            try (Connection c = t.connect()) {
                c.setAutoCommit(false);
                long v;
                try (Statement st = c.createStatement();
                        ResultSet rs =
                                st.executeQuery(
                                        "SELECT next_val FROM " + generator + " FOR UPDATE")) {
                    rs.next();
                    v = rs.getLong(1);
                }
                try (PreparedStatement ps =
                        c.prepareStatement(
                                "UPDATE " + generator + " SET next_val = ? WHERE next_val = ?")) {
                    ps.setLong(1, v + allocation);
                    ps.setLong(2, v);
                    assertEquals(1, ps.executeUpdate());
                }
                c.commit();
                return v;
            }
        }
        switch (kind) {
            case POSTGRES:
                return t.scalar("SELECT nextval('" + generator.toLowerCase(Locale.ROOT) + "')");
            case ORACLE:
                return t.scalar("SELECT " + generator + ".NEXTVAL FROM DUAL");
            default:
                return t.scalar("SELECT NEXT VALUE FOR " + generator);
        }
    }

    private static void insertType(TestDatabase db, long id) throws SQLException {
        try (Connection c = db.connect();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO UP_THING_TYPE (TYPE_ID, NAME, ENABLED)"
                                        + " VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, "type " + id);
            ps.setBoolean(3, true);
            ps.executeUpdate();
        }
    }

    private static void insertThing(TestDatabase db, long id, long type) throws SQLException {
        try (Connection c = db.connect();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO UP_THING (THING_ID, TYPE_ID, CREATED)"
                                        + " VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setLong(2, type);
            ps.setObject(3, LocalDateTime.of(2026, 10, 10, 12, 0));
            ps.executeUpdate();
        }
    }

    private static void insertEvent(TestDatabase db, long id) throws SQLException {
        String ts = Fixture.timestampColumn(db.vendor());
        if (db.vendor() == Vendor.POSTGRESQL) {
            ts = ts.toLowerCase(Locale.ROOT);
        }
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps =
                    c.prepareStatement(
                            "INSERT INTO UP_RAW_EVENTS (EVENT_ID, EVENT_DATA, "
                                    + ts
                                    + ") VALUES (?, "
                                    + (db.vendor() == Vendor.POSTGRESQL
                                            ? "CAST(lo_from_bytea(0, CAST(? AS bytea)) AS text)"
                                            : "?")
                                    + ", ?)")) {
                ps.setLong(1, id);
                ps.setString(2, "{\"id\":" + id + "}");
                ps.setObject(3, LocalDateTime.of(2026, 10, 10, 12, 0));
                ps.executeUpdate();
            }
            c.commit();
        }
    }
}
