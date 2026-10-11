package org.apereo.portal.dbmigrate;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apereo.portal.dbmigrate.copy.CopyJob;
import org.apereo.portal.dbmigrate.copy.DataSources;
import org.apereo.portal.dbmigrate.copy.GeneratorPositioner;
import org.apereo.portal.dbmigrate.copy.PostgresLargeObjects;
import org.apereo.portal.dbmigrate.copy.TableCopier;
import org.apereo.portal.dbmigrate.copy.TargetCleaner;
import org.apereo.portal.dbmigrate.plan.ColumnMapping;
import org.apereo.portal.dbmigrate.plan.MigrationPlan;
import org.apereo.portal.dbmigrate.plan.Planner;
import org.apereo.portal.dbmigrate.plan.SchemaProfile;
import org.apereo.portal.dbmigrate.plan.TableMapping;
import org.apereo.portal.dbmigrate.plan.TargetCodePageChecks;
import org.apereo.portal.dbmigrate.plan.TargetConstraintChecks;
import org.apereo.portal.dbmigrate.report.MigrationReport;
import org.apereo.portal.dbmigrate.rules.RuleSet;
import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.schema.SchemaModel;
import org.apereo.portal.dbmigrate.schema.SchemaReader;
import org.apereo.portal.dbmigrate.schema.Table;
import org.apereo.portal.dbmigrate.types.ColumnType;
import org.apereo.portal.dbmigrate.verify.TableVerifier;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The copy migration: plan, (optionally) clear the target, copy every table in foreign-key order as
 * a Spring Batch job, position the ID generators, verify. The source is only ever read. All
 * database access goes through Spring JDBC on two HikariCP pools.
 */
public final class DbMigrate {

    /** Exit status: everything copied and verified. */
    public static final int OK = 0;

    /** Exit status: a copy, verification or generator check failed. */
    public static final int FAILED = 1;

    /** Exit status: the plan has errors (nothing was written) or the arguments are wrong. */
    public static final int PLAN_ERROR = 2;

    private final MigrationOptions options;
    private final PrintStream out;
    private final MigrationReport report = new MigrationReport();
    private final AtomicLong maxUsedHeap = new AtomicLong();

    public DbMigrate(MigrationOptions options, PrintStream out) {
        this.options = options;
        this.out = out;
    }

    public MigrationReport report() {
        return report;
    }

    public int run() {
        report.mode = options.mode().name();
        ScheduledExecutorService memory =
                Executors.newSingleThreadScheduledExecutor(daemon("db-migrate-memory"));
        MemoryMXBean heap = ManagementFactory.getMemoryMXBean();
        var unused =
                memory.scheduleAtFixedRate(
                        () ->
                                maxUsedHeap.accumulateAndGet(
                                        heap.getHeapMemoryUsage().getUsed(), Math::max),
                        0,
                        200,
                        TimeUnit.MILLISECONDS);
        int status;
        try {
            status = execute();
        } catch (Exception e) {
            report.errors.add(DataSources.describe(e));
            status = FAILED;
        } finally {
            memory.shutdownNow();
            report.memory.put("maxUsedHeapMiB", maxUsedHeap.get() / (1024 * 1024));
            report.memory.put("maxHeapMiB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
            report.finished = Instant.now();
        }
        if (status == OK && !report.success()) {
            status = FAILED;
        }
        out.println();
        out.print(report.summary());
        if (options.report() != null) {
            try {
                report.write(options.report());
                out.println("Report written to " + options.report().toAbsolutePath());
            } catch (IOException e) {
                out.println("Could not write the report: " + e);
                status = FAILED;
            }
        }
        return status;
    }

    private int execute() throws Exception {
        options(report.options);
        SchemaProfile sourceProfile = SchemaProfile.load(options.sourceProfile());
        SchemaProfile targetProfile = SchemaProfile.load(options.targetProfile());
        RuleSet rules = RuleSet.empty();
        for (Path p : options.ruleFiles()) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                rules.add(r, p.toString());
            }
        }
        // Rules for the target's own generators (e.g. portlet tables) also position them
        targetProfile.generators().addRules(rules);
        options.excludes().forEach(rules::exclude);
        if (options.skipRawEvents()) {
            rules.exclude(MigrationOptions.RAW_EVENTS_TABLE);
        }

