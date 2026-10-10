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

import java.sql.Types;
import org.apereo.portal.dbmigrate.Vendor;
import org.apereo.portal.dbmigrate.schema.Column;

/**
 * What a column can hold, vendor-neutrally: its family plus the storage details that change how
 * values are read, written and compared (CHAR padding, LOB streams, PostgreSQL large objects,
 * fractional-second precision, SQL Server's 1/300 s {@code datetime} grid, Oracle's DATE).
 */
public final class ColumnType {

    public enum Family {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        DOUBLE,
        STRING,
        BINARY,
        DATE,
        TIME,
        TIMESTAMP,
        TIMESTAMP_TZ,
        /** Anything else; copied as-is with getObject/setObject between equal types only. */
        OTHER
    }

    private final Vendor vendor;
    private final Column column;
    private final Family family;
    private final boolean padded;
    private final boolean largeText;
    private final boolean largeBinary;
    private final boolean postgresLargeObject;
    private final int fractionDigits;
    private final boolean sqlServerDatetime;
    private final boolean dateTimeStorage;
    private final boolean booleanLike;

    private ColumnType(Vendor vendor, Column column, Builder b) {
        this.vendor = vendor;
        this.column = column;
        this.family = b.family;
        this.padded = b.padded;
        this.largeText = b.largeText;
        this.largeBinary = b.largeBinary;
        this.postgresLargeObject = b.postgresLargeObject;
        this.fractionDigits = b.fractionDigits;
        this.sqlServerDatetime = b.sqlServerDatetime;
        this.dateTimeStorage = b.dateTimeStorage;
        this.booleanLike = b.booleanLike;
    }

    /**
     * Classifies a column.
     *
     * @param oidText the column is a PostgreSQL {@code text} column that holds large-object OIDs
     *     (MOD-I24); declared by a {@code lob-oid} rule because the type alone cannot tell
     */
    public static ColumnType classify(Vendor vendor, Column c, boolean oidText) {
        Builder b = new Builder();
        String type = c.typeName();
        int jdbc = c.jdbcType();
        if (vendor == Vendor.POSTGRESQL && "oid".equals(type)) {
            b.family = Family.BINARY;
            b.postgresLargeObject = true;
        } else if (jdbc == Types.BOOLEAN
                || (jdbc == Types.BIT && c.size() <= 1)
                || "bool".equals(type)
                || "boolean".equals(type)) {
            b.family = Family.BOOLEAN;
        } else if (vendor == Vendor.ORACLE && "date".equals(type)) {
            // Oracle DATE has a time part to the second
            b.family = Family.TIMESTAMP;
            b.fractionDigits = 0;
            b.dateTimeStorage = true;
        } else if (c.isIntegerType()) {
            b.family = Family.INTEGER;
            b.booleanLike =
                    c.size() == 1
                            && (jdbc == Types.NUMERIC
                                    || jdbc == Types.DECIMAL
                                    || jdbc == Types.TINYINT);
        } else {
            switch (jdbc) {
                case Types.NUMERIC:
                case Types.DECIMAL:
                    b.family = Family.DECIMAL;
                    break;
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                case 100: // Oracle BINARY_FLOAT
                case 101: // Oracle BINARY_DOUBLE
                    b.family = Family.DOUBLE;
                    break;
                case Types.CHAR:
                case Types.NCHAR:
                    b.family = Family.STRING;
                    b.padded = true;
                    break;
                case Types.VARCHAR:
                case Types.NVARCHAR:
                case Types.LONGVARCHAR:
                case Types.LONGNVARCHAR:
                    b.family = Family.STRING;
                    break;
                case Types.CLOB:
                case Types.NCLOB:
                    b.family = Family.STRING;
                    b.largeText = true;
                    break;
                case Types.BINARY:
                case Types.VARBINARY:
                    b.family = Family.BINARY;
                    break;
                case Types.LONGVARBINARY:
                case Types.BLOB:
                    b.family = Family.BINARY;
                    b.largeBinary = true;
                    break;
                case Types.DATE:
                    b.family = Family.DATE;
                    break;
                case Types.TIME:
                    b.family = Family.TIME;
                    b.fractionDigits = fractionDigits(vendor, c, 0);
                    break;
                case Types.TIME_WITH_TIMEZONE:
                    b.family = Family.OTHER;
                    break;
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    b.family = Family.TIMESTAMP_TZ;
                    b.fractionDigits = fractionDigits(vendor, c, 6);
                    break;
                case Types.TIMESTAMP:
                    if (type.contains("time zone") || "timestamptz".equals(type)) {
                        b.family = Family.TIMESTAMP_TZ;
                    } else {
                        b.family = Family.TIMESTAMP;
                    }
                    if (vendor == Vendor.SQLSERVER && "datetime".equals(type)) {
                        b.sqlServerDatetime = true;
                        b.dateTimeStorage = true;
                        b.fractionDigits = 3;
                    } else if (vendor == Vendor.SQLSERVER && "smalldatetime".equals(type)) {
                        b.dateTimeStorage = true;
                        b.fractionDigits = 0;
                    } else {
                        b.fractionDigits = fractionDigits(vendor, c, 6);
                    }
                    break;
                default:
                    // SQL Server datetimeoffset; Oracle reports its own type codes (-101, -102)
                    if ("datetimeoffset".equals(type) || type.contains("time zone")) {
                        b.family = Family.TIMESTAMP_TZ;
                        b.fractionDigits = fractionDigits(vendor, c, 7);
                    } else {
                        b.family = Family.OTHER;
                    }
            }
        }
        if (oidText) {
            if (b.family != Family.STRING || vendor != Vendor.POSTGRESQL) {
                throw new IllegalArgumentException(
                        "lob-oid applies to PostgreSQL text columns only, not " + c);
            }
            b.postgresLargeObject = true;
        }
        return new ColumnType(vendor, c, b);
    }

