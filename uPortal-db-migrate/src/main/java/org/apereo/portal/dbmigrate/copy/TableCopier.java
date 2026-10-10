package org.apereo.portal.dbmigrate.copy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apereo.portal.dbmigrate.plan.ColumnMapping;
import org.apereo.portal.dbmigrate.plan.TableMapping;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.ForeignKey;
import org.apereo.portal.dbmigrate.schema.Names;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;
import org.apereo.portal.dbmigrate.types.ValueCodec;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds the Spring Batch steps that copy tables: a chunk-oriented step per table, reading with a
 * {@link JdbcCursorItemReader} (forward-only, with a fetch size, so memory stays bounded by one
 * chunk whatever the table size) and writing with a {@link JdbcBatchItemWriter} (one batched INSERT
 * and one target transaction per chunk). Column values go through {@link ValueCodec}.
 */
public final class TableCopier {

    private final DataSource source;
    private final Dialect sourceDialect;
    private final DataSource target;
    private final Dialect targetDialect;
    private final PlatformTransactionManager targetTransactions;
    private final JobRepository jobRepository;
    private final int batchSize;
    private final int fetchSize;

    public TableCopier(
            DataSource source,
            Dialect sourceDialect,
            DataSource target,
            Dialect targetDialect,
            PlatformTransactionManager targetTransactions,
            JobRepository jobRepository,
            int batchSize,
            int fetchSize) {
        this.source = source;
        this.sourceDialect = sourceDialect;
        this.target = target;
        this.targetDialect = targetDialect;
        this.targetTransactions = targetTransactions;
        this.jobRepository = jobRepository;
        this.batchSize = batchSize;
        this.fetchSize = fetchSize;
    }

    /** The source query of a table mapping: the sourced columns, in mapping order. */
    public static String selectSql(TableMapping m, Dialect d) {
        List<String> exprs = new ArrayList<>();
        for (ColumnMapping c : m.sourcedColumns()) {
            exprs.add(ValueCodec.selectExpression(c.pair().source(), d));
        }
        if (exprs.isEmpty()) {
            exprs.add("1");
        }
        return "SELECT "
                + String.join(", ", exprs)
                + " FROM "
                + d.qualify(m.source().name())
                + (m.sourceFilter() == null ? "" : " WHERE " + m.sourceFilter());
    }

    public static String insertSql(TableMapping m, Dialect d) {
        List<String> cols = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (ColumnMapping c : m.columns()) {
            cols.add(d.quote(c.target().name()));
            values.add(ValueCodec.placeholder(c.pair().target()));
        }
        return "INSERT INTO "
                + d.qualify(m.target().name())
                + " ("
                + String.join(", ", cols)
                + ") VALUES ("
                + String.join(", ", values)
                + ")";
    }

    /** Step name of a table's copy; the report finds the step execution by it. */
    public static String copyStepName(TableMapping m) {
        return "copy:" + m.key();
    }

    public static String deferredStepName(TableMapping m) {
        return "deferred:" + m.key();
    }

    /**
     * The family a source column is read as: the pair's logical type, or the source column's own
     * family when a transform changes the type.
     */
    public static Family readFamily(ColumnMapping c) {
        if (c.transform() != null && c.transform().resultFamily() != null) {
            return c.pair().source().family();
        }
        return c.pair().logical();
    }

    /** A source row: the sourced columns' values, read the vendor-neutral way. */
    static Object[] readRow(ResultSet rs, List<ColumnMapping> sourced) throws SQLException {
        Object[] values = new Object[sourced.size()];
        for (int i = 0; i < sourced.size(); i++) {
            ColumnMapping c = sourced.get(i);
            values[i] = ValueCodec.read(rs, i + 1, c.pair().source(), readFamily(c));
        }
        return values;
    }

    private JdbcCursorItemReader<Object[]> reader(String name, String sql, List<ColumnMapping> cols)
            throws Exception {
        JdbcCursorItemReader<Object[]> reader =
                new JdbcCursorItemReader<>(source, sql, (rs, n) -> readRow(rs, cols));
        reader.setName(name);
        reader.setFetchSize(sourceDialect.streamingFetchSize(fetchSize));
        // PostgreSQL only streams inside a transaction; MySQL streaming result sets have no row
        // position to verify. Nothing is restarted, so no read position is saved either.
        reader.setConnectionAutoCommit(false);
        reader.setVerifyCursorPosition(false);
        reader.setSaveState(false);
        return reader;
    }

