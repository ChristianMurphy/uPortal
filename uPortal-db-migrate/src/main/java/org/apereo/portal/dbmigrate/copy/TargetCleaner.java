package org.apereo.portal.dbmigrate.copy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.dbmigrate.plan.ColumnMapping;
import org.apereo.portal.dbmigrate.plan.MigrationPlan;
import org.apereo.portal.dbmigrate.plan.TableMapping;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.ForeignKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code --clear-target}: deletes the rows of every table the run will load, children first, in one
 * transaction, so a schema created by {@code dataInit} can serve as the empty target. Generator
 * tables and tables the run does not load are left alone.
 */
public final class TargetCleaner {

    private TargetCleaner() {}

    /** Deletes the rows and returns how many were deleted. */
    public static long clear(MigrationPlan plan, JdbcTemplate target, TransactionTemplate tx) {
        Dialect d = plan.target().dialect();
        List<TableMapping> reversed = new ArrayList<>(plan.tables());
        Collections.reverse(reversed);
        Long deleted =
                tx.execute(
                        status -> {
                            // Self-references and cycles first, or MySQL refuses row by row
                            for (TableMapping m : reversed) {
                                for (ForeignKey fk : m.deferredForeignKeys()) {
                                    List<String> sets = new ArrayList<>();
                                    for (String col : fk.columns()) {
                                        sets.add(d.quote(col) + " = NULL");
                                    }
                                    target.update(
                                            "UPDATE "
                                                    + d.qualify(m.target().name())
                                                    + " SET "
                                                    + String.join(", ", sets));
                                }
                            }
                            long n = 0;
                            for (TableMapping m : reversed) {
                                String table = d.qualify(m.target().name());
                                for (ColumnMapping cm : m.columns()) {
                                    if (cm.pair().target().postgresLargeObject()) {
                                        target.execute(
                                                PostgresLargeObjects.unlinkSql(
                                                        table, cm.pair().target(), d));
                                    }
                                }
                                n += target.update("DELETE FROM " + table);
                            }
                            return n;
                        });
        return deleted == null ? 0 : deleted;
    }
}
