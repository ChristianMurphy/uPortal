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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.apereo.portal.dbmigrate.Vendor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads tables, columns, keys and sequences through the JDBC metadata, limited to one schema (or
 * one catalog on MySQL/MariaDB), so that other schemas on the same server are never touched.
 */
public final class SchemaReader {

    private SchemaReader() {}

    static Dialect dialect(Connection c, String schemaOverride) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        Vendor vendor = Vendor.detect(md.getDatabaseProductName(), md.getDatabaseProductVersion());
        String catalog = null;
        String schema = null;
        if (vendor.isMySqlFamily()) {
            catalog = schemaOverride != null ? schemaOverride : c.getCatalog();
        } else {
            if (vendor == Vendor.SQLSERVER) {
                catalog = c.getCatalog();
            }
            schema = schemaOverride != null ? schemaOverride : c.getSchema();
            if (schema == null && vendor == Vendor.ORACLE) {
                schema = md.getUserName();
            }
        }
        return new Dialect(
                vendor, md.getIdentifierQuoteString(), catalog, schema, md.getDriverName());
    }

    /** Reads the schema through a pooled data source (Spring JDBC). */
    public static SchemaModel read(DataSource dataSource, String schemaOverride) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SchemaModel model =
                jdbc.execute((ConnectionCallback<SchemaModel>) c -> read(c, schemaOverride));
        for (String seq : model.dialect().listSequences(jdbc)) {
            model.addSequence(seq);
        }
        return model;
    }

    /** Detects the vendor and scope of a data source. */
    public static Dialect dialect(DataSource dataSource, String schemaOverride) {
        return new JdbcTemplate(dataSource)
                .execute((ConnectionCallback<Dialect>) c -> dialect(c, schemaOverride));
    }

    static SchemaModel read(Connection c, String schemaOverride) throws SQLException {
        Dialect dialect = dialect(c, schemaOverride);
        DatabaseMetaData md = c.getMetaData();
        SchemaModel model =
                new SchemaModel(
                        dialect,
                        md.getDatabaseProductName(),
                        md.getDatabaseProductVersion(),
                        md.getDriverName() + " " + md.getDriverVersion());
        String catalog = dialect.catalog();
        String schema = dialect.schema();

        // A PostgreSQL partitioned table is loaded through its parent ("PARTITIONED TABLE"), which
        // routes the rows; its partitions are not tables of their own for the copy
        boolean partitioned =
                dialect.vendor() == Vendor.POSTGRESQL && md.getDatabaseMajorVersion() >= 10;
        Set<String> partitions = partitioned ? postgresPartitions(c, schema) : new TreeSet<>();
        String[] types =
                partitioned ? new String[] {"TABLE", "PARTITIONED TABLE"} : new String[] {"TABLE"};
        Map<String, Table> tables = new TreeMap<>();
        try (ResultSet rs = md.getTables(catalog, schema, "%", types)) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!inScope(dialect, rs.getString("TABLE_CAT"), rs.getString("TABLE_SCHEM"))
                        || isSystemTable(dialect.vendor(), name)
                        || partitions.contains(name)) {
                    continue;
                }
                tables.put(
                        Names.key(name),
                        new Table(rs.getString("TABLE_CAT"), rs.getString("TABLE_SCHEM"), name));
            }
        }

        // MySQL-family drivers report no DECIMAL_DIGITS for temporal columns, and their display
        // sizes differ (MariaDB Connector/J: TIME(0) is 10 characters, MySQL Connector/J: 8)
        Map<String, Integer> temporalDigits =
                dialect.vendor().isMySqlFamily()
                        ? mysqlDatetimePrecision(c, catalog)
                        : new HashMap<>();

        // One call for every column of the schema; the table name pattern would treat '_' as a
        // wildcard anyway, so results are matched by exact name.
        Map<String, List<Column>> columns = new HashMap<>();
        try (ResultSet rs = md.getColumns(catalog, schema, "%", "%")) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                Table t = tables.get(Names.key(table));
                if (t == null
                        || !t.name().equals(table)
                        || !inScope(
                                dialect, rs.getString("TABLE_CAT"), rs.getString("TABLE_SCHEM"))) {
                    continue;
                }
                int digits = rs.getInt("DECIMAL_DIGITS");
                Integer decimalDigits = rs.wasNull() ? null : digits;
                Integer precision =
                        temporalDigits.get(Names.key(table + "." + rs.getString("COLUMN_NAME")));
                if (precision != null) {
                    decimalDigits = precision;
                }
                String def = rs.getString("COLUMN_DEF");
                String autoInc = rs.getString("IS_AUTOINCREMENT");
                columns.computeIfAbsent(t.key(), k -> new ArrayList<>())
                        .add(
                                new Column(
                                        rs.getString("COLUMN_NAME"),
                                        rs.getInt("DATA_TYPE"),
                                        rs.getString("TYPE_NAME"),
                                        rs.getInt("COLUMN_SIZE"),
                                        decimalDigits,
                                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                                        def != null && !def.trim().isEmpty(),
                                        "YES".equalsIgnoreCase(autoInc),
                                        rs.getInt("ORDINAL_POSITION")));
            }
        }

        for (Table t : tables.values()) {
            List<Column> cols = columns.getOrDefault(t.key(), new ArrayList<>());
            cols.sort((a, b) -> Integer.compare(a.position(), b.position()));
            cols.forEach(t::addColumn);
            t.primaryKey(primaryKey(md, catalog, schema, t.name()));
            for (ForeignKey fk : importedKeys(md, catalog, schema, t.name())) {
                t.addForeignKey(fk);
            }
            for (List<String> uk : uniqueKeys(md, catalog, schema, t.name())) {
                t.addUniqueKey(uk);
            }
            model.addTable(t);
        }
        return model;
    }

    /** Fractional-second digits of the TIME, DATETIME and TIMESTAMP columns, by TABLE.COLUMN. */
    private static Map<String, Integer> mysqlDatetimePrecision(Connection c, String catalog)
            throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        try (PreparedStatement ps =
                c.prepareStatement(
                        "SELECT TABLE_NAME, COLUMN_NAME, DATETIME_PRECISION"
                                + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ?"
                                + " AND DATETIME_PRECISION IS NOT NULL")) {
            ps.setString(1, catalog);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(Names.key(rs.getString(1) + "." + rs.getString(2)), rs.getInt(3));
                }
            }
        }
        return out;
    }

    private static Set<String> postgresPartitions(Connection c, String schema) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (PreparedStatement ps =
                c.prepareStatement(
                        "SELECT c.relname FROM pg_class c JOIN pg_namespace n"
                                + " ON n.oid = c.relnamespace WHERE c.relispartition"
                                + " AND n.nspname = ?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    private static boolean inScope(Dialect d, String cat, String schem) {
        if (d.vendor().isMySqlFamily()) {
            return d.catalog() == null || cat == null || d.catalog().equalsIgnoreCase(cat);
        }
        return d.schema() == null || schem == null || d.schema().equalsIgnoreCase(schem);
    }

    private static boolean isSystemTable(Vendor vendor, String name) {
        // Oracle's recycle bin; dropped tables live on as BIN$... in the same schema
        return vendor == Vendor.ORACLE && name.startsWith("BIN$");
    }

    private static List<String> primaryKey(
            DatabaseMetaData md, String catalog, String schema, String table) throws SQLException {
        TreeMap<Integer, String> cols = new TreeMap<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                cols.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return new ArrayList<>(cols.values());
    }

    /**
     * Unique indexes (unique constraints are backed by one), by name. Approximate statistics: an
     * exact request makes Oracle analyze the table.
     */
    private static List<List<String>> uniqueKeys(
            DatabaseMetaData md, String catalog, String schema, String table) throws SQLException {
        Map<String, TreeMap<Integer, String>> indexes = new TreeMap<>();
        try (ResultSet rs = md.getIndexInfo(catalog, schema, table, true, true)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic
                        || name == null
                        || column == null
                        || rs.getBoolean("NON_UNIQUE")) {
                    continue;
                }
                indexes.computeIfAbsent(name, k -> new TreeMap<>())
                        .put((int) rs.getShort("ORDINAL_POSITION"), column);
            }
        }
        List<List<String>> out = new ArrayList<>();
        for (TreeMap<Integer, String> cols : indexes.values()) {
            out.add(new ArrayList<>(cols.values()));
        }
        return out;
    }

    private static List<ForeignKey> importedKeys(
            DatabaseMetaData md, String catalog, String schema, String table) throws SQLException {
        // Keyed by constraint name (or the referenced table when unnamed), ordered by KEY_SEQ
        Map<String, TreeMap<Integer, String[]>> parts = new TreeMap<>();
        Map<String, String[]> heads = new HashMap<>();
        try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String name = rs.getString("FK_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String key = (name == null ? "" : name) + "/" + pkTable;
                heads.put(key, new String[] {name, pkTable});
                parts.computeIfAbsent(key, k -> new TreeMap<>())
                        .put(
                                rs.getInt("KEY_SEQ"),
                                new String[] {
                                    rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME")
                                });
            }
        }
        List<ForeignKey> fks = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Integer, String[]>> e : parts.entrySet()) {
            List<String> fkCols = new ArrayList<>();
            List<String> pkCols = new ArrayList<>();
            for (String[] p : e.getValue().values()) {
                fkCols.add(p[0]);
                pkCols.add(p[1]);
            }
            String[] head = heads.get(e.getKey());
            fks.add(new ForeignKey(head[0], table, fkCols, head[1], pkCols));
        }
        return fks;
    }
}
