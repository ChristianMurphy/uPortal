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
package org.apereo.portal.dbmigrate.copy;

import org.apereo.portal.dbmigrate.schema.Dialect;
import org.apereo.portal.dbmigrate.types.ColumnType;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * SQL for PostgreSQL large objects (MOD-I24): Hibernate 4.3 writes a {@code @Lob String} as a large
 * object and stores its OID, as digits, in a {@code text} column; {@code oid} columns hold the OID
 * directly.
 */
public final class PostgresLargeObjects {

    private PostgresLargeObjects() {}

    /** The column value as an {@code oid} expression. */
    public static String oidExpression(ColumnType c, Dialect d) {
        String col = d.quote(c.column().name());
        return c.postgresOidText() ? "CAST(" + col + " AS oid)" : col;
    }

    /** A condition true for rows whose reference is NULL or points to an existing object. */
    public static String resolvableCondition(ColumnType c, Dialect d) {
        return d.quote(c.column().name())
                + " IS NULL OR EXISTS (SELECT 1 FROM pg_largeobject_metadata m WHERE m.oid = "
                + oidExpression(c, d)
                + ")";
    }

    /** Rows whose column references a large object that no longer exists. */
    public static String countDanglingSql(String qualifiedTable, ColumnType c, Dialect d) {
        return "SELECT count(*) FROM "
                + qualifiedTable
                + " WHERE NOT ("
                + resolvableCondition(c, d)
                + ")";
    }

    /**
     * Rows of a plain {@code text} column whose value is the OID of an existing large object: the
     * column holds LOB references nobody declared, and copying it as text would lose the data.
     * Nested CASEs keep PostgreSQL from casting non-numeric text.
     */
    public static String countUndeclaredSql(String qualifiedTable, String column, Dialect d) {
        String col = d.quote(column);
        return "SELECT count(*) FROM "
                + qualifiedTable
                + " WHERE EXISTS (SELECT 1 FROM pg_largeobject_metadata m WHERE m.oid = CASE WHEN "
                + col
                + " ~ '^[0-9]{1,10}$' THEN CASE WHEN CAST("
                + col
                + " AS bigint) < 4294967296 THEN CAST(CAST("
                + col
                + " AS bigint) AS oid) END END)";
    }

    /** Unlinks the large objects a target column references, before its rows are deleted. */
    public static String unlinkSql(String qualifiedTable, ColumnType c, Dialect d) {
        return "SELECT lo_unlink(m.oid) FROM pg_largeobject_metadata m WHERE m.oid IN (SELECT "
                + oidExpression(c, d)
                + " FROM "
                + qualifiedTable
                + " WHERE "
                + d.quote(c.column().name())
                + " IS NOT NULL)";
    }

    public static long count(JdbcOperations jdbc, String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
