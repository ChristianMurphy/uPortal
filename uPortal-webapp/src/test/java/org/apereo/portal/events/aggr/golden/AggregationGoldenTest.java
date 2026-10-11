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
package org.apereo.portal.events.aggr.golden;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.events.GoldenEventStream;
import org.apereo.portal.events.GoldenEventStream.Burst;
import org.apereo.portal.events.PortalEvent;
import org.apereo.portal.events.aggr.AggregatedGroupConfig;
import org.apereo.portal.events.aggr.AggregatedIntervalConfig;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.AggregationIntervalHelper;
import org.apereo.portal.events.aggr.AggregationIntervalInfo;
import org.apereo.portal.events.aggr.BaseAggregationImpl;
import org.apereo.portal.events.aggr.IEventAggregatorStatus;
import org.apereo.portal.events.aggr.IEventAggregatorStatus.ProcessingType;
import org.apereo.portal.events.aggr.IPortalEventAggregator;
import org.apereo.portal.events.aggr.IPortalEventProcessingManager;
import org.apereo.portal.events.aggr.UniqueStrings;
import org.apereo.portal.events.aggr.action.SearchRequestAggregator;
import org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregation;
import org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregationDao;
import org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregationKeyImpl;
import org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregator;
import org.apereo.portal.events.aggr.dao.DateDimensionDao;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.dao.TimeDimensionDao;
import org.apereo.portal.events.aggr.dao.jpa.AcademicTermDetailImpl;
import org.apereo.portal.events.aggr.groups.AggregatedGroupLookupDao;
import org.apereo.portal.events.aggr.groups.AggregatedGroupMapping;
import org.apereo.portal.events.aggr.login.LoginAggregation;
import org.apereo.portal.events.aggr.login.LoginAggregationDao;
import org.apereo.portal.events.aggr.login.LoginAggregationKeyImpl;
import org.apereo.portal.events.aggr.login.LoginPortalEventAggregator;
import org.apereo.portal.events.aggr.portletexec.PortletExecutionAggregator;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregator;
import org.apereo.portal.events.aggr.portlets.AggregatedPortletLookupDao;
import org.apereo.portal.events.aggr.session.EventSessionImpl;
import org.apereo.portal.events.aggr.tabrender.TabRenderAggregator;
import org.apereo.portal.events.aggr.tabs.AggregatedTabLookupDao;
import org.apereo.portal.events.handlers.db.IPortalEventDao;
import org.apereo.portal.test.golden.DatabaseVendor;
import org.apereo.portal.test.golden.GoldenFiles;
import org.apereo.portal.test.golden.GoldenRenderer;
import org.apereo.portal.test.golden.JvmTimeZone;
import org.apereo.portal.test.golden.PersistenceUnits;
import org.apereo.portal.version.dao.VersionDao;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.context.support.GenericXmlApplicationContext;

/**
 * Analytics aggregation goldens (MOD-D29 item 3): {@link GoldenEventStream} is stored through the
 * raw-event store and aggregated by the real pipeline, the way the scheduler runs it ({@code
 * populateDimensions}, then {@code aggregateRawEvents}, which also closes finished intervals), in
 * two JVM time zones. The aggregate rows (logins, concurrent users, tab renders, portlet
 * executions, layout changes, searches), the event sessions, the aggregation status and one stored
 * raw event of each type are compared with {@code aggregation/<zone>.txt}.
 *
 * <p>The aggregation configuration is uPortal-start's {@code default.event-aggregation.xml}
 * (local.Everyone and local.Students; five-minute, hour, day and month intervals), with the login
 * aggregator also on WEEK, CALENDAR_QUARTER, ACADEMIC_TERM and YEAR so that every interval type is
 * computed, plus two academic terms. Each {@link Burst} is aggregated from empty tables, so the
 * closing of intervals (which walks every five minutes between the first and last event) stays
 * short.
 */
@RunWith(Parameterized.class)
public class AggregationGoldenTest {
    private static GenericXmlApplicationContext context;
    private static String contextZone;

