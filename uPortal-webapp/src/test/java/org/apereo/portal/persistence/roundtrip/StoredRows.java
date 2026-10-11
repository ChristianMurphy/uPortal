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
package org.apereo.portal.persistence.roundtrip;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apereo.portal.test.golden.DatabaseVendor;

/**
 * The rows uPortal's tables hold, read with plain JDBC and rendered vendor-neutrally: what a later
 * Hibernate, Jadira or time-library version must keep reading (stored values outlive the code that
 * wrote them: P2 found map keys with the nullSafeString '_' prefix this way).
 *
 * <ul>
 *   <li>Identifiers and column names are upper-cased; tables are those named {@code UP_*}, minus
 *       the one-column {@code next_val} tables that emulate sequences on MySQL and SQL Server.
 *   <li>Each column is read by its type tag: {@code s} string, {@code lob} character LOB, {@code
 *       bin} binary, {@code b} boolean, {@code i} integer, {@code n} decimal, {@code f} floating
 *       point, {@code d} date, {@code t} time, {@code ts} timestamp (the stored wall-clock time).
 *       On HSQLDB the tags come from the JDBC types; elsewhere from the golden, so that a {@code
 *       NUMBER(1)} on Oracle is still read as the boolean it maps.
 *   <li>Generated keys are replaced by symbols: a numeric primary key by {@code #n}, its rank in
 *       the table; a UUID key by {@code ~n}, the rank of its row's other values; a foreign key by
 *       the symbol of the row it references.
 * </ul>
 */
final class StoredRows {
    static final String NULL = "null";
    private static final Pattern UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final int SHORT_STRING = 120;

    /** table -> column -> tag, sorted */
    private final Map<String, Map<String, String>> tags = new TreeMap<>();

    /** table -> primary key columns */
    private final Map<String, List<String>> primaryKeys = new HashMap<>();

    /** table -> column -> referenced "TABLE.COLUMN" */
    private final Map<String, Map<String, String>> foreignKeys = new HashMap<>();

    /** table -> raw rows (column -> raw value) */
    private final Map<String, List<Map<String, Object>>> rows = new TreeMap<>();

    /** LOB columns that hold a large-object OID instead of the text (PostgreSQL) */
    private final Set<String> oidLobColumns = new TreeSet<>();

    private StoredRows() {}

    /**
     * Reads every {@code UP_*} table.
     *
     * @param expectedTags table -> column -> tag from the golden; empty when recording
     */
    static StoredRows read(
            DataSource dataSource,
            DatabaseVendor vendor,
            Map<String, Map<String, String>> expectedTags)
            throws SQLException {
        final StoredRows stored = new StoredRows();
        try (Connection connection = dataSource.getConnection()) {
            // PostgreSQL reads large objects only inside a transaction
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                stored.readTables(connection, vendor, expectedTags);
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
        }
        return stored;
    }

    private void readTables(
            Connection connection,
            DatabaseVendor vendor,
            Map<String, Map<String, String>> expectedTags)
            throws SQLException {
        {
            final DatabaseMetaData metaData = connection.getMetaData();
            final String catalog = connection.getCatalog();
            final String schema =
                    vendor == DatabaseVendor.MYSQL || vendor == DatabaseVendor.MARIADB
                            ? null
                            : connection.getSchema();
            final Map<String, String> tableNames = new TreeMap<>();
            try (ResultSet tables =
                    metaData.getTables(catalog, schema, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    final String name = tables.getString("TABLE_NAME");
                    if (name.toUpperCase(Locale.ROOT).startsWith("UP_")) {
                        tableNames.put(name.toUpperCase(Locale.ROOT), name);
                    }
                }
            }
            for (final Map.Entry<String, String> table : tableNames.entrySet()) {
                readTable(
                        connection,
                        metaData,
                        catalog,
                        schema,
                        vendor,
                        table.getKey(),
                        table.getValue(),
                        expectedTags.getOrDefault(table.getKey(), Collections.emptyMap()));
            }
        }
    }

