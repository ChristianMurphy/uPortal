package org.apereo.portal.dbmigrate.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.apereo.portal.dbmigrate.copy.TableCopier;
import org.apereo.portal.dbmigrate.plan.ColumnMapping;
import org.apereo.portal.dbmigrate.plan.TableMapping;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.types.ValueCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Compares a copied table with its source: row count and an order-independent hash of the
 * normalized values, both sides read independently (and concurrently) from the databases with
 * streaming {@link JdbcTemplate} queries inside read-only transactions.
 */
public final class TableVerifier {

    /** One table's comparison. */
    public static final class Result {
        public long sourceRows;
        public long targetRows;
        public String sourceHash;
        public String targetHash;
        public long millis;

        public boolean matches() {
            return sourceRows == targetRows && sourceHash.equals(targetHash);
        }
    }

    private final Side source;
    private final Side target;
    private final Executor readers;

    public TableVerifier(
            DataSource sourceDs,
            Dialect sourceDialect,
            DataSource targetDs,
            Dialect targetDialect,
            int fetchSize,
            Executor readers) {
        this.source = new Side(sourceDs, sourceDialect, fetchSize);
        this.target = new Side(targetDs, targetDialect, fetchSize);
        this.readers = readers;
    }

    /** A database to read from: streaming JdbcTemplate plus read-only transactions. */
    private static final class Side {
        final Dialect dialect;
        final JdbcTemplate jdbc;
        final TransactionTemplate readOnly;

        Side(DataSource ds, Dialect dialect, int fetchSize) {
            this.dialect = dialect;
            this.jdbc = new JdbcTemplate(ds);
            this.jdbc.setFetchSize(dialect.streamingFetchSize(fetchSize));
            this.readOnly = new TransactionTemplate(new DataSourceTransactionManager(ds));
            this.readOnly.setReadOnly(true);
        }
    }

    public Result verify(TableMapping m) {
        long start = System.nanoTime();
        CompletableFuture<RowHasher> t =
                CompletableFuture.supplyAsync(() -> hashTarget(m), readers);
        RowHasher s;
        try {
            s = hashSource(m);
        } catch (RuntimeException e) {
            t.cancel(true);
            throw e;
        }
        RowHasher th;
        try {
            th = t.join();
        } catch (CompletionException e) {
            throw e.getCause() instanceof RuntimeException ? (RuntimeException) e.getCause() : e;
        }
        Result r = new Result();
        r.sourceRows = s.rows();
        r.sourceHash = s.value();
        r.targetRows = th.rows();
        r.targetHash = th.value();
        r.millis = (System.nanoTime() - start) / 1_000_000;
        return r;
    }

    /** Source side: the copy's own query, the transforms applied, normalized per column pair. */
    RowHasher hashSource(TableMapping m) {
        List<ColumnMapping> columns = m.columns();
        List<ColumnMapping> sourced = m.sourcedColumns();
        boolean rowMap = m.needsRowMap();
        RowHasher hasher = new RowHasher();
        Object[] values = new Object[sourced.size()];
        byte[][] row = new byte[columns.size()][];
        Map<String, Object> map = rowMap ? new HashMap<>() : null;
        source.readOnly.executeWithoutResult(
                status ->
                        source.jdbc.query(
                                TableCopier.selectSql(m, source.dialect),
                                rs -> {
                                    for (int i = 0; i < sourced.size(); i++) {
                                        ColumnMapping cm = sourced.get(i);
                                        values[i] =
                                                ValueCodec.read(
                                                        rs,
                                                        i + 1,
                                                        cm.pair().source(),
                                                        TableCopier.readFamily(cm));
                                        if (map != null) {
                                            map.put(cm.source().key(), values[i]);
                                        }
                                    }
                                    int s = 0;
                                    for (int i = 0; i < columns.size(); i++) {
                                        ColumnMapping cm = columns.get(i);
                                        Object v = cm.source() != null ? values[s++] : null;
                                        if (cm.transform() != null) {
                                            v =
                                                    cm.transform()
                                                            .canonical(
                                                                    cm.transform().apply(v, map));
                                        }
                                        row[i] = ValueCodec.normalize(v, cm.pair());
                                    }
                                    hasher.add(row);
                                }));
        return hasher;
    }

    /** Target side: the mapped target columns, normalized per column pair. */
    RowHasher hashTarget(TableMapping m) {
        List<ColumnMapping> columns = m.columns();
        List<String> exprs = new ArrayList<>();
        for (ColumnMapping cm : columns) {
            exprs.add(ValueCodec.selectExpression(cm.pair().target(), target.dialect));
        }
        String sql =
                "SELECT "
                        + String.join(", ", exprs)
                        + " FROM "
                        + target.dialect.qualify(m.target().name());
        RowHasher hasher = new RowHasher();
        byte[][] row = new byte[columns.size()][];
        target.readOnly.executeWithoutResult(
                status ->
                        target.jdbc.query(
                                sql,
                                rs -> {
                                    for (int i = 0; i < columns.size(); i++) {
                                        ColumnMapping cm = columns.get(i);
                                        Object v =
                                                ValueCodec.read(
                                                        rs,
                                                        i + 1,
                                                        cm.pair().target(),
                                                        cm.pair().logical());
                                        if (cm.transform() != null) {
                                            v = cm.transform().canonical(v);
                                        }
                                        row[i] = ValueCodec.normalize(v, cm.pair());
                                    }
                                    hasher.add(row);
                                }));
        return hasher;
    }
}