    private final String zone;

    public AggregationGoldenTest(String zone) {
        this.zone = zone;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> zones() {
        final List<Object[]> parameters = new ArrayList<>();
        for (final String zone : JvmTimeZone.GOLDEN_ZONES) {
            parameters.add(new Object[] {zone});
        }
        return parameters;
    }

    @AfterClass
    public static void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    private static GenericXmlApplicationContext context(String zone) {
        if (context != null && !zone.equals(contextZone)) {
            closeContext();
        }
        if (context == null) {
            context =
                    new GenericXmlApplicationContext("classpath:goldenAggregationTestContext.xml");
            contextZone = zone;
        }
        return context;
    }

    @Test
    public void aggregatesTheEventStream() {
        try (JvmTimeZone timeZone = JvmTimeZone.set(zone)) {
            final GenericXmlApplicationContext ctx = context(zone);
            final EntityManagerFactory raw =
                    ctx.getBean("RawEventsDbEntityManagerFactory", EntityManagerFactory.class);
            final EntityManagerFactory aggr =
                    ctx.getBean("AggrEventsDbEntityManagerFactory", EntityManagerFactory.class);
            final VersionDao versionDao = ctx.getBean(VersionDao.class);
            final DatabaseVendor vendor =
                    DatabaseVendor.of(ctx.getBean("AggrEventsDb", javax.sql.DataSource.class));

            PersistenceUnits.deleteAll(raw);
            PersistenceUnits.deleteAll(aggr);
            versionDao.setVersion("RawEventsDb", 4, 1, 0, null);
            versionDao.setVersion("AggrEventsDb", 4, 1, 0, null);
            try {
                // Configured in one transaction, as the data import does
                ctx.getBean(
                                "aggrTransactionOperations",
                                org.springframework.transaction.support.TransactionOperations.class)
                        .execute(
                                status -> {
                                    configure(ctx);
                                    return null;
                                });
                final List<String> golden = new ArrayList<>();
                for (final Burst burst : Burst.values()) {
                    golden.addAll(aggregate(ctx, raw, aggr, burst));
                }
                GoldenFiles.verify(
                        "aggregation/" + timeZone.fileId() + ".txt",
                        golden,
                        vendor,
                        "JVM zone " + zone,
                        line -> line);
            } finally {
                PersistenceUnits.deleteAll(raw);
                PersistenceUnits.deleteAll(aggr);
                final EntityManagerFactory portal =
                        ctx.getBean("PortalDbEntityManagerFactory", EntityManagerFactory.class);
                final EntityManager em = portal.createEntityManager();
                try {
                    em.getTransaction().begin();
                    for (final String product : Arrays.asList("RawEventsDb", "AggrEventsDb")) {
                        em.createQuery("DELETE FROM VersionImpl v WHERE v.product = :product")
                                .setParameter("product", product)
                                .executeUpdate();
                    }
                    em.getTransaction().commit();
                } finally {
                    em.close();
                }
            }
        }
    }

    /** uPortal-start's default.event-aggregation.xml, plus all intervals for logins and terms */
    private static void configure(GenericXmlApplicationContext ctx) {
        final IEventAggregationManagementDao dao =
                ctx.getBean(IEventAggregationManagementDao.class);
        final AggregatedGroupLookupDao groups = ctx.getBean(AggregatedGroupLookupDao.class);

        final AggregatedGroupConfig groupConfig =
                dao.createAggregatedGroupConfig(IPortalEventAggregator.class);
        groupConfig.getIncluded().add(groups.getGroupMapping("local", "Everyone"));
        groupConfig.getIncluded().add(groups.getGroupMapping("local", "Students"));
        dao.updateAggregatedGroupConfig(groupConfig);

        intervals(dao, ConcurrentUserAggregator.class, AggregationInterval.FIVE_MINUTE);
        intervals(
                dao,
                LoginPortalEventAggregator.class,
                AggregationInterval.FIVE_MINUTE,
                AggregationInterval.HOUR,
                AggregationInterval.DAY,
                AggregationInterval.WEEK,
                AggregationInterval.MONTH,
                AggregationInterval.CALENDAR_QUARTER,
                AggregationInterval.ACADEMIC_TERM,
                AggregationInterval.YEAR);
        intervals(
                dao,
                PortletExecutionAggregator.class,
                AggregationInterval.HOUR,
                AggregationInterval.DAY,
                AggregationInterval.MONTH);
        intervals(
                dao,
                TabRenderAggregator.class,
                AggregationInterval.HOUR,
                AggregationInterval.DAY,
                AggregationInterval.MONTH);
        intervals(
                dao,
                PortletLayoutAggregator.class,
                AggregationInterval.DAY,
                AggregationInterval.MONTH);
        intervals(
                dao,
                SearchRequestAggregator.class,
                AggregationInterval.FIVE_MINUTE,
                AggregationInterval.HOUR,
                AggregationInterval.DAY);

        dao.getQuartersDetails();
        dao.setAcademicTermDetails(
                Arrays.asList(
                        new AcademicTermDetailImpl(
                                new DateMidnight(2025, 8, 25),
                                new DateMidnight(2025, 12, 20),
                                "Fall 2025"),
                        new AcademicTermDetailImpl(
                                new DateMidnight(2026, 1, 12),
                                new DateMidnight(2026, 5, 9),
                                "Spring 2026")));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void intervals(
            IEventAggregationManagementDao dao,
            Class<? extends IPortalEventAggregator> aggregator,
            AggregationInterval... included) {
        final AggregatedIntervalConfig config =
                dao.createAggregatedIntervalConfig((Class) aggregator);
        config.getIncluded().addAll(EnumSet.copyOf(Arrays.asList(included)));
        dao.updateAggregatedIntervalConfig(config);
    }

    /** Stores and aggregates one burst from empty tables; its golden lines */
    private static List<String> aggregate(
            GenericXmlApplicationContext ctx,
            EntityManagerFactory raw,
            EntityManagerFactory aggr,
            Burst burst) {
        // Keep the configuration and the date/time dimensions, drop the previous burst's output
        PersistenceUnits.deleteAll(raw);
        PersistenceUnits.deleteAll(
                aggr,
                type ->
                        BaseAggregationImpl.class.isAssignableFrom(type)
                                || type == EventSessionImpl.class
                                || type == UniqueStrings.class
                                || IEventAggregatorStatus.class.isAssignableFrom(type));

        final List<PortalEvent> events = GoldenEventStream.events(burst);
        ctx.getBean(IPortalEventDao.class).storePortalEvents(events);

        final IPortalEventProcessingManager manager =
                ctx.getBean(IPortalEventProcessingManager.class);
        assertTrue(burst + ": populateDimensions did not run", manager.populateDimensions());
        boolean complete = false;
        for (int run = 0; run < 20 && !complete; run++) {
            complete = manager.aggregateRawEvents();
        }
        assertTrue(burst + ": aggregateRawEvents did not complete", complete);

        final List<String> lines = new ArrayList<>();
        final String prefix = burst.name().toLowerCase(java.util.Locale.ROOT) + " ";
        lines.add(prefix + "events " + events.size());
        lines.addAll(rawEventSamples(raw, prefix));
        lines.addAll(queries(ctx, burst, prefix));
        final GoldenRenderer renderer = new GoldenRenderer(aggr.getMetamodel());
        final EntityManager em = aggr.createEntityManager();
        try {
            em.getTransaction().begin();
            final Map<String, Integer> counts = new TreeMap<>();
            final List<String> rows = new ArrayList<>();
            for (final Class<?> type : outputTypes(aggr)) {
                for (final Object entity :
                        em.createQuery("SELECT e FROM " + type.getName() + " e").getResultList()) {
                    rows.add(prefix + type.getSimpleName() + " " + render(renderer, entity));
                    counts.merge(type.getSimpleName(), 1, Integer::sum);
                }
            }
            for (final Map.Entry<String, Integer> count : counts.entrySet()) {
                lines.add(prefix + "count " + count.getKey() + " " + count.getValue());
            }
            rows.sort(String::compareTo);
            lines.addAll(rows);
            em.getTransaction().rollback();
        } finally {
            em.close();
        }
        return lines;
    }

    private static Set<Class<?>> outputTypes(EntityManagerFactory aggr) {
        final Set<Class<?>> types = new LinkedHashSet<>();
        for (final javax.persistence.metamodel.EntityType<?> type :
                PersistenceUnits.entityTypes(aggr.getMetamodel())) {
            final Class<?> javaType = type.getJavaType();
            if (BaseAggregationImpl.class.isAssignableFrom(javaType)
                    || javaType == EventSessionImpl.class
                    || IEventAggregatorStatus.class.isAssignableFrom(javaType)) {
                types.add(javaType);
            }
        }
        return types;
    }

    /**
     * The status rows record when aggregation last started and ended (wall-clock "now"); only the
     * date of the last aggregated event is deterministic
     */
    private static String render(GoldenRenderer renderer, Object entity) {
        if (entity instanceof IEventAggregatorStatus) {
            final IEventAggregatorStatus status = (IEventAggregatorStatus) entity;
            final ProcessingType type = status.getProcessingType();
            return "{processingType="
                    + type
                    + ", lastEventDate="
                    + GoldenRenderer.renderValue(status.getLastEventDate())
                    + "}";
        }
        return renderer.render(entity);
    }

    /**
     * The read side, on the rows the pipeline wrote: the interval arithmetic for the burst's
     * visits, the raw-event store's time queries, the date and time dimension lookups, and the
     * aggregation DAO queries the statistics reports use.
     */
    private static List<String> queries(
            GenericXmlApplicationContext ctx, Burst burst, String prefix) {
        final List<String> lines = new ArrayList<>();
        final AggregationIntervalHelper intervals = ctx.getBean(AggregationIntervalHelper.class);
        for (final java.time.Instant visit : burst.visits()) {
            final DateTime at = new DateTime(visit.toEpochMilli());
            for (final AggregationInterval interval : AggregationInterval.values()) {
                final AggregationIntervalInfo info = intervals.getIntervalInfo(interval, at);
                lines.add(
                        prefix
                                + "interval "
                                + visit
                                + " "
                                + interval
                                + " "
                                + (info == null
                                        ? "null"
                                        : GoldenRenderer.renderValue(info.getStart())
                                                + " .. "
                                                + GoldenRenderer.renderValue(info.getEnd())
                                                + " minutes "
                                                + info.getTotalDuration()
                                                + " to visit "
                                                + info.getDurationTo(at)));
            }
        }
        final DateTime first = new DateTime(burst.visits().get(0).toEpochMilli());
        final DateTime last =
                new DateTime(burst.visits().get(burst.visits().size() - 1).toEpochMilli());
        lines.add(
                prefix
                        + "intervalsBetween HOUR "
                        + intervals.intervalsBetween(
                                AggregationInterval.HOUR, first, last.plusHours(1)));
        lines.add(
                prefix
                        + "getIntervalStartDateTimesBetween DAY "
                        + render(
                                intervals.getIntervalStartDateTimesBetween(
                                        AggregationInterval.DAY,
                                        first.minusDays(1),
                                        last.plusDays(1))));

        final IPortalEventDao events = ctx.getBean(IPortalEventDao.class);
        lines.add(
                prefix
                        + "portalEventDao.getOldestPortalEventTimestamp() "
                        + GoldenRenderer.renderValue(events.getOldestPortalEventTimestamp()));
        lines.add(
                prefix
                        + "portalEventDao.getNewestPortalEventTimestamp() "
                        + GoldenRenderer.renderValue(events.getNewestPortalEventTimestamp()));
        final List<String> read = new ArrayList<>();
        // Streams a scrollable result, so (like the aggregator) inside a raw-events transaction
        ctx.getBean(
                        "rawTransactionOperations",
                        org.springframework.transaction.support.TransactionOperations.class)
                .execute(
                        status -> {
                            events.getPortalEvents(
                                    first.minusMinutes(1),
                                    first.plusMinutes(10),
                                    new org.apereo.portal.concurrency.FunctionWithoutResult<
                                            PortalEvent>() {
                                        @Override
                                        protected void applyWithoutResult(PortalEvent event) {
                                            read.add(
                                                    event.getClass().getSimpleName()
                                                            + "@"
                                                            + GoldenRenderer.renderValue(
                                                                    event.getTimestampAsDate())
                                                            + " "
                                                            + event.getUserName());
                                        }
                                    });
                            return null;
                        });
        // In time order; the events of one second come in any order
        final List<String> byTime = new ArrayList<>(read);
        byTime.sort(
                java.util.Comparator.comparing((String event) -> event.split("[@ ]")[1])
                        .thenComparing(java.util.Comparator.naturalOrder()));
        boolean inTimeOrder = true;
        for (int i = 1; i < read.size(); i++) {
            inTimeOrder &=
                    read.get(i - 1).split("[@ ]")[1].compareTo(read.get(i).split("[@ ]")[1]) <= 0;
        }
        lines.add(
                prefix
                        + "portalEventDao.getPortalEvents(first visit) "
                        + read.size()
                        + (inTimeOrder ? " in time order " : " NOT in time order ")
                        + byTime);

        final DateDimensionDao dateDimensions = ctx.getBean(DateDimensionDao.class);
        final TimeDimensionDao timeDimensions = ctx.getBean(TimeDimensionDao.class);
        final GoldenRenderer renderer =
                new GoldenRenderer(
                        ctx.getBean("AggrEventsDbEntityManagerFactory", EntityManagerFactory.class)
                                .getMetamodel());
        lines.add(
                prefix
                        + "dateDimensionDao.getDateDimensionByDate "
                        + renderer.render(
                                dateDimensions.getDateDimensionByDate(first.toDateMidnight())));
        lines.add(
                prefix
                        + "dateDimensionDao.getDateDimensionsBetween "
                        + dateDimensions
                                .getDateDimensionsBetween(
                                        first.minusDays(1).toDateMidnight(),
                                        last.plusDays(1).toDateMidnight())
                                .size());
        lines.add(
                prefix
                        + "timeDimensionDao.getTimeDimensionByTime(01:30) "
                        + renderer.render(
                                timeDimensions.getTimeDimensionByTime(
                                        new org.joda.time.LocalTime(1, 30))));

        final AggregatedGroupLookupDao groups = ctx.getBean(AggregatedGroupLookupDao.class);
        final AggregatedGroupMapping everyone = groups.getGroupMapping("local", "Everyone");
        final AggregatedGroupMapping students = groups.getGroupMapping("local", "Students");
        lines.add(
                prefix
                        + "aggregatedGroupLookupDao.getGroupMappings() "
                        + renderAll(renderer, groups.getGroupMappings()));
        lines.add(
                prefix
                        + "aggregatedTabLookupDao.getTabMappings() "
                        + renderAll(
                                renderer,
                                ctx.getBean(AggregatedTabLookupDao.class).getTabMappings()));
        lines.add(
                prefix
                        + "aggregatedPortletLookupDao.getPortletMappings() "
                        + renderAll(
                                renderer,
                                ctx.getBean(AggregatedPortletLookupDao.class)
                                        .getPortletMappings()));

        @SuppressWarnings("unchecked")
        final LoginAggregationDao<LoginAggregation> logins = ctx.getBean(LoginAggregationDao.class);
        lines.add(
                prefix
                        + "loginAggregationDao.getAggregationIntervals() "
                        + new java.util.TreeSet<>(logins.getAggregationIntervals()));
        lines.add(
                prefix
                        + "loginAggregationDao.getAggregatedGroupMappings() "
                        + renderAll(renderer, logins.getAggregatedGroupMappings()));
        final DateTime start = first.minusDays(1);
        final DateTime end = last.plusDays(1);
        for (final AggregationInterval interval :
                Arrays.asList(
                        AggregationInterval.HOUR,
                        AggregationInterval.DAY,
                        AggregationInterval.MONTH)) {
            final List<LoginAggregation> found =
                    logins.getAggregations(
                            start,
                            end,
                            new LoginAggregationKeyImpl(interval, everyone),
                            everyone,
                            students);
            // Ordered by date and time only, so the rank counts distinct times: the rows of one
            // time (one per group) may come in any order
            int rank = 0;
            for (int i = 0; i < found.size(); i++) {
                if (i > 0 && !found.get(i).getDateTime().equals(found.get(i - 1).getDateTime())) {
                    rank++;
                }
                lines.add(
                        prefix
                                + "loginAggregationDao.getAggregations("
                                + interval
                                + ", Everyone+Students)#"
                                + rank
                                + " "
                                + renderer.render(found.get(i)));
            }
        }
        @SuppressWarnings("unchecked")
        final ConcurrentUserAggregationDao<ConcurrentUserAggregation> concurrent =
                ctx.getBean(ConcurrentUserAggregationDao.class);
        final List<ConcurrentUserAggregation> users =
                concurrent.getAggregations(
                        start,
                        end,
                        new ConcurrentUserAggregationKeyImpl(
                                AggregationInterval.FIVE_MINUTE, everyone),
                        everyone);
        lines.add(
                prefix
                        + "concurrentUserAggregationDao.getAggregations(FIVE_MINUTE, Everyone) "
                        + users.size()
                        + " "
                        + renderCounts(users));
        return lines;
    }

    private static String render(List<DateTime> dateTimes) {
        final List<String> rendered = new ArrayList<>();
        for (final DateTime dateTime : dateTimes) {
            rendered.add(GoldenRenderer.renderValue(dateTime));
        }
        return rendered.toString();
    }

    private static String renderAll(GoldenRenderer renderer, Collection<?> values) {
        final List<String> rendered = new ArrayList<>();
        for (final Object value : values) {
            rendered.add(renderer.render(value));
        }
        rendered.sort(String::compareTo);
        return rendered.toString();
    }

    private static String renderCounts(List<ConcurrentUserAggregation> aggregations) {
        final List<String> rendered = new ArrayList<>();
        for (final ConcurrentUserAggregation aggregation : aggregations) {
            rendered.add(
                    aggregation
                                    .getDateTime()
                                    .withZone(org.joda.time.DateTimeZone.UTC)
                                    .toString("MM-dd'T'HH:mm")
                            + "="
                            + aggregation.getConcurrentUsers());
        }
        return rendered.toString();
    }

    /**
     * The first stored raw event of each type (by time, then JSON, as events can share a time): the
     * JSON the event store writes
     */
    private static List<String> rawEventSamples(EntityManagerFactory raw, String prefix) {
        final Map<String, String> samples = new TreeMap<>();
        final EntityManager em = raw.createEntityManager();
        try {
            // PostgreSQL reads large objects only inside a transaction
            em.getTransaction().begin();
            final List<String> rows = new ArrayList<>();
            for (final Object result :
                    em.createQuery(
                                    "SELECT e.eventType, e.timestamp, e.eventData"
                                            + " FROM PersistentPortalEvent e")
                            .getResultList()) {
                final Object[] row = (Object[]) result;
                rows.add(
                        ((Class<?>) row[0]).getSimpleName()
                                + " "
                                + GoldenRenderer.renderValue(row[1])
                                + " "
                                + GoldenRenderer.renderValue(row[2]));
            }
            em.getTransaction().rollback();
            rows.sort(String::compareTo);
            for (final String row : rows) {
                samples.putIfAbsent(row.substring(0, row.indexOf(' ')), prefix + "raw " + row);
            }
        } finally {
            em.close();
        }
        return new ArrayList<>(samples.values());
    }
}
