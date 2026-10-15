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
package org.apereo.portal.events.aggr.dao.jpa;

import com.google.common.collect.ComparisonChain;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import org.apache.commons.lang.Validate;
import org.apereo.portal.events.aggr.AcademicTermDetail;
import org.apereo.portal.events.aggr.EventDateTimeUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;

/** */
@Entity
@Table(name = "UP_ACADEMIC_TERM_DETAIL")
@SequenceGenerator(
        name = "UP_ACADEMIC_TERM_DETAIL_GEN",
        sequenceName = "UP_ACADEMIC_TERM_DETAIL_SEQ",
        allocationSize = 1)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class AcademicTermDetailImpl implements AcademicTermDetail, Serializable {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    @Id
    @GeneratedValue(generator = "UP_ACADEMIC_TERM_DETAIL_GEN")
    @Column(name = "TERM_ID")
    private final long id;

    @NaturalId
    @Column(name = "TERM_START", nullable = false)
    private Instant start;

    @NaturalId
    @Column(name = "TERM_END", nullable = false)
    private Instant end;

    @Column(name = "TERM_NAME", nullable = false)
    private String termName;

    @Transient private LocalDate startDate;
    @Transient private LocalDate endDate;

    @SuppressWarnings("unused")
    private AcademicTermDetailImpl() {
        this.id = -1;
        this.start = null;
        this.end = null;
        this.termName = null;
    }

    /** A term from the start of one day to the start of another, in the JVM's zone */
    public AcademicTermDetailImpl(LocalDate start, LocalDate end, String termName) {
        this(
                start == null ? null : start.atStartOfDay(ZoneId.systemDefault()),
                end == null ? null : end.atStartOfDay(ZoneId.systemDefault()),
                termName);
    }

    /**
     * A term between two instants, normally the starts of two days. The import of an event
     * aggregation file passes the days' starts in the zone the file gives, as Joda-Time's
     * DateMidnight did.
     */
    public AcademicTermDetailImpl(ZonedDateTime start, ZonedDateTime end, String termName) {
        Validate.notNull(start);
        Validate.notNull(end);
        Validate.notNull(termName);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("end cannot be before or equal to start");
        }
        this.id = -1;
        this.start = start.toInstant();
        this.end = end.toInstant();
        this.termName = termName;
        this.init();
    }

    @PostLoad
    @PostUpdate
    @PostPersist
    void init() {
        this.startDate = this.start.atZone(ZoneId.systemDefault()).toLocalDate();
        this.endDate = this.end.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Midnight in the JVM's zone, the zone Jadira read the term dates in */
    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    @Override
    public String getTermName() {
        return this.termName;
    }

    @Override
    public void setTermName(String termName) {
        Validate.notNull(termName);
        this.termName = termName;
    }

    @Override
    public LocalDate getStart() {
        return this.startDate;
    }

    @Override
    public void setStart(LocalDate start) {
        this.startDate = start;
        this.start = startOfDay(start);
    }

    @Override
    public LocalDate getEnd() {
        return this.endDate;
    }

    @Override
    public void setEnd(LocalDate end) {
        this.endDate = end;
        this.end = startOfDay(end);
    }

    @Override
    public int compareTo(ZonedDateTime instant) {
        return EventDateTimeUtils.compareTo(this.start, this.end, instant.toInstant());
    }

    @Override
    public int compareTo(AcademicTermDetail o) {
        return ComparisonChain.start()
                .compare(this.getStart(), o.getStart())
                .compare(this.getEnd(), o.getEnd())
                .result();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((end == null) ? 0 : end.hashCode());
        result = prime * result + ((start == null) ? 0 : start.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof AcademicTermDetail)) return false;
        AcademicTermDetail other = (AcademicTermDetail) obj;
        if (getEnd() == null) {
            if (other.getEnd() != null) return false;
        } else if (!getEnd().equals(other.getEnd())) return false;
        if (getStart() == null) {
            if (other.getStart() != null) return false;
        } else if (!getStart().equals(other.getStart())) return false;
        return true;
    }

    @Override
    public String toString() {
        return "AcademicTermDetailImpl [termName="
                + this.termName
                + ", startDate="
                + this.startDate
                + ", endDate="
                + this.endDate
                + "]";
    }
}
