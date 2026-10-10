package org.apereo.portal.dbmigrate.copy;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apereo.portal.dbmigrate.plan.MigrationPlan;
import org.apereo.portal.dbmigrate.plan.TableMapping;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.FlowJobBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The copy as a Spring Batch job: one chunk-oriented step per table, grouped by foreign-key level.
 * The tables of a level run in parallel (a split over a task executor limited to {@code
 * --threads}); a level starts when the previous one has completed, so every table is loaded after
 * the tables it references, with constraints enabled. The deferred foreign keys follow, one step
 * per table.
 *
 * <p>The job repository is Spring Batch's JDBC repository on an in-memory H2 database: it is safe
 * for parallel steps, keeps the step metadata (counts, times, failures) that the report is built
 * from, and leaves the source and target schemas untouched. Restarting a failed copy is a new run
 * with {@code --clear-target}; a persistent repository (and saved reader positions) would make
 * steps restartable, see the follow-ups in the report.
 */
public final class CopyJob implements AutoCloseable {

    private final EmbeddedDatabase metadata;
    private final JobRepository repository;

    public CopyJob() throws Exception {
        metadata =
                new EmbeddedDatabaseBuilder()
                        .generateUniqueName(true)
                        .setType(EmbeddedDatabaseType.H2)
                        .addScript("classpath:org/springframework/batch/core/schema-h2.sql")
                        .build();
        JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(metadata);
        factory.setTransactionManager(new JdbcTransactionManager(metadata));
        factory.afterPropertiesSet();
        repository = factory.getObject();
    }

    public JobRepository repository() {
        return repository;
    }

    /** Transactions on the target database: one per chunk. */
    public static PlatformTransactionManager transactions(DataSource target) {
        return new JdbcTransactionManager(target);
    }

    /** Builds and runs the copy job; returns its execution with every step's metadata. */
    public JobExecution run(MigrationPlan plan, TableCopier copier, int threads) throws Exception {
        SimpleAsyncTaskExecutor parallel = new SimpleAsyncTaskExecutor("db-migrate-copy-");
        parallel.setConcurrencyLimit(threads);
        List<Flow> levels = new ArrayList<>();
        int n = 0;
        for (List<String> level : plan.order().levels()) {
            List<Flow> tables = new ArrayList<>();
            for (String key : level) {
                TableMapping m = plan.table(key);
                tables.add(
                        new FlowBuilder<SimpleFlow>("table:" + key)
                                .start(copier.copyStep(m))
                                .build());
            }
            levels.add(
                    new FlowBuilder<SimpleFlow>("level-" + n++)
                            .split(parallel)
                            .add(tables.toArray(new Flow[0]))
                            .build());
        }
        for (TableMapping m : plan.tables()) {
            if (!m.deferredForeignKeys().isEmpty()) {
                levels.add(
                        new FlowBuilder<SimpleFlow>("deferred:" + m.key())
                                .start(copier.deferredStep(m))
                                .build());
            }
        }
        if (levels.isEmpty()) {
            return null;
        }
        FlowBuilder<FlowJobBuilder> flow =
                new JobBuilder("db-migrate-copy", repository).start(levels.get(0));
        for (int i = 1; i < levels.size(); i++) {
            flow = flow.next(levels.get(i));
        }
        Job job = flow.end().build();

        TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
        operator.setJobRepository(repository);
        operator.setJobRegistry(new MapJobRegistry());
        operator.setTaskExecutor(new SyncTaskExecutor());
        operator.afterPropertiesSet();
        return operator.start(job, new JobParameters());
    }

    @Override
    public void close() {
        metadata.shutdown();
    }
}
