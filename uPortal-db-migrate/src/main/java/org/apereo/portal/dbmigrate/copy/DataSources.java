package org.apereo.portal.dbmigrate.copy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apereo.portal.dbmigrate.ConnectionSpec;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.SchemaReader;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Connection pools for the two databases, and readable database errors. */
public final class DataSources {

    private DataSources() {}

    /** Detects the vendor and scope behind a connection spec with one short-lived connection. */
    public static Dialect detect(ConnectionSpec spec) throws SQLException {
        try (Connection c = spec.open()) {
            return SchemaReader.dialect(new SingleConnectionDataSource(c, true), spec.schema());
        }
    }

    /**
     * A HikariCP pool for one side. Auto-commit stays on: everything that writes, or needs a
     * transaction to stream (PostgreSQL cursors), runs in an explicit Spring transaction or a
     * Spring Batch chunk. Each new connection runs the dialect's session statement.
     */
    public static HikariDataSource pool(
            ConnectionSpec spec, Dialect dialect, boolean target, int size) throws SQLException {
        HikariConfig c = new HikariConfig();
        c.setPoolName("db-migrate-" + spec.label());
        c.setDataSource(
                new SimpleDriverDataSource(
                        spec.driver(), spec.url(), spec.user(), spec.password()));
        c.setMaximumPoolSize(size);
        c.setMinimumIdle(0);
        c.setAutoCommit(true);
        String init = dialect.sessionInitSql(target);
        if (init != null) {
            c.setConnectionInitSql(init);
        }
        return new HikariDataSource(c);
    }

    public static void close(DataSource ds) {
        if (ds instanceof HikariDataSource) {
            ((HikariDataSource) ds).close();
        }
    }

    /**
     * The message of an error including the chained causes drivers hide batch errors in (Spring
     * wraps the SQLException; PostgreSQL puts the real reason in getNextException).
     */
    public static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder(String.valueOf(e.getMessage()));
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        if (t != e && !sb.toString().contains(String.valueOf(t.getMessage()))) {
            sb.append(" / ").append(t.getMessage());
        }
        if (t instanceof SQLException) {
            SQLException next = ((SQLException) t).getNextException();
            int n = 0;
            while (next != null && n++ < 3) {
                sb.append(" / ").append(next.getMessage());
                next = next.getNextException();
            }
            if (t instanceof BatchUpdateException && n == 0 && t.getCause() != null) {
                sb.append(" / ").append(t.getCause().getMessage());
            }
        }
        return sb.toString();
    }
}
