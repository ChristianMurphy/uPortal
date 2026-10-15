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
package org.apereo.portal.io.xml.eventaggr;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.apereo.portal.events.aggr.AcademicTermDetail;
import org.apereo.portal.events.aggr.AggregatedGroupConfig;
import org.apereo.portal.events.aggr.AggregatedIntervalConfig;
import org.apereo.portal.events.aggr.AggregationInterval;
import org.apereo.portal.events.aggr.EventDateTimeUtils;
import org.apereo.portal.events.aggr.IPortalEventAggregator;
import org.apereo.portal.events.aggr.QuarterDetail;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.apereo.portal.events.aggr.dao.jpa.AcademicTermDetailImpl;
import org.apereo.portal.events.aggr.dao.jpa.QuarterDetailImpl;
import org.apereo.portal.events.aggr.groups.AggregatedGroupLookupDao;
import org.apereo.portal.events.aggr.groups.AggregatedGroupMapping;
import org.apereo.portal.io.xml.AbstractJaxbDataHandler;
import org.apereo.portal.io.xml.IPortalData;
import org.apereo.portal.io.xml.IPortalDataType;
import org.apereo.portal.io.xml.PortalDataKey;
import org.apereo.portal.utils.EnumNameComparator;
import org.apereo.portal.utils.SafeFilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class EventAggregationConfigurationImporterExporter
        extends AbstractJaxbDataHandler<ExternalEventAggregationConfiguration> {
    private static final DateTimeFormatter LENIENT_MONTH_DAY =
            new DateTimeFormatterBuilder()
                    .appendLiteral("--")
                    .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral('-')
                    .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                    .toFormatter();

    private static final String SINGLE_DATA_ID = "CONFIG";

    private EventAggregationConfigurationPortalDataType eventAggregationDataType;
    private IEventAggregationManagementDao aggregationManagementDao;
    private AggregatedGroupLookupDao aggregatedGroupLookupDao;

    @Autowired
    public void setAggregatedGroupLookupDao(AggregatedGroupLookupDao aggregatedGroupLookupDao) {
        this.aggregatedGroupLookupDao = aggregatedGroupLookupDao;
    }

    @Autowired
    public void setEventAggregationDataType(
            EventAggregationConfigurationPortalDataType eventAggregationDataType) {
        this.eventAggregationDataType = eventAggregationDataType;
    }

    @Autowired
    public void setAggregationManagementDao(
            IEventAggregationManagementDao aggregationManagementDao) {
        this.aggregationManagementDao = aggregationManagementDao;
    }

    @Override
    public Set<PortalDataKey> getImportDataKeys() {
        return Collections.singleton(
                EventAggregationConfigurationPortalDataType.IMPORT_40_DATA_KEY);
    }

    @Override
    public IPortalDataType getPortalDataType() {
        return this.eventAggregationDataType;
    }

    @Override
    public Iterable<? extends IPortalData> getPortalData() {
        return ImmutableSet.of(
                new IPortalData() {
                    @Override
                    public String getDataId() {
                        return "Event Aggregation Configuration";
                    }

                    @Override
                    public String getDataTitle() {
                        return SINGLE_DATA_ID;
                    }

                    @Override
                    public String getDataDescription() {
                        return null;
                    }
                });
    }

    @Transactional("aggrEventsTransactionManager")
    @Override
    public void importData(ExternalEventAggregationConfiguration data) {
        // Import interval configs
        final Set<AggregatedIntervalConfig> oldAggregatedIntervalConfigs =
                new HashSet<AggregatedIntervalConfig>(
                        this.aggregationManagementDao.getAggregatedIntervalConfigs());
        for (final ExternalAggregatedIntervalConfig extAggregatedIntervalConfig :
                data.getAggregatedIntervalConfigs()) {
            final String aggregatorTypeName = extAggregatedIntervalConfig.getAggregatorType();

            final Class<? extends IPortalEventAggregator> aggregatorType =
                    getAggregatorType(aggregatorTypeName);
            AggregatedIntervalConfig aggregatedIntervalConfig =
                    this.aggregationManagementDao.getAggregatedIntervalConfig(aggregatorType);
            if (aggregatedIntervalConfig == null) {
                aggregatedIntervalConfig =
                        this.aggregationManagementDao.createAggregatedIntervalConfig(
                                aggregatorType);
            }

            // Remove the config from the old configs set, marking it as updated
            oldAggregatedIntervalConfigs.remove(aggregatedIntervalConfig);

            // Copy over excludes
            final Set<AggregationInterval> excluded = aggregatedIntervalConfig.getExcluded();
            excluded.clear();
            for (final ExternalAggregationInterval extInterval :
                    extAggregatedIntervalConfig.getExcludes()) {
                excluded.add(convert(extInterval));
            }

            // Copy over includes
            final Set<AggregationInterval> included = aggregatedIntervalConfig.getIncluded();
            included.clear();
            for (final ExternalAggregationInterval extInterval :
                    extAggregatedIntervalConfig.getIncludes()) {
                included.add(convert(extInterval));
            }

            this.aggregationManagementDao.updateAggregatedIntervalConfig(aggregatedIntervalConfig);
        }

        // Delete interval configs that were not updated
        for (final AggregatedIntervalConfig aggregatedIntervalConfig :
                oldAggregatedIntervalConfigs) {
            this.aggregationManagementDao.deleteAggregatedIntervalConfig(aggregatedIntervalConfig);
        }

        // Import Group configs
        final Set<AggregatedGroupConfig> oldAggregatedGroupConfigs =
                new HashSet<AggregatedGroupConfig>(
                        this.aggregationManagementDao.getAggregatedGroupConfigs());
        for (final ExternalAggregatedGroupConfig extAggregatedGroupConfig :
                data.getAggregatedGroupConfigs()) {
            final String aggregatorTypeName = extAggregatedGroupConfig.getAggregatorType();

            final Class<? extends IPortalEventAggregator> aggregatorType =
                    getAggregatorType(aggregatorTypeName);
            AggregatedGroupConfig aggregatedGroupConfig =
                    this.aggregationManagementDao.getAggregatedGroupConfig(aggregatorType);
            if (aggregatedGroupConfig == null) {
                aggregatedGroupConfig =
                        this.aggregationManagementDao.createAggregatedGroupConfig(aggregatorType);
            }

            // Remove the config from the old configs set, marking it as updated
            oldAggregatedGroupConfigs.remove(aggregatedGroupConfig);

            // Copy over excludes
            final Set<AggregatedGroupMapping> excluded = aggregatedGroupConfig.getExcluded();
            excluded.clear();
            for (final ExternalAggregatedGroupMapping extGroup :
                    extAggregatedGroupConfig.getExcludes()) {
                excluded.add(convert(extGroup));
            }

            // Copy over includes
            final Set<AggregatedGroupMapping> included = aggregatedGroupConfig.getIncluded();
            included.clear();
            for (final ExternalAggregatedGroupMapping extGroup :
                    extAggregatedGroupConfig.getIncludes()) {
                included.add(convert(extGroup));
            }

            this.aggregationManagementDao.updateAggregatedGroupConfig(aggregatedGroupConfig);
        }

        // Delete interval configs that were not updated
        for (final AggregatedGroupConfig aggregatedGroupConfig : oldAggregatedGroupConfigs) {
            this.aggregationManagementDao.deleteAggregatedGroupConfig(aggregatedGroupConfig);
        }

        // Set quarter details if configured or set default quarters
        final List<ExternalQuarterDetail> extQuarterDetails = data.getQuarterDetails();
        final List<QuarterDetail> quarterDetails;
        if (!extQuarterDetails.isEmpty()) {
            quarterDetails = convertQuarterDetail(extQuarterDetails);
        } else {
            quarterDetails = EventDateTimeUtils.createStandardQuarters();
        }
        this.aggregationManagementDao.setQuarterDetails(quarterDetails);

        // Set academic term if configured
        final List<AcademicTermDetail> academicTerms =
                Lists.transform(
                        data.getTermDetails(),
                        new Function<ExternalTermDetail, AcademicTermDetail>() {
                            @Override
                            public AcademicTermDetail apply(ExternalTermDetail externalTermDetail) {
                                return new AcademicTermDetailImpl(
                                        startOfDay(externalTermDetail.getStart()),
                                        startOfDay(externalTermDetail.getEnd()),
                                        externalTermDetail.getName());
                            }
                        });
        this.aggregationManagementDao.setAcademicTermDetails(academicTerms);
    }

    /** The start of the calendar's day in the calendar's zone, as Joda-Time's DateMidnight was */
    private static ZonedDateTime startOfDay(Calendar calendar) {
        final ZoneId zone = calendar.getTimeZone().toZoneId();
        return Instant.ofEpochMilli(calendar.getTimeInMillis())
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone);
    }

    /**
     * The start of the day in the JVM's zone, as Joda-Time's DateMidnight.toGregorianCalendar()
     * built it, so the exported xs:date keeps its text
     */
    private static Calendar toCalendar(LocalDate date) {
        final ZoneId zone = ZoneId.systemDefault();
        final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone(zone));
        calendar.setTimeInMillis(date.atStartOfDay(zone).toInstant().toEpochMilli());
        return calendar;
    }

    /**
     * A quarter's start or end: {@code --MM-dd} as exported, also with one-digit fields, or a full
     * ISO date whose month and day are used. Joda-Time's MonthDay.parse accepted these.
     */
    static MonthDay parseMonthDay(String text) {
        try {
            return MonthDay.parse(text, LENIENT_MONTH_DAY);
        } catch (DateTimeParseException e) {
            return MonthDay.from(LocalDate.parse(text));
        }
    }

    protected List<QuarterDetail> convertQuarterDetail(
            List<ExternalQuarterDetail> externalQuarterDetails) {
        final List<QuarterDetail> quarterDetails = new ArrayList<QuarterDetail>(4);
        for (final ExternalQuarterDetail externalQuarterDetail : externalQuarterDetails) {
            quarterDetails.add(
                    new QuarterDetailImpl(
                            parseMonthDay(externalQuarterDetail.getStart()),
                            parseMonthDay(externalQuarterDetail.getEnd()),
                            externalQuarterDetail.getId()));
        }
        return quarterDetails;
    }

    protected AggregatedGroupMapping convert(
            ExternalAggregatedGroupMapping externalAggregatedGroupMapping) {
        return this.aggregatedGroupLookupDao.getGroupMapping(
                externalAggregatedGroupMapping.getGroupService(),
                externalAggregatedGroupMapping.getGroupName());
    }

    protected AggregationInterval convert(ExternalAggregationInterval externalAggregationInterval) {
        return AggregationInterval.valueOf(externalAggregationInterval.name());
    }

    protected Class<? extends IPortalEventAggregator> getAggregatorType(String aggregatorTypeName) {

        /*
         * For version 5.0, all uPortal sources were repackaged from 'org.jasig.portal'
         * to 'org.apereo.portal'.  *.event-aggregation.xml files exported from earlier
         * versions of uPortal will contain the old package name.  We can detect that
         * and intervene here.
         */
        aggregatorTypeName = aggregatorTypeName.replace("org.jasig.portal", "org.apereo.portal");

        final Class<?> aggregatorType;
        try {
            aggregatorType = Class.forName(aggregatorTypeName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Specified aggregator type name "
                            + aggregatorTypeName
                            + " could not be resolved to a Class",
                    e);
        }

        if (!IPortalEventAggregator.class.isAssignableFrom(aggregatorType)) {
            throw new IllegalArgumentException(
                    "Specified aggregator type "
                            + aggregatorType.getName()
                            + " is not an instance of "
                            + IPortalEventAggregator.class.getName());
        }
        return (Class<? extends IPortalEventAggregator>) aggregatorType;
    }

    /*
     * (non-Javadoc)
     * @see org.apereo.portal.io.xml.IDataImporterExporter#exportData(java.lang.String)
     */
    @Override
    public ExternalEventAggregationConfiguration exportData(String id) {
        final ExternalEventAggregationConfiguration externalData =
                new ExternalEventAggregationConfiguration();

        // Copy interval configs
        final List<ExternalAggregatedIntervalConfig> aggregatedIntervalConfigs =
                externalData.getAggregatedIntervalConfigs();
        for (final AggregatedIntervalConfig aggregatedIntervalConfig :
                this.aggregationManagementDao.getAggregatedIntervalConfigs()) {
            final ExternalAggregatedIntervalConfig externalIntervalConfig =
                    new ExternalAggregatedIntervalConfig();
            externalIntervalConfig.setAggregatorType(
                    aggregatedIntervalConfig.getAggregatorType().getName());

            final List<ExternalAggregationInterval> extIncludes =
                    externalIntervalConfig.getIncludes();
            for (final AggregationInterval interval : aggregatedIntervalConfig.getIncluded()) {
                extIncludes.add(convert(interval));
            }
            Collections.sort(extIncludes, EnumNameComparator.INSTANCE);

            final List<ExternalAggregationInterval> extExcludes =
                    externalIntervalConfig.getExcludes();
            for (final AggregationInterval interval : aggregatedIntervalConfig.getExcluded()) {
                extExcludes.add(convert(interval));
            }
            Collections.sort(extExcludes, EnumNameComparator.INSTANCE);

            aggregatedIntervalConfigs.add(externalIntervalConfig);
        }
        Collections.sort(
                aggregatedIntervalConfigs, ExternalAggregatedDimensionConfigComparator.INSTANCE);

        // Copy group configs
        final List<ExternalAggregatedGroupConfig> aggregatedGroupConfigs =
                externalData.getAggregatedGroupConfigs();
        for (final AggregatedGroupConfig aggregatedGroupConfig :
                this.aggregationManagementDao.getAggregatedGroupConfigs()) {
            final ExternalAggregatedGroupConfig externalGroupConfig =
                    new ExternalAggregatedGroupConfig();
            externalGroupConfig.setAggregatorType(
                    aggregatedGroupConfig.getAggregatorType().getName());

            final List<ExternalAggregatedGroupMapping> extIncludes =
                    externalGroupConfig.getIncludes();
            for (final AggregatedGroupMapping Group : aggregatedGroupConfig.getIncluded()) {
                extIncludes.add(convert(Group));
            }
            Collections.sort(extIncludes, ExternalAggregatedGroupMappingComparator.INSTANCE);

            final List<ExternalAggregatedGroupMapping> extExcludes =
                    externalGroupConfig.getExcludes();
            for (final AggregatedGroupMapping Group : aggregatedGroupConfig.getExcluded()) {
                extExcludes.add(convert(Group));
            }
            Collections.sort(extExcludes, ExternalAggregatedGroupMappingComparator.INSTANCE);

            aggregatedGroupConfigs.add(externalGroupConfig);
        }
        Collections.sort(
                aggregatedGroupConfigs, ExternalAggregatedDimensionConfigComparator.INSTANCE);

        // Copy term details
        final List<ExternalTermDetail> externalTermDetails = externalData.getTermDetails();
        for (final AcademicTermDetail academicTermDetail :
                this.aggregationManagementDao.getAcademicTermDetails()) {
            final ExternalTermDetail externalTermDetail = new ExternalTermDetail();
            externalTermDetail.setName(academicTermDetail.getTermName());
            externalTermDetail.setStart(toCalendar(academicTermDetail.getStart()));
            externalTermDetail.setEnd(toCalendar(academicTermDetail.getEnd()));
            externalTermDetails.add(externalTermDetail);
        }
        Collections.sort(externalTermDetails, ExternalTermDetailComparator.INSTANCE);

        // Copy quarter details
        final List<ExternalQuarterDetail> quarterDetails = externalData.getQuarterDetails();
        for (final QuarterDetail quarterDetail :
                this.aggregationManagementDao.getQuartersDetails()) {
            final ExternalQuarterDetail externalQuarterDetail = new ExternalQuarterDetail();
            externalQuarterDetail.setId(quarterDetail.getQuarterId());
            externalQuarterDetail.setStart(quarterDetail.getStart().toString());
            externalQuarterDetail.setEnd(quarterDetail.getEnd().toString());
            quarterDetails.add(externalQuarterDetail);
        }
        Collections.sort(quarterDetails, ExternalQuarterDetailComparator.INSTANCE);

        return externalData;
    }

    protected ExternalAggregationInterval convert(AggregationInterval aggregationInterval) {
        return ExternalAggregationInterval.valueOf(aggregationInterval.name());
    }

    protected ExternalAggregatedGroupMapping convert(
            AggregatedGroupMapping aggregatedGroupMapping) {
        final ExternalAggregatedGroupMapping externalAggregatedGroupMapping =
                new ExternalAggregatedGroupMapping();
        externalAggregatedGroupMapping.setGroupService(aggregatedGroupMapping.getGroupService());
        externalAggregatedGroupMapping.setGroupName(aggregatedGroupMapping.getGroupName());
        return externalAggregatedGroupMapping;
    }

    @Override
    public String getFileName(ExternalEventAggregationConfiguration data) {
        return SafeFilenameUtils.makeSafeFilename("default");
    }

    /*
     * (non-Javadoc)
     * @see org.apereo.portal.io.xml.IDataImporterExporter#deleteData(java.lang.String)
     */
    @Transactional("aggrEventsTransactionManager")
    @Override
    public ExternalEventAggregationConfiguration deleteData(String id) {
        final ExternalEventAggregationConfiguration data = this.exportData(id);

        for (final AggregatedIntervalConfig aggregatedIntervalConfig :
                this.aggregationManagementDao.getAggregatedIntervalConfigs()) {
            this.aggregationManagementDao.deleteAggregatedIntervalConfig(aggregatedIntervalConfig);
        }

        for (final AggregatedGroupConfig aggregatedGroupConfig :
                this.aggregationManagementDao.getAggregatedGroupConfigs()) {
            this.aggregationManagementDao.deleteAggregatedGroupConfig(aggregatedGroupConfig);
        }

        this.aggregationManagementDao.setAcademicTermDetails(
                Collections.<AcademicTermDetail>emptyList());

        this.aggregationManagementDao.setQuarterDetails(
                EventDateTimeUtils.createStandardQuarters());

        return data;
    }
}
