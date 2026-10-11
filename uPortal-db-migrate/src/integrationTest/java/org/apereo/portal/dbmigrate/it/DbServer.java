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
package org.apereo.portal.dbmigrate.it;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apereo.portal.dbmigrate.Vendor;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A database server for the copy tool's integration tests: a Testcontainers container of one of the
 * 6.0 runtime servers (images in {@code db-images.properties}), or in-memory HSQLDB. Each test
 * creates its own empty databases on it ({@link #create(String)}), so tests and property tries
 * never see each other's tables.
 *
 * <p>The servers a run uses come from the system property {@code dbmigrate.it.vendors} (a comma
 * separated list of {@link Kind} names, default {@code postgres,mariadb}); HSQLDB is always there.
 * Shared servers are started on first use and stopped when the JVM exits (Testcontainers' Ryuk
 * removes them even if it crashes).
 */
public final class DbServer {

    /** The kinds of server. */
    public enum Kind {
        HSQLDB(Vendor.HSQLDB),
        POSTGRES(Vendor.POSTGRESQL),
        MARIADB(Vendor.MARIADB),
        MYSQL(Vendor.MYSQL),
        SQLSERVER(Vendor.SQLSERVER),
        ORACLE(Vendor.ORACLE);

        private final Vendor vendor;

        Kind(Vendor vendor) {
            this.vendor = vendor;
        }

        public Vendor vendor() {
            return vendor;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean isMySqlFamily() {
            return vendor.isMySqlFamily();
        }
    }

    /**
     * InnoDB gives up a lock wait after 50 s by default; the fault tests hold a copy on a lock
     * until they inject the fault, which a loaded test machine may take longer to do.
     */
    private static final String LOCK_WAIT = "--innodb-lock-wait-timeout=600";

    private static final Map<Kind, DbServer> SHARED = new EnumMap<>(Kind.class);
    private static final AtomicInteger NAMES = new AtomicInteger();

    private final Kind kind;
    private final JdbcDatabaseContainer<?> container;

    private DbServer(Kind kind, JdbcDatabaseContainer<?> container) {
        this.kind = kind;
        this.container = container;
    }

    /** The server kinds this run tests, in a stable order; HSQLDB first. */
    public static List<Kind> enabled() {
        String value = System.getProperty("dbmigrate.it.vendors", "postgres,mariadb");
        List<Kind> kinds = new ArrayList<>();
        kinds.add(Kind.HSQLDB);
        for (Kind k : Kind.values()) {
            for (String v : value.split(",")) {
                if (k != Kind.HSQLDB && k.id().equals(v.trim().toLowerCase(Locale.ROOT))) {
                    kinds.add(k);
                }
            }
        }
        return kinds;
    }

    public static boolean isEnabled(Kind k) {
        return enabled().contains(k);
    }

    /** The shared server of a kind, started on first use. */
    public static synchronized DbServer shared(Kind kind) {
        DbServer s = SHARED.get(kind);
        if (s == null) {
            s = start(kind);
            SHARED.put(kind, s);
        }
        return s;
    }

    /** A server of its own, for a test that stops it; the caller stops it. */
    public static DbServer dedicated(Kind kind) {
        return start(kind);
    }

    private static DbServer start(Kind kind) {
        if (kind == Kind.HSQLDB) {
            return new DbServer(kind, null);
        }
        JdbcDatabaseContainer<?> c;
        switch (kind) {
            case POSTGRES:
                c = new PostgreSQLContainer(image(kind, "postgres"));
                break;
            case MARIADB:
                // As uPortal-start runs it (tests/db/vendors/mariadb-12.3.properties)
                c =
                        new MariaDBContainer(image(kind, "mariadb"))
                                .withCommand(
                                        "--character-set-server=utf8mb4",
                                        "--lower-case-table-names=1",
                                        LOCK_WAIT);
                break;
            case MYSQL:
                c =
                        new MySQLContainer(image(kind, "mysql"))
                                .withCommand("--lower-case-table-names=1", LOCK_WAIT);
                break;
            case SQLSERVER:
                c =
                        new MSSQLServerContainer(image(kind, "mcr.microsoft.com/mssql/server"))
                                .acceptLicense()
                                .withPassword("uPortal-Copy-1");
                break;
            case ORACLE:
                c = new OracleContainer(image(kind, "gvenzl/oracle-free")).withPassword("uportal");
                break;
            default:
                throw new IllegalArgumentException(kind.toString());
        }
        c.withStartupTimeout(Duration.ofMinutes(kind == Kind.ORACLE ? 8 : 4));
        c.start();
        return new DbServer(kind, c);
    }

    /**
     * The pinned image of a kind. Testcontainers does not accept {@code name:tag@digest}, so the
     * tag (kept in the file for Renovate and for people) is dropped and the digest pulled.
     */
    static DockerImageName image(Kind kind, String repository) {
        return pinned(kind.id(), repository);
    }

    /**
     * An image of {@code db-images.properties} by its key ({@code <key>.image}), e.g. an old server
     * that is only a migration source.
     */
    public static DockerImageName pinned(String key, String repository) {
        String ref = images().getProperty(key + ".image");
        if (ref == null) {
            throw new IllegalStateException("No image " + key + " in db-images.properties");
        }
        String byDigest = ref.replaceFirst(":[^/@]+@", "@");
        return DockerImageName.parse(byDigest).asCompatibleSubstituteFor(repository);
    }

    private static Properties images() {
        Properties p = new Properties();
        try (InputStream in = DbServer.class.getResourceAsStream("/db-images.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    public Kind kind() {
        return kind;
    }

    /** Stops a dedicated server (the container is removed). */
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    /** A new, empty database (a schema of its own user on Oracle). */
    public TestDatabase create(String prefix) throws SQLException {
        String name = (prefix + "_" + NAMES.incrementAndGet()).toLowerCase(Locale.ROOT);
        switch (kind) {
            case HSQLDB:
                return new TestDatabase(this, name, "jdbc:hsqldb:mem:" + name, "SA", "");
            case POSTGRES:
                admin("CREATE DATABASE " + name);
                return new TestDatabase(
                        this,
                        name,
                        withDatabase(container.getJdbcUrl(), name),
                        container.getUsername(),
                        container.getPassword());
            case MARIADB:
            case MYSQL:
                admin(
                        "CREATE DATABASE " + name + " CHARACTER SET utf8mb4",
                        "GRANT ALL ON " + name + ".* TO '" + container.getUsername() + "'@'%'");
                return new TestDatabase(
                        this,
                        name,
                        withDatabase(container.getJdbcUrl(), name)
                                + (kind == Kind.MARIADB ? mariadbParams() : ""),
                        container.getUsername(),
                        container.getPassword());
            case SQLSERVER:
                admin("CREATE DATABASE " + name);
                return new TestDatabase(
                        this,
                        name,
                        container.getJdbcUrl() + ";databaseName=" + name,
                        container.getUsername(),
                        container.getPassword());
            case ORACLE:
                admin(
                        "CREATE USER " + name + " IDENTIFIED BY uportal QUOTA UNLIMITED ON USERS",
                        "GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO " + name);
                return new TestDatabase(this, name, container.getJdbcUrl(), name, "uportal");
            default:
                throw new IllegalStateException(kind.toString());
        }
    }

    /** As uPortal-start connects: MariaDB reported as MySQL (Hibernate 4.3 needs that). */
    private static String mariadbParams() {
        return "?useMysqlMetadata=true";
    }

    private static String withDatabase(String url, String name) {
        return url.replaceFirst("/[^/?;]+(\\?|$)", "/" + name + "$1");
    }

    /** Removes a database created by {@link #create(String)}, ending its sessions first. */
    void drop(TestDatabase db) throws SQLException {
        switch (kind) {
            case HSQLDB:
                try (Connection c = db.connect();
                        Statement st = c.createStatement()) {
                    st.execute("SHUTDOWN");
                }
                break;
            case POSTGRES:
                admin("DROP DATABASE IF EXISTS " + db.name() + " WITH (FORCE)");
                break;
            case MARIADB:
            case MYSQL:
                killSessions(db);
                admin("DROP DATABASE IF EXISTS " + db.name());
                break;
            case SQLSERVER:
                admin(
                        "ALTER DATABASE " + db.name() + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE",
                        "DROP DATABASE " + db.name());
                break;
            case ORACLE:
                killSessions(db);
                admin("DROP USER " + db.name() + " CASCADE");
                break;
            default:
                throw new IllegalStateException(kind.toString());
        }
    }

    /**
     * Ends every session connected to a database, as a server restart or a failover does to the
     * copy tool's connections. Returns how many were ended.
     */
    public int killSessions(TestDatabase db) throws SQLException {
        return killSessions(db, null);
    }

    /**
     * Ends every session connected to a database except one ({@link #sessionId}; null for none).
     * Returns how many were ended.
     */
    public int killSessions(TestDatabase db, String except) throws SQLException {
        String keep = except == null ? "-1" : except;
        List<String> kills = new ArrayList<>();
        try (Connection c = adminConnection()) {
            switch (kind) {
                case POSTGRES:
                    return count(
                            c,
                            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity"
                                    + " WHERE datname = ? AND pid <> pg_backend_pid()"
                                    + " AND pid <> "
                                    + Long.parseLong(keep),
                            db.name());
                case MARIADB:
                case MYSQL:
                    for (String id :
                            strings(
                                    c,
                                    "SELECT ID FROM information_schema.PROCESSLIST"
                                            + " WHERE DB = ? AND ID <> CONNECTION_ID()",
                                    db.name())) {
                        if (!id.equals(keep)) {
                            kills.add("KILL " + id);
                        }
                    }
                    break;
                case SQLSERVER:
                    for (String id :
                            strings(
                                    c,
                                    "SELECT CAST(session_id AS varchar(10)) FROM"
                                            + " sys.dm_exec_sessions WHERE database_id ="
                                            + " DB_ID(?) AND session_id <> @@SPID",
                                    db.name())) {
                        if (!id.equals(keep)) {
                            kills.add("KILL " + id);
                        }
                    }
                    break;
                case ORACLE:
                    for (String id :
                            strings(
                                    c,
                                    "SELECT sid || ',' || serial# FROM v$session"
                                            + " WHERE username = ?",
                                    db.name().toUpperCase(Locale.ROOT))) {
                        if (!id.startsWith(keep + ",")) {
                            kills.add("ALTER SYSTEM KILL SESSION '" + id + "' IMMEDIATE");
                        }
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("kill sessions on " + kind);
            }
            int n = 0;
            for (String sql : kills) {
                try (Statement st = c.createStatement()) {
                    st.execute(sql);
                    n++;
                } catch (SQLException e) {
                    // The session ended meanwhile
                }
            }
            return n;
        }
    }

    /**
     * The server's id of a connection's session, as {@link #killSessions(TestDatabase, String)}
     * takes it.
     */
    public String sessionId(Connection c) throws SQLException {
        String sql;
        switch (kind) {
            case POSTGRES:
                sql = "SELECT pg_backend_pid()";
                break;
            case MARIADB:
            case MYSQL:
                sql = "SELECT CONNECTION_ID()";
                break;
            case SQLSERVER:
                sql = "SELECT @@SPID";
                break;
            case ORACLE:
                sql = "SELECT SYS_CONTEXT('USERENV', 'SID') FROM DUAL";
                break;
            default:
                throw new UnsupportedOperationException("session id on " + kind);
        }
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1).trim();
        }
    }

    /** How many sessions of a database are waiting for a lock that another session holds. */
    public long lockWaits(TestDatabase db) throws SQLException {
        String sql;
        String arg = db.name();
        switch (kind) {
            case POSTGRES:
                sql =
                        "SELECT COUNT(*) FROM pg_stat_activity"
                                + " WHERE datname = ? AND wait_event_type = 'Lock'";
                break;
            case MARIADB:
            case MYSQL:
                sql =
                        "SELECT COUNT(*) FROM information_schema.INNODB_TRX t"
                                + " JOIN information_schema.PROCESSLIST p"
                                + " ON p.ID = t.trx_mysql_thread_id"
                                + " WHERE t.trx_state = 'LOCK WAIT' AND p.DB = ?";
                break;
            case SQLSERVER:
                sql =
                        "SELECT COUNT(*) FROM sys.dm_exec_requests"
                                + " WHERE database_id = DB_ID(?) AND blocking_session_id <> 0";
                break;
            case ORACLE:
                sql =
                        "SELECT COUNT(*) FROM v$session"
                                + " WHERE username = ? AND blocking_session IS NOT NULL";
                arg = db.name().toUpperCase(Locale.ROOT);
                break;
            default:
                throw new UnsupportedOperationException("lock waits on " + kind);
        }
        try (Connection c = adminConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * On SQL Server, lets readers see the last committed rows instead of waiting for another
     * session's uncommitted ones (READ_COMMITTED_SNAPSHOT), as the other servers do; elsewhere
     * nothing.
     */
    public void readCommittedSnapshot(TestDatabase db) throws SQLException {
        if (kind == Kind.SQLSERVER) {
            admin(
                    "ALTER DATABASE "
                            + db.name()
                            + " SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE");
        }
    }

    private static int count(Connection c, String sql, String arg) throws SQLException {
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    n++;
                }
            }
        }
        return n;
    }

    private static List<String> strings(Connection c, String sql, String arg) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private void admin(String... statements) throws SQLException {
        try (Connection c = adminConnection();
                Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /** A connection with the rights to create databases and end sessions. */
    Connection adminConnection() throws SQLException {
        switch (kind) {
            case MARIADB:
            case MYSQL:
                return DriverManager.getConnection(
                        container.getJdbcUrl(), "root", container.getPassword());
            case ORACLE:
                return DriverManager.getConnection(
                        container.getJdbcUrl(), "system", container.getPassword());
            default:
                return DriverManager.getConnection(
                        container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }
    }

    @Override
    public String toString() {
        return kind.id();
    }
}