    /**
     * Fractional-second digits of a TIME or TIMESTAMP column. Most drivers report them as
     * DECIMAL_DIGITS (for MySQL and MariaDB the schema reader takes them from information_schema);
     * without them, the display size tells: JDBC's length of the value as text, 19 or 8 characters
     * for seconds, plus a point and the fraction digits. HSQLDB reports no DECIMAL_DIGITS for
     * TIMESTAMP(0).
     */
    static int fractionDigits(Vendor vendor, Column c, int fallback) {
        Integer d = c.decimalDigits();
        int max = vendor.isMySqlFamily() ? 6 : 9;
        if (d == null) {
            if (c.jdbcType() == Types.TIMESTAMP && c.size() >= 19) {
                return Math.min(max, Math.max(0, c.size() - 20));
            }
            if (c.jdbcType() == Types.TIME && c.size() >= 8) {
                return Math.min(max, Math.max(0, c.size() - 9));
            }
        }
        if (d != null && d >= 0 && d <= 9) {
            return d;
        }
        return fallback;
    }

    public Vendor vendor() {
        return vendor;
    }

    public Column column() {
        return column;
    }

    public Family family() {
        return family;
    }

    /** CHAR(n): the database pads values with spaces. */
    public boolean padded() {
        return padded;
    }

    /** CLOB / NCLOB: written as a character stream. */
    public boolean largeText() {
        return largeText;
    }

    /** BLOB / LONGVARBINARY (Oracle LONG RAW): written as a byte stream. */
    public boolean largeBinary() {
        return largeBinary;
    }

    /** The value lives in a PostgreSQL large object; the column holds its OID. */
    public boolean postgresLargeObject() {
        return postgresLargeObject;
    }

    /** The column is {@code text} holding an OID, as Hibernate 4.3 writes {@code @Lob String}. */
    public boolean postgresOidText() {
        return postgresLargeObject && family == Family.STRING;
    }

    public int fractionDigits() {
        return fractionDigits;
    }

    /** SQL Server {@code datetime}: 1/300 s ticks, read back as .000/.003/.007 ms. */
    public boolean sqlServerDatetime() {
        return sqlServerDatetime;
    }

    /**
     * A date-time column that 5.17 also uses for date-only or time-only values on vendors where the
     * dialect has no separate type (SQL Server {@code datetime}, Oracle {@code DATE}).
     */
    public boolean dateTimeStorage() {
        return dateTimeStorage;
    }

    /** NUMBER(1,0) / TINYINT(1): may be a boolean on the other side. */
    public boolean booleanLike() {
        return booleanLike;
    }

    /** Oracle stores '' as NULL. */
    public boolean emptyStringIsNull() {
        return vendor == Vendor.ORACLE;
    }

    @Override
    public String toString() {
        return family
                + (fractionDigits > 0 || family == Family.TIMESTAMP
                        ? "(" + fractionDigits + ")"
                        : "")
                + (postgresLargeObject ? "[pg-lob]" : "")
                + (sqlServerDatetime ? "[1/300s]" : "")
                + (padded ? "[char]" : "");
    }

    private static final class Builder {
        Family family = Family.OTHER;
        boolean padded;
        boolean largeText;
        boolean largeBinary;
        boolean postgresLargeObject;
        int fractionDigits;
        boolean sqlServerDatetime;
        boolean dateTimeStorage;
        boolean booleanLike;
    }
}
