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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import javax.xml.bind.DatatypeConverter;
import org.apereo.portal.events.aggr.AcademicTermDetail;
import org.apereo.portal.events.aggr.dao.IEventAggregationManagementDao;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;

/**
 * An academic term's start is stored and exported as uPortal 5.17 did with Joda-Time's
 * DateMidnight. The term's date is a day in the zone the file gives, or in the JVM's zone without
 * one. Where that day's midnight repeats, the stored start keeps the file's offset, and the export
 * writes the stored midnight with its offset. The expected values were recorded on uPortal
 * 47d6333e2 with Joda-Time 2.14.3.
 */
@RunWith(Parameterized.class)
public class TermDateZoneTest {
    @Parameters(name = "{1} in a JVM in {0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(
                new Object[][] {
                    // a term date at +10:00 imported in New York: the day in the file's zone
                    {
                        "America/New_York",
                        "2026-09-06+10:00",
                        "2026-09-05T14:00:00Z",
                        "2026-09-05-04:00"
                    },
                    {"America/New_York", "2026-11-01", "2026-11-01T04:00:00Z", "2026-11-01-04:00"},
                    // 01:00 -04:00 becomes 00:00 -05:00: the second midnight, as Joda-Time stored
                    // it
                    {"America/Havana", "2026-11-01", "2026-11-01T05:00:00Z", "2026-11-01-05:00"},
                    // the day starts in a gap, at 01:00 -04:00
                    {"America/Havana", "2026-03-08", "2026-03-08T05:00:00Z", "2026-03-08-04:00"},
                    // 01:00 +00:00 becomes 00:00 -01:00
                    {"Atlantic/Azores", "2026-10-25", "2026-10-25T01:00:00Z", "2026-10-25-01:00"},
                    {
                        "Atlantic/Azores",
                        "2026-11-01-05:00",
                        "2026-11-01T05:00:00Z",
                        "2026-11-01-01:00"
                    },
                });
    }

    private final String jvmZone;
    private final String startText;
    private final Instant storedStart;
    private final String exportedStart;
    private TimeZone defaultZone;

    public TermDateZoneTest(
            String jvmZone, String startText, String storedStart, String exportedStart) {
        this.jvmZone = jvmZone;
        this.startText = startText;
        this.storedStart = Instant.parse(storedStart);
        this.exportedStart = exportedStart;
    }

    @Before
    public void setJvmZone() {
        this.defaultZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(this.jvmZone)));
    }

    @After
    public void restoreJvmZone() {
        TimeZone.setDefault(this.defaultZone);
    }

    @Test
    public void storesAndExportsTheTermStartAsJodaTimeDid() {
        final IEventAggregationManagementDao dao = mock(IEventAggregationManagementDao.class);
        final EventAggregationConfigurationImporterExporter importerExporter =
                new EventAggregationConfigurationImporterExporter();
        importerExporter.setAggregationManagementDao(dao);

        // Import, as the JAXB binding reads an xs:date
        final ExternalTermDetail externalTerm = new ExternalTermDetail();
        externalTerm.setName("term");
        externalTerm.setStart(DatatypeConverter.parseDate(this.startText));
        externalTerm.setEnd(DatatypeConverter.parseDate("2026-12-15"));
        final ExternalEventAggregationConfiguration data =
                new ExternalEventAggregationConfiguration();
        data.getTermDetails().add(externalTerm);
        importerExporter.importData(data);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<AcademicTermDetail>> imported =
                ArgumentCaptor.forClass((Class<List<AcademicTermDetail>>) (Class<?>) List.class);
        verify(dao).setAcademicTermDetails(imported.capture());
        final List<AcademicTermDetail> terms = new ArrayList<>(imported.getValue());
        assertEquals(1, terms.size());
        final AcademicTermDetail term = terms.get(0);
        final ZoneId zone = ZoneId.systemDefault();
        assertEquals(
                "the term starts at the stored instant",
                0,
                term.compareTo(this.storedStart.atZone(zone)));
        assertNotEquals(
                "the term doesn't start a millisecond earlier",
                0,
                term.compareTo(this.storedStart.minusMillis(1).atZone(zone)));

        // Export, as the JAXB binding writes an xs:date
        when(dao.getAcademicTermDetails()).thenReturn(terms);
        final ExternalEventAggregationConfiguration exported =
                importerExporter.exportData("CONFIG");
        assertEquals(
                this.exportedStart,
                DatatypeConverter.printDate(exported.getTermDetails().get(0).getStart()));
    }
}