        // Per side: a copy reader or writer per parallel table, a verification reader, metadata
        int poolSize = options.threads() * 2 + 2;
        HikariDataSource sourceDs = null;
        HikariDataSource targetDs = null;
        try {
            sourceDs =
                    DataSources.pool(
                            options.source(),
                            DataSources.detect(options.source()),
                            false,
                            poolSize);
            targetDs =
                    DataSources.pool(
                            options.target(), DataSources.detect(options.target()), true, poolSize);
            return execute(sourceProfile, targetProfile, rules, sourceDs, targetDs);
        } finally {
            DataSources.close(sourceDs);
            DataSources.close(targetDs);
        }
    }

    private int execute(
            SchemaProfile sourceProfile,
            SchemaProfile targetProfile,
            RuleSet rules,
            HikariDataSource sourceDs,
            HikariDataSource targetDs)
            throws Exception {
        log("Reading the source schema: " + options.source());
        SchemaModel source = SchemaReader.read(sourceDs, options.source().schema());
        log("Reading the target schema: " + options.target());
        SchemaModel target = SchemaReader.read(targetDs, options.target().schema());
        describe(report.source, options.source(), source);
        describe(report.target, options.target(), target);

        MigrationPlan plan =
                new Planner(sourceProfile, targetProfile, rules, options.includes())
                        .plan(source, target);
        JdbcTemplate sourceJdbc = new JdbcTemplate(sourceDs);
        JdbcTemplate targetJdbc = new JdbcTemplate(targetDs);
        PlatformTransactionManager targetTransactions = CopyJob.transactions(targetDs);
        TransactionTemplate targetTx = new TransactionTemplate(targetTransactions);
        checkWithDatabases(plan, sourceJdbc, targetJdbc);
        planReport(plan);
        if (plan.hasErrors()) {
            log("The plan has errors; nothing was written.");
            return PLAN_ERROR;
        }
        if (options.mode() == MigrationOptions.Mode.DRY_RUN) {
            log("Dry run: nothing was written.");
            return OK;
        }

        if (options.mode() == MigrationOptions.Mode.COPY) {
            if (options.clearTarget()) {
                log("Clearing " + plan.tables().size() + " target tables");
                report.clearedRows = TargetCleaner.clear(plan, targetJdbc, targetTx);
            }
            long start = System.nanoTime();
            try (CopyJob job = new CopyJob()) {
                TableCopier copier =
                        new TableCopier(
                                sourceDs,
                                source.dialect(),
                                targetDs,
                                target.dialect(),
                                targetTransactions,
                                job.repository(),
                                options.batchSize(),
                                options.fetchSize());
                JobExecution execution = job.run(plan, copier, options.threads());
                copyReport(plan, execution);
            }
            report.copyMillis = (System.nanoTime() - start) / 1_000_000;
            if (!report.success()) {
                log("The copy failed; generators are not positioned and nothing is verified.");
                return FAILED;
            }
            log("Positioning ID generators");
            report.generators.addAll(
                    GeneratorPositioner.run(plan, sourceJdbc, targetJdbc, targetTx, true));
            report.identities.addAll(
                    GeneratorPositioner.resetIdentities(plan, targetJdbc, targetTx));
        } else {
            report.generators.addAll(
                    GeneratorPositioner.run(plan, sourceJdbc, targetJdbc, targetTx, false));
        }
        if (options.verify() || options.mode() == MigrationOptions.Mode.VERIFY_ONLY) {
            long start = System.nanoTime();
            verify(plan, sourceDs, targetDs);
            report.verifyMillis = (System.nanoTime() - start) / 1_000_000;
        }
        return report.success() ? OK : FAILED;
    }

    // ------------------------------------------------------------------ checks

    /**
     * Checks that need the databases: row counts, an empty target, and on a PostgreSQL source that
     * every large object reference resolves and no undeclared column holds OIDs.
     */
    private void checkWithDatabases(MigrationPlan plan, JdbcTemplate s, JdbcTemplate t) {
        Dialect sd = plan.source().dialect();
        Dialect td = plan.target().dialect();
        if (plan.source().vendor() == Vendor.POSTGRESQL) {
            checkPostgresLobs(plan, s);
        }
        for (TableMapping m : plan.tables()) {
            m.sourceRows(count(s, sd.qualify(m.source().name()), m.sourceFilter()));
            m.targetRows(count(t, td.qualify(m.target().name()), null));
            if (m.targetRows() > 0
                    && options.mode() == MigrationOptions.Mode.COPY
                    && !options.clearTarget()) {
                plan.error(
                        "Target table "
                                + m.target().name()
                                + " already has "
                                + m.targetRows()
                                + " rows; the target must be empty (or pass --clear-target)");
            }
        }
        if (options.mode() != MigrationOptions.Mode.VERIFY_ONLY) {
            // A target constraint the source rows violate fails here, not half-way through
            TargetConstraintChecks.check(plan, s);
            // So does text the target's character set can't hold (MOD-I202)
            TargetCodePageChecks.check(plan, s, t);
        }
    }

    private void checkPostgresLobs(MigrationPlan plan, JdbcTemplate s) {
        Dialect sd = plan.source().dialect();
        for (TableMapping m : plan.tables()) {
            String table = sd.qualify(m.source().name());
            List<String> filters = new ArrayList<>();
            for (ColumnMapping cm : m.sourcedColumns()) {
                ColumnType st = cm.pair().source();
                if (st.postgresLargeObject()) {
                    long dangling =
                            PostgresLargeObjects.count(
                                    s, PostgresLargeObjects.countDanglingSql(table, st, sd));
                    if (dangling > 0) {
                        String what =
                                dangling
                                        + " rows of "
                                        + m.source().name()
                                        + "."
                                        + cm.source().name()
                                        + " reference large objects that no longer exist"
                                        + " (vacuumlo removes them, MOD-I24)";
                        if (options.missingLobPolicy()
                                == MigrationOptions.MissingLobPolicy.SKIP_ROW) {
                            plan.warn(what + "; those rows are skipped (--missing-lob skip-row)");
                            filters.add(
                                    "(" + PostgresLargeObjects.resolvableCondition(st, sd) + ")");
                        } else {
                            plan.error(
                                    what + "; pass --missing-lob skip-row to copy the other rows");
                        }
                    }
                } else if (st.family() == ColumnType.Family.STRING
                        && "text".equals(cm.source().typeName())) {
                    long oids =
                            PostgresLargeObjects.count(
                                    s,
                                    PostgresLargeObjects.countUndeclaredSql(
                                            table, cm.source().name(), sd));
                    if (oids > 0) {
                        plan.error(
                                m.source().name()
                                        + "."
                                        + cm.source().name()
                                        + " holds "
                                        + oids
                                        + " large-object OIDs but is not declared; add the rule"
                                        + " 'lob-oid "
                                        + m.source().name()
                                        + "."
                                        + cm.source().name()
                                        + "' or the data would be copied as numbers");
                    }
                }
            }
            if (!filters.isEmpty()) {
                m.sourceFilter(String.join(" AND ", filters));
            }
        }
        report.plan.put(
                "sourceLargeObjects",
                PostgresLargeObjects.count(s, "SELECT count(*) FROM pg_largeobject_metadata"));
    }

    private static long count(JdbcTemplate jdbc, String qualifiedTable, String filter) {
        Long n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + qualifiedTable
                                + (filter == null ? "" : " WHERE " + filter),
                        Long.class);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ copy

    /** Fills the report from the job's step executions. */
    private void copyReport(MigrationPlan plan, JobExecution execution) {
        Map<String, StepExecution> steps = new HashMap<>();
        if (execution != null) {
            for (StepExecution s : execution.getStepExecutions()) {
                steps.put(s.getStepName(), s);
            }
            report.plan.put("batchJobStatus", execution.getStatus().name());
        }
        int i = 0;
        for (TableMapping m : plan.tables()) {
            MigrationReport.TableEntry entry = report.tables.get(i++);
            StepExecution copy = steps.get(TableCopier.copyStepName(m));
            if (copy == null) {
                entry.error = "not copied: a table it references failed";
                entry.verify = "SKIPPED";
                continue;
            }
            entry.copiedRows = copy.getWriteCount();
            entry.copyMillis = millis(copy);
            entry.batchCommits = copy.getCommitCount();
            if (copy.getStatus() != BatchStatus.COMPLETED) {
                entry.error = failure(copy);
                entry.verify = "SKIPPED";
                continue;
            }
            log(
                    String.format(
                            Locale.ROOT,
                            "Copied %-34s %,12d rows in %,.1f s (%d commits)",
                            m.source().name(),
                            entry.copiedRows,
                            entry.copyMillis / 1000.0,
                            copy.getCommitCount()));
            StepExecution deferred = steps.get(TableCopier.deferredStepName(m));
            if (deferred != null) {
                entry.deferredRows = deferred.getWriteCount();
                if (deferred.getStatus() != BatchStatus.COMPLETED) {
                    entry.error = failure(deferred);
                    entry.verify = "SKIPPED";
                } else {
                    log(
                            "Loaded the deferred foreign keys of "
                                    + m
                                    + ": "
                                    + entry.deferredRows
                                    + " rows");
                }
            } else if (!m.deferredForeignKeys().isEmpty()) {
                entry.error = "deferred foreign keys not loaded: an earlier step failed";
                entry.verify = "SKIPPED";
            }
        }
    }

    private static long millis(StepExecution s) {
        if (s.getStartTime() == null || s.getEndTime() == null) {
            return 0;
        }
        return Duration.between(s.getStartTime(), s.getEndTime()).toMillis();
    }

    private static String failure(StepExecution s) {
        if (s.getFailureExceptions().isEmpty()) {
            return s.getStatus() + " " + s.getExitStatus().getExitDescription();
        }
        return DataSources.describe(s.getFailureExceptions().get(0));
    }

    // ------------------------------------------------------------------ verify

    private void verify(MigrationPlan plan, HikariDataSource sourceDs, HikariDataSource targetDs) {
        ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemon("db-migrate-verify"));
        ExecutorService readers = Executors.newCachedThreadPool(daemon("db-migrate-verify-read"));
        try {
            TableVerifier verifier =
                    new TableVerifier(
                            sourceDs,
                            plan.source().dialect(),
                            targetDs,
                            plan.target().dialect(),
                            options.fetchSize(),
                            readers);
            List<CompletableFuture<Void>> all = new ArrayList<>();
            int i = 0;
            for (TableMapping m : plan.tables()) {
                MigrationReport.TableEntry entry = report.tables.get(i++);
                if (entry.error != null) {
                    continue;
                }
                all.add(
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        TableVerifier.Result r = verifier.verify(m);
                                        entry.verifiedSourceRows = r.sourceRows;
                                        entry.verifiedTargetRows = r.targetRows;
                                        entry.sourceHash = r.sourceHash;
                                        entry.targetHash = r.targetHash;
                                        entry.verifyMillis = r.millis;
                                        entry.verify = r.matches() ? "OK" : "MISMATCH";
                                        if (!r.matches()) {
                                            log(
                                                    "MISMATCH "
                                                            + m
                                                            + ": rows "
                                                            + r.sourceRows
                                                            + "/"
                                                            + r.targetRows
                                                            + " hash "
                                                            + r.sourceHash
                                                            + "/"
                                                            + r.targetHash);
                                        }
                                    } catch (RuntimeException e) {
                                        entry.verify = "ERROR";
                                        entry.error = DataSources.describe(e);
                                    }
                                },
                                pool));
            }
            CompletableFuture.allOf(all.toArray(new CompletableFuture<?>[0])).join();
            int ok = 0;
            for (MigrationReport.TableEntry e : report.tables) {
                ok += "OK".equals(e.verify) ? 1 : 0;
            }
            log("Verified " + ok + " of " + report.tables.size() + " tables");
        } finally {
            pool.shutdownNow();
            readers.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ report

    private void planReport(MigrationPlan plan) {
        report.errors.addAll(plan.errors());
        report.warnings.addAll(plan.warnings());
        report.plan.put("tables", plan.tables().size());
        report.plan.put("excludedTables", plan.excludedTables());
        report.plan.put("sourceGeneratorTables", plan.generatorTables().size());
        report.plan.put("targetOnlyTables", plan.targetOnlyTables());
        List<Object> levels = new ArrayList<>();
        if (plan.order() != null) {
            levels.addAll(plan.order().levels());
        }
        report.plan.put("loadOrder", levels);
        List<Object> gens = new ArrayList<>();
        plan.generators().forEach(g -> gens.add(g.toString()));
        report.plan.put("generators", gens);
        long rows = 0;
        for (TableMapping m : plan.tables()) {
            MigrationReport.TableEntry e = report.table(m.source().name(), m.target().name());
            e.sourceRows = m.sourceRows();
            e.targetRowsBefore = m.targetRows();
            e.notes.addAll(m.notes());
            rows += Math.max(0, m.sourceRows());
        }
        report.plan.put("sourceRows", rows);
        log(
                "Plan: "
                        + plan.tables().size()
                        + " tables ("
                        + rows
                        + " rows) in "
                        + (plan.order() == null ? 0 : plan.order().levels().size())
                        + " foreign-key levels, "
                        + plan.generators().size()
                        + " generators, "
                        + plan.excludedTables().size()
                        + " excluded, "
                        + plan.errors().size()
                        + " errors, "
                        + plan.warnings().size()
                        + " warnings");
    }

    private void options(Map<String, Object> m) {
        m.put("batchSize", options.batchSize());
        m.put("fetchSize", options.fetchSize());
        m.put("threads", options.threads());
        m.put("clearTarget", options.clearTarget());
        m.put("verify", options.verify());
        m.put("skipRawEvents", options.skipRawEvents());
        m.put("includes", options.includes());
        m.put("excludes", options.excludes());
        m.put("sourceProfile", options.sourceProfile());
        m.put("targetProfile", options.targetProfile());
        List<String> files = new ArrayList<>();
        options.ruleFiles().forEach(p -> files.add(p.toString()));
        m.put("rules", files);
        m.put("missingLob", options.missingLobPolicy().name());
        m.put("jvmTimeZone", java.util.TimeZone.getDefault().getID());
        m.put("java", System.getProperty("java.version"));
    }

    private static void describe(Map<String, Object> m, ConnectionSpec spec, SchemaModel model) {
        m.put("vendor", model.vendor().displayName());
        m.put("product", model.productName());
        m.put("productVersion", model.productVersion());
        m.put("driver", model.driver());
        m.put("url", spec.safeUrl());
        m.put(
                "schema",
                model.vendor().isMySqlFamily()
                        ? model.dialect().catalog()
                        : model.dialect().schema());
        int sequenceTables = 0;
        for (Table t : model.tables()) {
            sequenceTables += Planner.isSequenceTable(t) ? 1 : 0;
        }
        m.put("tables", model.tables().size());
        m.put("sequences", model.sequences().size());
        m.put("sequenceTables", sequenceTables);
    }

    private void log(String message) {
        synchronized (out) {
            out.println("[db-migrate] " + message);
        }
    }

    private static ThreadFactory daemon(String name) {
        AtomicLong n = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
