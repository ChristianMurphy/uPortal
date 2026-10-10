package org.apereo.portal.dbmigrate;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apereo.portal.dbmigrate.it.DbServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Copies the {@link Fixture} from an old server to current ones, as a 6.0 upgrade does (MOD-D25):
 * PostgreSQL 16 to MariaDB 12.3 to PostgreSQL 18, the last into a time-partitioned UP_RAW_EVENTS
 * (Testcontainers, so Docker is needed). It checks what only real databases show: PostgreSQL large
 * objects (OID-in-text, MOD-I24) resolved and re-created, sequences versus one-row sequence tables
 * (MOD-I23), MySQL DATETIME precision, partition routing, and the verification across vendors.
 */
public class CrossVendorCopyIT {

    private static PostgreSQLContainer oldPostgres;
    private static MariaDBContainer mariadb;
    private static PostgreSQLContainer newPostgres;
    private static Path rules;

    @BeforeClass
    public static void start() throws Exception {
        // Pinned by digest (db-images.properties)
        oldPostgres = new PostgreSQLContainer(DbServer.pinned("postgres16", "postgres"));
        newPostgres = new PostgreSQLContainer(DbServer.pinned("postgres", "postgres"));
        mariadb =
                new MariaDBContainer(DbServer.pinned("mariadb", "mariadb"))
                        .withCommand(
                                "--character-set-server=utf8mb4", "--lower-case-table-names=1");
        oldPostgres.start();
        newPostgres.start();
        mariadb.start();
        rules = Files.createTempFile("dbmig", ".rules");
        Files.write(rules, Fixture.RULES.getBytes(StandardCharsets.UTF_8));
    }

    @AfterClass
    public static void stop() throws Exception {
        for (JdbcDatabaseContainer<?> c : Arrays.asList(oldPostgres, newPostgres, mariadb)) {
            if (c != null) {
                c.stop();
            }
        }
        if (rules != null) {
            Files.deleteIfExists(rules);
        }
    }

    /** A database on one of the servers: URL plus credentials. */
    private static final class Db {
        final String url;
        final String user;
        final String password;

        Db(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        Connection connect() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }
    }

