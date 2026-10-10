package org.apereo.portal.dbmigrate.copy;

import java.util.ArrayList;
import java.util.List;
import org.apereo.portal.dbmigrate.plan.GeneratorTarget;
import org.apereo.portal.dbmigrate.plan.MigrationPlan;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.Dialect.GeneratorState;
import org.apereo.portal.dbmigrate.schema.Table;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves every target ID generator above the ids the copy wrote.
 *
 * <p>Hibernate's pooled optimizers take one value from the database per {@code allocationSize} ids.
 * With POOLED (entity sequences in 5.17) a database value {@code v} hands out {@code v -
 * allocationSize + 1 .. v}; with POOLED_LO (the {@code UP_SEQUENCE} counters since 5.17.10,
 * MOD-I72) it hands out {@code v .. v + allocationSize - 1}; without an optimizer (allocationSize
 * 1) just {@code v}. A next value of at least {@code max(id) + allocationSize} is above {@code
 * max(id)} under all three, so that is the floor. The generator never moves below the source
 * generator's own position (it may already have handed out ids whose rows were deleted) nor below
 * where the target already is.
 */
public final class GeneratorPositioner {

    /** The outcome for one generator. */
    public static final class Result {
        public String name;
        public String targetStorage;
        public String sourceStorage;
        public String origin;
        public int allocationSize;
        public List<String> served = new ArrayList<>();
        public Long maxId;
        public Long sourceNext;
        public Long targetBefore;
        public Long targetAfter;
        public long required;
        public boolean changed;
        public boolean ok;
        public String message;
    }

    private GeneratorPositioner() {}

    /** The lowest next value that cannot collide with {@code maxId} (null: no rows). */
    public static long requiredNext(Long maxId, int allocationSize) {
        if (maxId == null) {
            return 1;
        }
        return maxId + Math.max(1, allocationSize);
    }

    /** The next value to set: the floor, but never below the source's or the target's own. */
    public static long position(Long maxId, int allocationSize, Long sourceNext, Long targetNext) {
        long next = requiredNext(maxId, allocationSize);
        if (sourceNext != null) {
            next = Math.max(next, sourceNext);
        }
        if (targetNext != null) {
            next = Math.max(next, targetNext);
        }
        return next;
    }

    /**
     * Positions (or only checks) every generator of the plan, each in its own target transaction.
     *
     * @param apply false to only check the positions (verify-only)
     */
    public static List<Result> run(
            MigrationPlan plan,
            JdbcOperations source,
            JdbcOperations target,
            TransactionTemplate targetTx,
            boolean apply) {
        List<Result> results = new ArrayList<>();
        for (GeneratorTarget g : plan.generators()) {
            Result r = new Result();
            r.name = g.name();
            r.targetStorage = g.targetStorage().name();
            r.sourceStorage =
                    g.sourceName() == null ? "NONE" : g.sourceStorage() + " " + g.sourceName();
            r.origin = g.origin();
            try {
                targetTx.executeWithoutResult(
                        status ->
                                position(
                                        g,
                                        r,
                                        plan.source().dialect(),
                                        source,
                                        plan.target().dialect(),
                                        target,
                                        apply));
            } catch (DataAccessException e) {
                r.ok = false;
                r.message = DataSources.describe(e);
            }
            results.add(r);
        }
        return results;
    }

    private static void position(
            GeneratorTarget g,
            Result r,
            Dialect sd,
            JdbcOperations source,
            Dialect td,
            JdbcOperations target,
            boolean apply) {
        if (g.sourceName() != null) {
            GeneratorState s =
                    g.sourceStorage() == GeneratorTarget.Storage.SEQUENCE
                            ? sd.readSequence(source, g.sourceName())
                            : sd.readSequenceTable(
                                    source,
                                    g.sourceTable(),
                                    g.sourceTable().columns().get(0).name());
            r.sourceNext = s.nextValue();
        }
        GeneratorState t = readTarget(g, td, target);
        r.targetBefore = t.nextValue();
        int allocation = g.allocationSize();
        if (allocation <= 0) {
            // Not in the catalog: Hibernate creates sequences with increment = allocationSize
            allocation = t.increment() == null ? 1 : (int) Math.max(1, t.increment());
        }
        r.allocationSize = allocation;
        for (GeneratorTarget.Served s : g.served()) {
            r.served.add(s.toString());
            Long max = maxId(td, target, s.table(), s.column());
            if (max != null && (r.maxId == null || max > r.maxId)) {
                r.maxId = max;
            }
        }
        r.required = requiredNext(r.maxId, allocation);
        long next = position(r.maxId, allocation, r.sourceNext, r.targetBefore);
        if (apply && (r.targetBefore == null || next != r.targetBefore)) {
            if (g.targetStorage() == GeneratorTarget.Storage.SEQUENCE) {
                for (String sql : td.restartSequenceSql(g.name(), next)) {
                    target.execute(sql);
                }
            } else {
                String column = g.targetTable().columns().get(0).name();
                target.update(
                        t.rows() == 0
                                ? td.insertSequenceTableSql(g.targetTable(), column)
                                : td.updateSequenceTableSql(g.targetTable(), column),
                        next);
            }
            r.changed = true;
        }
        r.targetAfter = readTarget(g, td, target).nextValue();
        long floor = r.sourceNext == null ? r.required : Math.max(r.required, r.sourceNext);
        if (r.maxId == null && r.sourceNext == null) {
            r.ok = true;
            r.message = "no copied ids";
        } else if (r.targetAfter != null && r.targetAfter >= floor) {
            r.ok = true;
        } else {
            r.ok = false;
            r.message =
                    "next value "
                            + r.targetAfter
                            + " is below "
                            + floor
                            + " (max id "
                            + r.maxId
                            + " + allocationSize "
                            + allocation
                            + (r.sourceNext == null ? "" : ", source next " + r.sourceNext)
                            + ")";
        }
    }

    private static GeneratorState readTarget(GeneratorTarget g, Dialect td, JdbcOperations target) {
        if (g.targetStorage() == GeneratorTarget.Storage.SEQUENCE) {
            return td.readSequence(target, g.name());
        }
        return td.readSequenceTable(
                target, g.targetTable(), g.targetTable().columns().get(0).name());
    }

    static Long maxId(Dialect d, JdbcOperations jdbc, Table table, String column) {
        return jdbc.queryForObject(
                "SELECT MAX(" + d.quote(column) + ") FROM " + d.qualify(table.name()), Long.class);
    }

    /** Identity / auto-increment columns: next value {@code max + 1} where the vendor needs it. */
    public static List<String> resetIdentities(
            MigrationPlan plan, JdbcOperations target, TransactionTemplate targetTx) {
        List<String> done = new ArrayList<>();
        Dialect td = plan.target().dialect();
        for (MigrationPlan.IdentityColumn ic : plan.identityColumns()) {
            targetTx.executeWithoutResult(
                    status -> {
                        Long max = maxId(td, target, ic.table(), ic.column().name());
                        long next = max == null ? 1 : max + 1;
                        String sql = td.resetIdentitySql(ic.table(), ic.column(), next);
                        if (sql != null) {
                            target.execute(sql);
                            done.add(ic + " next=" + next);
                        } else {
                            done.add(ic + " (moved by the explicit inserts)");
                        }
                    });
        }
        return done;
    }
}
