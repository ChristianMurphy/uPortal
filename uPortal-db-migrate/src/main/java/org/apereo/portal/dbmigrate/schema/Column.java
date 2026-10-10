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
package org.apereo.portal.dbmigrate.schema;

import java.sql.Types;
import java.util.Locale;

/** One column as the JDBC metadata describes it. */
public final class Column {

    private final String name;
    private final int jdbcType;
    private final String typeName;
    private final int size;
    private final Integer decimalDigits;
    private final boolean nullable;
    private final boolean hasDefault;
    private final boolean autoIncrement;
    private final int position;

    public Column(
            String name,
            int jdbcType,
            String typeName,
            int size,
            Integer decimalDigits,
            boolean nullable,
            boolean hasDefault,
            boolean autoIncrement,
            int position) {
        this.name = name;
        this.jdbcType = jdbcType;
        this.typeName = typeName == null ? "" : typeName;
        this.size = size;
        this.decimalDigits = decimalDigits;
        this.nullable = nullable;
        this.hasDefault = hasDefault;
        this.autoIncrement = autoIncrement;
        this.position = position;
    }

    /** A column for tests and rule-derived columns. */
    public static Column of(String name, int jdbcType, String typeName, int size, Integer digits) {
        return new Column(name, jdbcType, typeName, size, digits, true, false, false, 0);
    }

    public String name() {
        return name;
    }

    public String key() {
        return Names.key(name);
    }

    public int jdbcType() {
        return jdbcType;
    }

    /** The database's own type name, lower case. */
    public String typeName() {
        return typeName.toLowerCase(Locale.ROOT);
    }

    public int size() {
        return size;
    }

    public Integer decimalDigits() {
        return decimalDigits;
    }

    public boolean nullable() {
        return nullable;
    }

    public boolean hasDefault() {
        return hasDefault;
    }

    public boolean autoIncrement() {
        return autoIncrement;
    }

    public int position() {
        return position;
    }

    public boolean isIntegerType() {
        switch (jdbcType) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                return true;
            case Types.NUMERIC:
            case Types.DECIMAL:
                return decimalDigits != null && decimalDigits == 0 && size > 0;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return name
                + " "
                + typeName
                + "("
                + size
                + (decimalDigits == null ? "" : "," + decimalDigits)
                + ")"
                + (nullable ? "" : " not null");
    }
}