    private static Db postgresDb(PostgreSQLContainer server, String name) throws SQLException {
        try (Connection c =
                        DriverManager.getConnection(
                                server.getJdbcUrl(), server.getUsername(), server.getPassword());
                Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + name);
        }
        return new Db(
                server.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"),
                server.getUsername(),
                server.getPassword());
    }

    private static Db mariadbDb(String name) throws SQLException {
        String root = mariadb.getJdbcUrl();
        try (Connection c = DriverManager.getConnection(root, "root", mariadb.getPassword());
                Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + name);
            st.execute("GRANT ALL ON " + name + ".* TO '" + mariadb.getUsername() + "'@'%'");
        }
        return new Db(
                root.replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"),
                mariadb.getUsername(),
                mariadb.getPassword());
    }

    private static int copy(Db source, Db target, String... extra) {
        List<String> args =
                new ArrayList<>(
                        Arrays.asList(
                                "--source-url", source.url,
                                "--source-user", source.user,
                                "--source-password", source.password,
                                "--target-url", target.url,
                                "--target-user", target.user,
                                "--target-password", target.password,
                                "--rules", rules.toString(),
                                "--batch-size", "4"));
        args.addAll(Arrays.asList(extra));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(out, true, StandardCharsets.UTF_8);
        int status = DbMigrateCli.run(args.toArray(new String[0]), ps, ps);
        System.out.println(out.toString(StandardCharsets.UTF_8));
        return status;
    }

    private static Db postgresFixture(String name, boolean rows) throws SQLException {
        Db db = postgresDb(oldPostgres, name);
        try (Connection c = db.connect()) {
            c.setAutoCommit(false); // large objects need a transaction
            Fixture.createSchema(c, Vendor.POSTGRESQL);
            if (rows) {
                Fixture.insertRows(c, Vendor.POSTGRESQL);
            }
        }
        return db;
    }

    private static Db mariadbSchema(String name) throws SQLException {
        Db db = mariadbDb(name);
        try (Connection c = db.connect()) {
            Fixture.createSchema(c, Vendor.MARIADB);
        }
        return db;
    }

    @Test
    public void oldPostgresToNewMariadbToNewPartitionedPostgres() throws Exception {
        Db source = postgresFixture("src", true);
        Db mdb = mariadbSchema("tgt");

        assertEquals(0, copy(source, mdb));
        try (Connection c = mdb.connect();
                Statement st = c.createStatement()) {
            // The large object's text, not its OID
            try (ResultSet rs =
                    st.executeQuery("SELECT EVENT_DATA FROM UP_RAW_EVENTS WHERE EVENT_ID = 1001")) {
                rs.next();
                assertEquals("{\"@c\":\".LoginEvent\",\"id\":1,\"user\":\"ü1\"}", rs.getString(1));
            }
            // DATETIME keeps seconds; the DST-gap wall-clock hour is unchanged
            try (ResultSet rs =
                    st.executeQuery("SELECT CREATED FROM UP_THING WHERE THING_ID = 1")) {
                rs.next();
                assertEquals(Fixture.DST_GAP.withNano(0), rs.getObject(1, LocalDateTime.class));
            }
            assertEquals(26, scalar(st, "SELECT next_val FROM UP_THING_SEQ"));
            assertEquals(6, scalar(st, "SELECT next_val FROM UP_THING_TYPE_SEQ"));
            assertEquals(2030, scalar(st, "SELECT next_val FROM UP_RAW_EVENTS_SEQ"));
        }

        // A 6.0-style target: UP_RAW_EVENTS partitioned by month; rows go through the parent
        Db back = postgresDb(newPostgres, "back");
        try (Connection c = back.connect();
                Statement st = c.createStatement()) {
            Fixture.createSchema(c, Vendor.POSTGRESQL);
            st.execute("DROP TABLE up_raw_events");
            st.execute(
                    "CREATE TABLE up_raw_events (event_id bigint NOT NULL, event_data text NOT NULL,"
                            + " \"timestamp\" timestamp NOT NULL, aggregated boolean,"
                            + " PRIMARY KEY (event_id, \"timestamp\")) PARTITION BY RANGE (\"timestamp\")");
            st.execute(
                    "CREATE TABLE up_raw_events_2026_10 PARTITION OF up_raw_events"
                            + " FOR VALUES FROM ('2026-10-01') TO ('2026-11-01')");
            st.execute("CREATE TABLE up_raw_events_other PARTITION OF up_raw_events DEFAULT");
        }
        assertEquals(0, copy(mdb, back, "--threads", "1"));
        try (Connection c = back.connect();
                Statement st = c.createStatement()) {
            assertEquals(30, scalar(st, "SELECT count(*) FROM up_raw_events_2026_10"));
            assertEquals(
                    30,
                    scalar(
                            st,
                            "SELECT count(*) FROM up_raw_events e JOIN pg_largeobject_metadata m"
                                    + " ON m.oid = CAST(e.event_data AS oid)"));
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT convert_from(lo_get(CAST(event_data AS oid)), 'UTF8')"
                                    + " FROM up_raw_events WHERE event_id = 1030")) {
                rs.next();
                assertEquals(
                        "{\"@c\":\".LoginEvent\",\"id\":30,\"user\":\"ü30\"}", rs.getString(1));
            }
            assertEquals(26, scalar(st, "SELECT nextval('up_thing_seq')"));
        }
        // The round trip still verifies against the original (UP_THING lost sub-second precision
        // in MariaDB's DATETIME, so it is compared on the MariaDB leg only)
        assertEquals(0, copy(source, back, "--verify-only", "--exclude", "UP_THING"));
    }

    @Test
    public void missingLargeObjectsFailUnlessSkipped() throws Exception {
        Db source = postgresFixture("lost", true);
        try (Connection c = source.connect();
                Statement st = c.createStatement()) {
            // What vacuumlo does to OIDs kept in text columns (MOD-I24)
            st.execute(
                    "SELECT lo_unlink(CAST(event_data AS oid)) FROM up_raw_events"
                            + " WHERE event_id IN (1001, 1002)");
        }
        Db target = mariadbSchema("lost");
        assertEquals(2, copy(source, target));
        assertEquals(0, copy(source, target, "--missing-lob", "skip-row"));
        try (Connection c = target.connect();
                Statement st = c.createStatement()) {
            assertEquals(28, scalar(st, "SELECT count(*) FROM UP_RAW_EVENTS"));
        }
    }

    @Test
    public void undeclaredLargeObjectColumnsFailThePlan() throws Exception {
        Db source = postgresFixture("undeclared", true);
        try (Connection c = source.connect();
                Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute(
                    "UPDATE up_thing SET body = CAST(lo_from_bytea(0, 'x') AS text)"
                            + " WHERE thing_id = 9");
            c.commit();
        }
        assertEquals(2, copy(source, mariadbSchema("undeclared"), "--dry-run"));
    }

    private static long scalar(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
