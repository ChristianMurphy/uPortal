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
package org.apereo.portal.dbmigrate.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;

/**
 * A source column and the target column it is copied into, with the logical type values travel as
 * and the normalization both sides are compared under.
 *
 * <p>The logical type is the narrower meaning of the two: a 5.17 {@code date} column is a SQL
 * Server {@code datetime} or an Oracle {@code DATE} on those vendors, and a {@code bool} is an
 * Oracle {@code NUMBER(1,0)}. Precision is the lower of the two sides; the copy truncates to it
 * explicitly (never rounds), and verification compares both sides at that precision.
 */
public final class ColumnPair {

    private final ColumnType source;
    private final ColumnType target;
    private final Family logical;
    private final int fractionDigits;
    private final boolean sqlServerGrid;
    private final boolean trimTrailingSpaces;
    private final boolean emptyStringIsNull;
    private final List<String> notes;

    private ColumnPair(
            ColumnType source,
            ColumnType target,
            Family logical,
            int fractionDigits,
            boolean sqlServerGrid,
            boolean trimTrailingSpaces,
            boolean emptyStringIsNull,
            List<String> notes) {
        this.source = source;
        this.target = target;
        this.logical = logical;
        this.fractionDigits = fractionDigits;
        this.sqlServerGrid = sqlServerGrid;
        this.trimTrailingSpaces = trimTrailingSpaces;
        this.emptyStringIsNull = emptyStringIsNull;
        this.notes = Collections.unmodifiableList(notes);
    }

    /**
     * Works out how values of {@code source} are copied into {@code target}.
     *
     * @param source the source column; null for a target column derived by a rule
     * @param valueFamily the family the source value has after a transform rule, or null
     * @throws IllegalArgumentException when no safe conversion exists
     */
    public static ColumnPair resolve(ColumnType source, ColumnType target, Family valueFamily) {
        Family from = valueFamily != null ? valueFamily : source.family();
        // A transform that produces OTHER values takes responsibility for the target type
        Family logical =
                valueFamily == Family.OTHER && target.family() == Family.OTHER
                        ? Family.OTHER
                        : logicalFamily(from, source, target);
        if (logical == null) {
            throw new IllegalArgumentException(
                    "cannot copy "
                            + (source == null ? from : source)
                            + " into "
                            + target
                            + (source == null
                                    ? ""
                                    : " (" + source.column() + " -> " + target.column() + ")")
                            + "; add a transform rule");
        }
        List<String> notes = new ArrayList<>();
        int digits = 0;
        boolean grid = false;
        if (logical == Family.TIME
                || logical == Family.TIMESTAMP
                || logical == Family.TIMESTAMP_TZ) {
            int sourceDigits =
                    source == null || valueFamily != null ? 9 : precisionOf(source, logical);
            int targetDigits = precisionOf(target, logical);
            digits = Math.min(sourceDigits, targetDigits);
            grid = target.sqlServerDatetime() || (source != null && source.sqlServerDatetime());
            if (targetDigits < sourceDigits && source != null) {
                notes.add(
                        "fractional seconds reduced from "
                                + sourceDigits
                                + " to "
                                + targetDigits
                                + " digits (truncated)");
            }
            if (target.sqlServerDatetime() && (source == null || !source.sqlServerDatetime())) {
                notes.add("rounded to SQL Server datetime's 1/300 s");
            }
        }
        if (source != null
                && logical == Family.STRING
                && source.column().size() > target.column().size()
                && target.column().size() > 0
                // Oracle's driver reports a CLOB as 4000 wide; it holds any length
                && !target.largeText()) {
            notes.add(
                    "target is narrower ("
                            + target.column().size()
                            + " < "
                            + source.column().size()
                            + "); longer values fail the copy");
        }
        if (logical == Family.STRING
                && target.emptyStringIsNull()
                && (source == null || !source.emptyStringIsNull())) {
            notes.add("empty strings become NULL (Oracle stores '' as NULL)");
        }
        if (logical == Family.BINARY
                && target.emptyStringIsNull()
                && (source == null || !source.emptyStringIsNull())) {
            notes.add("empty binary values become NULL (Oracle stores them as NULL)");
        }
        if (source != null && logical == Family.DOUBLE && source.family() != Family.DOUBLE) {
            notes.add("copied as a binary double: digits beyond its precision (about 15) are lost");
        }
        boolean trim = target.padded() || (source != null && source.padded());
        boolean emptyNull =
                target.emptyStringIsNull() || (source != null && source.emptyStringIsNull());
        return new ColumnPair(source, target, logical, digits, grid, trim, emptyNull, notes);
    }

    private static int precisionOf(ColumnType c, Family logical) {
        if (c.family() == Family.DATE) {
            return 0;
        }
        if (logical == Family.TIME && c.dateTimeStorage()) {
            return c.fractionDigits();
        }
        return c.fractionDigits();
    }

    static Family logicalFamily(Family from, ColumnType source, ColumnType target) {
        Family to = target.family();
        if (from == to) {
            if (from == Family.OTHER) {
                boolean sameType =
                        source != null
                                && source.vendor() == target.vendor()
                                && source.column().typeName().equals(target.column().typeName());
                return sameType ? Family.OTHER : null;
            }
            return from;
        }
        // A PostgreSQL oid column holds a large object; Hibernate 5 maps @Lob String there, so
        // it pairs with a text column (CLOB, LONGTEXT) as UTF-8 text
        if (from == Family.BINARY
                && to == Family.STRING
                && source != null
                && source.postgresLargeObject()) {
            return Family.STRING;
        }
        if (from == Family.STRING && to == Family.BINARY && target.postgresLargeObject()) {
            return Family.STRING;
        }
        if (from == Family.BOOLEAN && to == Family.INTEGER) {
            return Family.BOOLEAN;
        }
        if (from == Family.INTEGER && to == Family.BOOLEAN) {
            return Family.BOOLEAN;
        }
        if (isNumeric(from) && isNumeric(to)) {
            if (from == Family.DOUBLE || to == Family.DOUBLE) {
                return Family.DOUBLE;
            }
            return Family.DECIMAL;
        }
        // 5.17 maps date-only and time-only values to datetime on SQL Server and DATE on Oracle
        if (from == Family.DATE && to == Family.TIMESTAMP && target.dateTimeStorage()) {
            return Family.DATE;
        }
        if (from == Family.TIMESTAMP
                && to == Family.DATE
                && source != null
                && source.dateTimeStorage()) {
            return Family.DATE;
        }
        if (from == Family.TIME && to == Family.TIMESTAMP && target.dateTimeStorage()) {
            return Family.TIME;
        }
        if (from == Family.TIMESTAMP
                && to == Family.TIME
                && source != null
                && source.dateTimeStorage()) {
            return Family.TIME;
        }
        return null;
    }

    private static boolean isNumeric(Family f) {
        return f == Family.INTEGER || f == Family.DECIMAL || f == Family.DOUBLE;
    }

    public ColumnType source() {
        return source;
    }

    public ColumnType target() {
        return target;
    }

    public Family logical() {
        return logical;
    }

    public int fractionDigits() {
        return fractionDigits;
    }

    public boolean sqlServerGrid() {
        return sqlServerGrid;
    }

    public boolean trimTrailingSpaces() {
        return trimTrailingSpaces;
    }

    public boolean emptyStringIsNull() {
        return emptyStringIsNull;
    }

    /** Lossy or noteworthy conversions, for the plan report. */
    public List<String> notes() {
        return notes;
    }

    @Override
    public String toString() {
        return (source == null ? "derived" : source.toString())
                + " -> "
                + target
                + " as "
                + logical;
    }
}