    /** Turns a source row into the target row: transforms, precision, deferred keys. */
    static ItemProcessor<Object[], Object[]> processor(TableMapping m) {
        List<ColumnMapping> columns = m.columns();
        boolean rowMap = m.needsRowMap();
        boolean[] deferred = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            deferred[i] = m.isDeferred(columns.get(i).target().name());
        }
        List<ColumnMapping> sourced = m.sourcedColumns();
        return values -> {
            Map<String, Object> row = null;
            if (rowMap) {
                row = new HashMap<>();
                for (int i = 0; i < sourced.size(); i++) {
                    row.put(sourced.get(i).source().key(), values[i]);
                }
            }
            Object[] out = new Object[columns.size()];
            int s = 0;
            for (int i = 0; i < columns.size(); i++) {
                ColumnMapping c = columns.get(i);
                Object v = c.source() != null ? values[s++] : null;
                if (c.transform() != null) {
                    v = c.transform().apply(v, row);
                }
                out[i] = deferred[i] ? null : ValueCodec.adapt(v, c.pair());
            }
            return out;
        };
    }

    private ItemWriter<Object[]> writer(TableMapping m) throws Exception {
        List<ColumnMapping> columns = m.columns();
        JdbcBatchItemWriter<Object[]> insert = new JdbcBatchItemWriter<>();
        insert.setDataSource(target);
        insert.setSql(insertSql(m, targetDialect));
        insert.setItemPreparedStatementSetter(
                (values, ps) -> {
                    for (int i = 0; i < columns.size(); i++) {
                        ColumnMapping c = columns.get(i);
                        ValueCodec.write(
                                ps, i + 1, c.pair().target(), c.pair().logical(), values[i]);
                    }
                });
        insert.afterPropertiesSet();
        String on = null;
        for (ColumnMapping c : columns) {
            if (c.target().autoIncrement()) {
                on = targetDialect.identityInsertSql(m.target(), true);
            }
        }
        if (on == null) {
            return insert;
        }
        // SQL Server: explicit identity values need IDENTITY_INSERT on the chunk's connection,
        // which the chunk transaction binds, so JdbcTemplate runs on that same connection
        String identityOn = on;
        String identityOff = targetDialect.identityInsertSql(m.target(), false);
        JdbcTemplate jdbc = new JdbcTemplate(target);
        return chunk -> {
            jdbc.execute(identityOn);
            insert.write(chunk);
            jdbc.execute(identityOff);
        };
    }

    /** The chunk-oriented step that copies one table. */
    public Step copyStep(TableMapping m) throws Exception {
        return new StepBuilder(copyStepName(m), jobRepository)
                .<Object[], Object[]>chunk(batchSize)
                .reader(reader("read:" + m.key(), selectSql(m, sourceDialect), m.sourcedColumns()))
                .processor(processor(m))
                .writer(writer(m))
                .transactionManager(targetTransactions)
                .build();
    }

    /**
     * The second pass for foreign keys inserted as NULL (self-references, cycles): reads the
     * foreign key and key columns from the source and updates the target rows.
     */
    public Step deferredStep(TableMapping m) throws Exception {
        List<ColumnMapping> keyCols = new ArrayList<>();
        for (String pk : m.target().primaryKey()) {
            keyCols.add(byTarget(m, pk));
        }
        List<ColumnMapping> fkCols = new ArrayList<>();
        for (ForeignKey fk : m.deferredForeignKeys()) {
            for (String c : fk.columns()) {
                ColumnMapping cm = byTarget(m, c);
                if (!fkCols.contains(cm)) {
                    fkCols.add(cm);
                }
            }
        }
        // Read in UPDATE parameter order: SET columns first, then the key
        List<ColumnMapping> read = new ArrayList<>(fkCols);
        read.addAll(keyCols);
        List<String> select = new ArrayList<>();
        for (ColumnMapping c : read) {
            select.add(ValueCodec.selectExpression(c.pair().source(), sourceDialect));
        }
        List<String> notNull = new ArrayList<>();
        for (ColumnMapping c : fkCols) {
            notNull.add(sourceDialect.quote(c.source().name()) + " IS NOT NULL");
        }
        String where = "(" + String.join(" OR ", notNull) + ")";
        if (m.sourceFilter() != null) {
            where += " AND (" + m.sourceFilter() + ")";
        }
        String selectSql =
                "SELECT "
                        + String.join(", ", select)
                        + " FROM "
                        + sourceDialect.qualify(m.source().name())
                        + " WHERE "
                        + where;
        List<String> sets = new ArrayList<>();
        for (ColumnMapping c : fkCols) {
            sets.add(targetDialect.quote(c.target().name()) + " = ?");
        }
        List<String> keys = new ArrayList<>();
        for (ColumnMapping c : keyCols) {
            keys.add(targetDialect.quote(c.target().name()) + " = ?");
        }
        JdbcBatchItemWriter<Object[]> update = new JdbcBatchItemWriter<>();
        update.setDataSource(target);
        update.setSql(
                "UPDATE "
                        + targetDialect.qualify(m.target().name())
                        + " SET "
                        + String.join(", ", sets)
                        + " WHERE "
                        + String.join(" AND ", keys));
        update.setItemPreparedStatementSetter(
                (values, ps) -> {
                    for (int i = 0; i < read.size(); i++) {
                        ColumnMapping c = read.get(i);
                        ValueCodec.write(
                                ps,
                                i + 1,
                                c.pair().target(),
                                c.pair().logical(),
                                ValueCodec.adapt(values[i], c.pair()));
                    }
                });
        update.afterPropertiesSet();
        return new StepBuilder(deferredStepName(m), jobRepository)
                .<Object[], Object[]>chunk(batchSize)
                .reader(reader("deferred:" + m.key(), selectSql, read))
                .writer(update)
                .transactionManager(targetTransactions)
                .build();
    }

    private static ColumnMapping byTarget(TableMapping m, String targetColumn) {
        for (ColumnMapping c : m.columns()) {
            if (c.target().key().equals(Names.key(targetColumn))) {
                if (c.source() == null || c.transform() != null) {
                    throw new IllegalStateException(
                            "Deferred key column "
                                    + targetColumn
                                    + " of "
                                    + m
                                    + " must be copied as-is");
                }
                return c;
            }
        }
        throw new IllegalStateException("No mapping for " + m.target().name() + "." + targetColumn);
    }
}
