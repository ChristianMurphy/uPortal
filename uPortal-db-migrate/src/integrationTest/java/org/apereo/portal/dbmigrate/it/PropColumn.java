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
package org.apereo.portal.dbmigrate.it;

import static org.apereo.portal.dbmigrate.it.DbServer.Kind.HSQLDB;
import static org.apereo.portal.dbmigrate.it.DbServer.Kind.ORACLE;
import static org.apereo.portal.dbmigrate.it.DbServer.Kind.POSTGRES;
import static org.apereo.portal.dbmigrate.it.DbServer.Kind.SQLSERVER;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apereo.portal.dbmigrate.it.DbServer.Kind;

/**
 * The columns of the property tests' table {@code UP_PROP}: one per kind of value the copy tool
 * handles, spelled per vendor the way uPortal 5.17 (Hibernate 4.3) creates them where 5.17 has such
 * a column, so the tests copy what adopters have. Each column knows how to write a value into a
 * source database, read it back, and how precise the vendor keeps it.
 */
public enum PropColumn {
    ID,
    I16,
    I32,
    I64,
    AMOUNT,
    RATIO,
    FLAG,
    CODE,
    S1,
    S255,
    S4000,
    BODY,
    PAYLOAD,
    /** PostgreSQL: a large object whose OID is kept in a text column (5.17 @Lob String). */
    LOB_TEXT,
    /** PostgreSQL: an {@code oid} column (Hibernate 5 @Lob byte[], the portlets). */
    LOB_DATA,
    DAY_VALUE,
    AT_TIME,
    TS0,
    TS3,
    TS6,
    /** The timestamp column 5.17 creates: DATETIME on MySQL/MariaDB, datetime on SQL Server. */
    TS517,
    TSTZ,
    GUID;

    /** The columns a copy from {@code source} to {@code target} can carry. */
    public static List<PropColumn> forPair(Kind source, Kind target) {
        List<PropColumn> out = new ArrayList<>();
        for (PropColumn c : values()) {
            if (c.ddlType(source) != null && c.ddlType(target) != null) {
                // uuid is a PostgreSQL type the tool copies between PostgreSQL databases only
                if (c == GUID && (source != POSTGRES || target != POSTGRES)) {
                    continue;
                }
                out.add(c);
            }
        }
        return out;
    }

    /** The column's type on a vendor, or null when the vendor has no such type. */
    public String ddlType(Kind k) {
        boolean my = k.isMySqlFamily();
        switch (this) {
            case ID:
            case I64:
                return k == ORACLE ? "NUMBER(19)" : "BIGINT";
            case I16:
                return k == ORACLE ? "NUMBER(5)" : "SMALLINT";
            case I32:
                return k == ORACLE ? "NUMBER(10)" : "INTEGER";
            case AMOUNT:
                return k == ORACLE ? "NUMBER(38,10)" : "DECIMAL(38,10)";
            case RATIO:
                return k == SQLSERVER ? "FLOAT(53)" : my ? "DOUBLE" : "DOUBLE PRECISION";
            case FLAG:
                return k == ORACLE
                        ? "NUMBER(1)"
                        : my ? "BIT(1)" : k == SQLSERVER ? "BIT" : "BOOLEAN";
            case CODE:
                return string(k, "CHAR", 10);
            case S1:
                return string(k, "VARCHAR", 1);
            case S255:
                return string(k, "VARCHAR", 255);
            case S4000:
                return string(k, "VARCHAR", 4000);
            case BODY:
            case LOB_TEXT:
                return k == POSTGRES
                        ? "TEXT"
                        : my
                                ? "LONGTEXT"
                                : k == SQLSERVER
                                        ? "NVARCHAR(MAX)"
                                        : k == ORACLE ? "CLOB" : "CLOB(64M)";
            case PAYLOAD:
                return k == POSTGRES
                        ? "BYTEA"
                        : my
                                ? "LONGBLOB"
                                : k == SQLSERVER
                                        ? "VARBINARY(MAX)"
                                        : k == ORACLE ? "BLOB" : "BLOB(64M)";
            case LOB_DATA:
                return k == POSTGRES ? "OID" : PAYLOAD.ddlType(k);
            case DAY_VALUE:
                return "DATE";
            case AT_TIME:
                // 5.17 on Oracle keeps time-only values in a DATE
                return k == ORACLE ? "DATE" : "TIME";
            case TS0:
            case TS3:
            case TS6:
                {
                    int p = Integer.parseInt(name().substring(2));
                    return my
                            ? "DATETIME(" + p + ")"
                            : k == SQLSERVER ? "DATETIME2(" + p + ")" : "TIMESTAMP(" + p + ")";
                }
            case TS517:
                return my ? "DATETIME" : k == SQLSERVER ? "DATETIME" : "TIMESTAMP";
            case TSTZ:
                switch (k) {
                    case POSTGRES:
                        return "TIMESTAMPTZ";
                    case SQLSERVER:
                        return "DATETIMEOFFSET(6)";
                    case ORACLE:
                    case HSQLDB:
                        return "TIMESTAMP(6) WITH TIME ZONE";
                    default:
                        return null;
                }
            case GUID:
                return k == POSTGRES ? "UUID" : null;
            default:
                throw new IllegalStateException(name());
        }
    }