    private void readTable(
            Connection connection,
            DatabaseMetaData metaData,
            String catalog,
            String schema,
            DatabaseVendor vendor,
            String table,
            String jdbcName,
            Map<String, String> expectedTags)
            throws SQLException {
        final Map<String, String> columnTags = new TreeMap<>();
        final Map<String, String> jdbcNames = new LinkedHashMap<>();
        try (ResultSet columns = metaData.getColumns(catalog, schema, jdbcName, "%")) {
            while (columns.next()) {
                final String name = columns.getString("COLUMN_NAME").toUpperCase(Locale.ROOT);
                jdbcNames.put(name, columns.getString("COLUMN_NAME"));
                final String tag = expectedTags.get(name);
                columnTags.put(name, tag != null ? tag : tag(columns.getInt("DATA_TYPE")));
            }
        }
        if (columnTags.keySet().equals(Collections.singleton("NEXT_VAL"))) {
            // A table that emulates a sequence (MySQL, SQL Server)
            return;
        }
        final List<String> keys = new ArrayList<>();
        try (ResultSet primaryKey = metaData.getPrimaryKeys(catalog, schema, jdbcName)) {
            while (primaryKey.next()) {
                keys.add(primaryKey.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }
        Collections.sort(keys);
        final Map<String, String> references = new TreeMap<>();
        try (ResultSet imported = metaData.getImportedKeys(catalog, schema, jdbcName)) {
            while (imported.next()) {
                references.put(
                        imported.getString("FKCOLUMN_NAME").toUpperCase(Locale.ROOT),
                        imported.getString("PKTABLE_NAME").toUpperCase(Locale.ROOT)
                                + "."
                                + imported.getString("PKCOLUMN_NAME").toUpperCase(Locale.ROOT));
            }
        }
        final List<Map<String, Object>> tableRows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT * FROM " + quote(vendor, jdbcName))) {
            while (resultSet.next()) {
                final Map<String, Object> row = new TreeMap<>();
                for (final Map.Entry<String, String> column : columnTags.entrySet()) {
                    final String name = column.getKey();
                    row.put(
                            name,
                            value(
                                    resultSet,
                                    jdbcNames.get(name),
                                    column.getValue(),
                                    vendor,
                                    table + "." + name));
                }
                tableRows.add(row);
            }
        }
        tags.put(table, columnTags);
        primaryKeys.put(table, keys);
        foreignKeys.put(table, references);
        rows.put(table, tableRows);
    }

    private static String quote(DatabaseVendor vendor, String name) {
        switch (vendor) {
            case MYSQL:
            case MARIADB:
                return '`' + name + '`';
            default:
                return '"' + name + '"';
        }
    }

    private Object value(
            ResultSet resultSet, String column, String tag, DatabaseVendor vendor, String path)
            throws SQLException {
        switch (tag) {
            case "s":
                return resultSet.getString(column);
            case "lob":
                {
                    if (vendor == DatabaseVendor.POSTGRESQL) {
                        final String raw = resultSet.getString(column);
                        if (raw == null || !DIGITS.matcher(raw).matches()) {
                            return raw;
                        }
                        // Hibernate binds @Lob strings as large objects: the column holds the OID
                        oidLobColumns.add(path);
                    }
                    final Clob clob = resultSet.getClob(column);
                    return clob == null ? null : read(clob);
                }
            case "bin":
                {
                    final byte[] bytes = resultSet.getBytes(column);
                    return bytes == null
                            ? null
                            : "bytes(len="
                                    + bytes.length
                                    + ", sha256="
                                    + EntityGraphComparator.sha256(
                                                    new String(
                                                            bytes,
                                                            java.nio.charset.StandardCharsets
                                                                    .ISO_8859_1))
                                            .substring(0, 12)
                                    + ")";
                }
            case "b":
                {
                    final boolean value = resultSet.getBoolean(column);
                    return resultSet.wasNull() ? null : value;
                }
            case "i":
                {
                    final BigDecimal value = resultSet.getBigDecimal(column);
                    return value == null ? null : value.toBigIntegerExact();
                }
            case "n":
                {
                    final BigDecimal value = resultSet.getBigDecimal(column);
                    return value == null ? null : value.stripTrailingZeros().toPlainString();
                }
            case "f":
                {
                    final double value = resultSet.getDouble(column);
                    return resultSet.wasNull() ? null : value;
                }
            case "d":
            case "t":
            case "ts":
                {
                    final LocalDateTime value = localDateTime(resultSet, column);
                    if (value == null) {
                        return null;
                    }
                    if ("d".equals(tag)) {
                        return value.toLocalDate();
                    }
                    if ("t".equals(tag)) {
                        return value.toLocalTime();
                    }
                    return value;
                }
            default:
                throw new IllegalStateException(path + ": unknown tag " + tag);
        }
    }

    /** The stored wall-clock time, without converting through the JVM zone where possible */
    private static LocalDateTime localDateTime(ResultSet resultSet, String column)
            throws SQLException {
        try {
            return resultSet.getObject(column, LocalDateTime.class);
        } catch (SQLException | RuntimeException e) {
            final Timestamp timestamp = resultSet.getTimestamp(column);
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }

    private static String read(Clob clob) throws SQLException {
        try (Reader reader = clob.getCharacterStream()) {
            final StringBuilder text = new StringBuilder();
            final char[] buffer = new char[8192];
            for (int n = reader.read(buffer); n >= 0; n = reader.read(buffer)) {
                text.append(buffer, 0, n);
            }
            return text.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String tag(int jdbcType) {
        switch (jdbcType) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
                return "s";
            case Types.CLOB:
            case Types.NCLOB:
                return "lob";
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return "bin";
            case Types.BIT:
            case Types.BOOLEAN:
                return "b";
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                return "i";
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "n";
            case Types.REAL:
            case Types.FLOAT:
            case Types.DOUBLE:
                return "f";
            case Types.DATE:
                return "d";
            case Types.TIME:
                return "t";
            case Types.TIMESTAMP:
                return "ts";
            default:
                return "s";
        }
    }

    Set<String> oidLobColumns() {
        return oidLobColumns;
    }

    /** The rows of {@code after} that are not in {@code before}, with the tables they are in */
    StoredRows minus(StoredRows before) {
        final StoredRows result = new StoredRows();
        result.tags.putAll(tags);
        result.primaryKeys.putAll(primaryKeys);
        result.foreignKeys.putAll(foreignKeys);
        result.oidLobColumns.addAll(oidLobColumns);
        for (final Map.Entry<String, List<Map<String, Object>>> table : rows.entrySet()) {
            final List<Map<String, Object>> remaining = new ArrayList<>(table.getValue());
            for (final Map<String, Object> row :
                    before.rows.getOrDefault(table.getKey(), Collections.emptyList())) {
                remaining.remove(row);
            }
            if (!remaining.isEmpty()) {
                result.rows.put(table.getKey(), remaining);
            }
        }
        result.tags.keySet().retainAll(result.rows.keySet());
        return result;
    }

    /** "table" lines (column tags) and "row" lines, sorted */
    List<String> render() {
        final Map<String, Map<Object, String>> symbols = symbols();
        final List<String> lines = new ArrayList<>();
        for (final Map.Entry<String, List<Map<String, Object>>> table : rows.entrySet()) {
            final String name = table.getKey();
            final StringBuilder header = new StringBuilder("table ").append(name).append(':');
            for (final Map.Entry<String, String> column : tags.get(name).entrySet()) {
                header.append(' ').append(column.getKey()).append('=').append(column.getValue());
                if (primaryKeys.get(name).contains(column.getKey())) {
                    header.append("(pk)");
                }
                final String reference = foreignKeys.get(name).get(column.getKey());
                if (reference != null) {
                    header.append("(fk ").append(reference).append(')');
                }
            }
            lines.add(header.toString());
            final List<String> rendered = new ArrayList<>();
            for (final Map<String, Object> row : table.getValue()) {
                rendered.add("row " + name + ": " + renderRow(name, row, symbols, true));
            }
            Collections.sort(rendered);
            lines.addAll(rendered);
        }
        return lines;
    }

    private String renderRow(
            String table,
            Map<String, Object> row,
            Map<String, Map<Object, String>> symbols,
            boolean withKey) {
        final StringBuilder line = new StringBuilder();
        for (final Map.Entry<String, Object> cell : row.entrySet()) {
            final String column = cell.getKey();
            final String reference = foreignKeys.get(table).get(column);
            final boolean key = primaryKeys.get(table).contains(column);
            if (!withKey && key && reference == null) {
                continue;
            }
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(column).append('=');
            final Object value = cell.getValue();
            if (value == null) {
                line.append(NULL);
            } else if (reference != null) {
                final String referencedTable = reference.substring(0, reference.indexOf('.'));
                final String symbol =
                        symbols.getOrDefault(referencedTable, Collections.emptyMap()).get(value);
                line.append(symbol != null ? symbol : "ext:" + renderValue(value));
            } else if (key
                    && symbols.getOrDefault(table, Collections.emptyMap()).containsKey(value)) {
                line.append(symbols.get(table).get(value));
            } else {
                line.append(renderValue(value));
            }
        }
        return line.toString();
    }

    /** table -> raw key value -> symbol, for single-column generated keys */
    private Map<String, Map<Object, String>> symbols() {
        final Map<String, Map<Object, String>> symbols = new HashMap<>();
        final List<String> uuidTables = new ArrayList<>();
        for (final Map.Entry<String, List<Map<String, Object>>> table : rows.entrySet()) {
            final List<String> keys = primaryKeys.get(table.getKey());
            if (keys.size() != 1 || foreignKeys.get(table.getKey()).containsKey(keys.get(0))) {
                continue;
            }
            final String key = keys.get(0);
            final List<Object> values = new ArrayList<>();
            boolean numeric = true;
            boolean uuid = true;
            for (final Map<String, Object> row : table.getValue()) {
                final Object value = row.get(key);
                values.add(value);
                numeric &= value instanceof java.math.BigInteger;
                uuid &= value instanceof String && UUID.matcher((String) value).matches();
            }
            if (numeric) {
                final List<java.math.BigInteger> sorted = new ArrayList<>();
                for (final Object value : values) {
                    sorted.add((java.math.BigInteger) value);
                }
                Collections.sort(sorted);
                final Map<Object, String> tableSymbols = new HashMap<>();
                for (int i = 0; i < sorted.size(); i++) {
                    tableSymbols.put(sorted.get(i), "#" + (i + 1));
                }
                symbols.put(table.getKey(), tableSymbols);
            } else if (uuid) {
                uuidTables.add(table.getKey());
            }
        }
        // Random UUID keys: rank rows by their other values, once the tables they reference have
        // symbols
        for (int pass = 0; pass <= uuidTables.size(); pass++) {
            for (final String table : new ArrayList<>(uuidTables)) {
                boolean ready = true;
                for (final String reference : foreignKeys.get(table).values()) {
                    final String referenced = reference.substring(0, reference.indexOf('.'));
                    ready &= !uuidTables.contains(referenced) || referenced.equals(table);
                }
                if (!ready && pass < uuidTables.size()) {
                    continue;
                }
                final String key = primaryKeys.get(table).get(0);
                final TreeMap<String, Object> byContent = new TreeMap<>();
                for (final Map<String, Object> row : rows.get(table)) {
                    byContent.put(
                            renderRow(table, row, symbols, false) + "\u0000" + row.get(key),
                            row.get(key));
                }
                final Map<Object, String> tableSymbols = new HashMap<>();
                int rank = 1;
                for (final Object value : byContent.values()) {
                    tableSymbols.put(value, "~" + rank++);
                }
                symbols.put(table, tableSymbols);
                uuidTables.remove(table);
            }
        }
        return symbols;
    }

    static String renderValue(Object value) {
        if (value instanceof String) {
            final String string = (String) value;
            if (string.length() <= SHORT_STRING) {
                return '"' + EntityGraphComparator.escape(string) + '"';
            }
            return '"'
                    + EntityGraphComparator.escape(string.substring(0, 16))
                    + "..\"(len="
                    + string.length()
                    + ", sha256="
                    + EntityGraphComparator.sha256(string).substring(0, 12)
                    + ")";
        }
        return String.valueOf(value);
    }

    /** Parses the "table" lines of a golden into table -> column -> tag */
    static Map<String, Map<String, String>> tags(List<String> golden) {
        final Map<String, Map<String, String>> result = new HashMap<>();
        for (final String line : golden) {
            if (!line.startsWith("table ")) {
                continue;
            }
            final int colon = line.indexOf(':');
            final Map<String, String> columns = new HashMap<>();
            for (final String column : line.substring(colon + 1).trim().split(" (?=[A-Z_0-9]+=)")) {
                final int equals = column.indexOf('=');
                String tag = column.substring(equals + 1);
                final int paren = tag.indexOf('(');
                if (paren >= 0) {
                    tag = tag.substring(0, paren);
                }
                columns.put(column.substring(0, equals), tag);
            }
            result.put(line.substring("table ".length(), colon), columns);
        }
        return result;
    }
}