    private static String string(Kind k, String type, int length) {
        if (k == SQLSERVER) {
            return "N" + type + "(" + length + ")";
        }
        if (k == ORACLE) {
            return (type.equals("VARCHAR") ? "VARCHAR2" : type) + "(" + length + " CHAR)";
        }
        return type + "(" + length + ")";
    }

    /** The declared length of a string column, in UTF-16 units; 0 otherwise. */
    public int length() {
        switch (this) {
            case CODE:
                return 10;
            case S1:
                return 1;
            case S255:
                return 255;
            case S4000:
                return 4000;
            default:
                return 0;
        }
    }

    public boolean isString() {
        return length() > 0 || this == BODY || this == LOB_TEXT;
    }

    public boolean isBinary() {
        return this == PAYLOAD || this == LOB_DATA;
    }

    public boolean isTimestamp() {
        return this == TS0 || this == TS3 || this == TS6 || this == TS517;
    }

    /** Fractional-second digits the vendor keeps in this column (9: not a time column). */
    public int precision(Kind k) {
        switch (this) {
            case AT_TIME:
                return k == POSTGRES ? 6 : k == SQLSERVER ? 7 : 0;
            case TS0:
                return 0;
            case TS3:
                return 3;
            case TS6:
            case TSTZ:
                return 6;
            case TS517:
                // SQL Server datetime: 1/300 s, see sqlServerDatetime
                return k.isMySqlFamily() ? 0 : k == SQLSERVER ? 3 : 6;
            default:
                return 9;
        }
    }

    /** SQL Server's {@code datetime}, which keeps 1/300 s ticks. */
    public boolean sqlServerDatetime(Kind k) {
        return this == TS517 && k == SQLSERVER;
    }

    /** A rule file line the column needs, or null. */
    public String rule() {
        return this == LOB_TEXT ? "lob-oid UP_PROP.LOB_TEXT" : null;
    }

    /** The SQL value expression for a write: a placeholder, or NULL for a null value. */
    public String placeholderFor(Kind k, Object value) {
        if (value == null) {
            return "NULL";
        }
        if (k == POSTGRES && this == LOB_TEXT) {
            return "CAST(lo_from_bytea(0, ?) AS text)";
        }
        if (k == POSTGRES && this == LOB_DATA) {
            return "lo_from_bytea(0, ?)";
        }
        return "?";
    }

    /** Binds a non-null value written to vendor {@code k}. */
    public void bindValue(PreparedStatement ps, int i, Kind k, Object v) throws SQLException {
        switch (this) {
            case ID:
            case I16:
            case I32:
            case I64:
                ps.setLong(i, ((Number) v).longValue());
                break;
            case AMOUNT:
                ps.setBigDecimal(i, (BigDecimal) v);
                break;
            case RATIO:
                ps.setDouble(i, (Double) v);
                break;
            case FLAG:
                if (k == ORACLE) {
                    ps.setInt(i, ((Boolean) v) ? 1 : 0);
                } else {
                    ps.setBoolean(i, (Boolean) v);
                }
                break;
            case LOB_TEXT:
                if (k == POSTGRES) {
                    ps.setBytes(i, ((String) v).getBytes(StandardCharsets.UTF_8));
                } else {
                    ps.setString(i, (String) v);
                }
                break;
            case PAYLOAD:
            case LOB_DATA:
                ps.setBytes(i, (byte[]) v);
                break;
            case AT_TIME:
                if (k == ORACLE) {
                    ps.setObject(i, LocalDateTime.of(LocalDate.of(1970, 1, 1), (LocalTime) v));
                } else {
                    ps.setObject(i, v);
                }
                break;
            default:
                if (isString()) {
                    ps.setString(i, (String) v);
                } else if (k == HSQLDB
                        && v instanceof LocalDate
                        && ((LocalDate) v).isBefore(GREGORIAN)) {
                    // HSQLDB 2.7's setObject(LocalDate) stores dates before 1582-10-15 shifted to
                    // the
                    // Julian calendar (getObject, literals and setDate do not); 5.17 writes setDate
                    ps.setDate(i, java.sql.Date.valueOf((LocalDate) v));
                } else if (k == HSQLDB
                        && v instanceof LocalDateTime
                        && ((LocalDateTime) v).toLocalDate().isBefore(GREGORIAN)) {
                    ps.setTimestamp(i, java.sql.Timestamp.valueOf((LocalDateTime) v));
                } else {
                    ps.setObject(i, v);
                }
        }
    }

    /** The first day of the Gregorian calendar. */
    private static final LocalDate GREGORIAN = LocalDate.of(1582, 10, 15);

    /** The select-list expression that reads the value (PostgreSQL large objects resolved). */
    public String selectExpressionFor(Kind k) {
        if (k == POSTGRES && this == LOB_TEXT) {
            return "convert_from(lo_get(CAST(LOB_TEXT AS oid)), 'UTF8')";
        }
        if (k == POSTGRES && this == LOB_DATA) {
            return "lo_get(LOB_DATA)";
        }
        return name();
    }

    /**
     * Reads the value at {@code i}: Long, BigDecimal, Double, Boolean, String, byte[], java.time.
     */
    public Object readValue(ResultSet rs, int i, Kind k) throws SQLException {
        Object v;
        switch (this) {
            case ID:
            case I16:
            case I32:
            case I64:
                v = rs.getLong(i);
                break;
            case AMOUNT:
                return rs.getBigDecimal(i);
            case RATIO:
                v = rs.getDouble(i);
                break;
            case FLAG:
                v = rs.getBoolean(i);
                break;
            case PAYLOAD:
            case LOB_DATA:
                return rs.getBytes(i);
            case DAY_VALUE:
                return rs.getObject(i, LocalDate.class);
            case AT_TIME:
                if (k == ORACLE) {
                    LocalDateTime t = rs.getObject(i, LocalDateTime.class);
                    return t == null ? null : t.toLocalTime();
                }
                return rs.getObject(i, LocalTime.class);
            case TS0:
            case TS3:
            case TS6:
            case TS517:
                return rs.getObject(i, LocalDateTime.class);
            case TSTZ:
                return rs.getObject(i, OffsetDateTime.class);
            case GUID:
                return rs.getObject(i, UUID.class);
            default:
                return rs.getString(i);
        }
        return rs.wasNull() ? null : v;
    }

    /**
     * A server-side text rendering of a date or timestamp column, independent of the JDBC driver's
     * java.time conversions; null for other columns.
     */
    public String textExpression(Kind k) {
        if (this != DAY_VALUE && this != TS6 && this != TS517) {
            return null;
        }
        switch (k) {
            case POSTGRES:
            case HSQLDB:
                return "CAST(" + name() + " AS VARCHAR(40))";
            case MARIADB:
            case MYSQL:
                return "CAST(" + name() + " AS CHAR)";
            case SQLSERVER:
                return "CONVERT(VARCHAR(40), " + name() + ", 121)";
            case ORACLE:
                return this == DAY_VALUE
                        ? "TO_CHAR(" + name() + ", 'YYYY-MM-DD')"
                        : "TO_CHAR(" + name() + ", 'YYYY-MM-DD HH24:MI:SS.FF6')";
            default:
                return null;
        }
    }

    /** Columns that hold text a vendor may not accept in every form (NUL, unpaired, ...). */
    public static Set<PropColumn> strings() {
        Set<PropColumn> s = EnumSet.noneOf(PropColumn.class);
        for (PropColumn c : values()) {
            if (c.isString()) {
                s.add(c);
            }
        }
        return s;
    }
}
