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
package org.apereo.portal.tools.dbloader;

import java.lang.reflect.Field;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apereo.portal.hibernate.TableDefinition;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Builds a {@link TableDefinition} for every table in an XML definition file. Once parsing is
 * complete the tables are available via {@link #getTables()}
 */
public class TableXmlHandler extends BaseDbXmlHandler implements ITableDataProvider {

    @Override
    public Map<String, TableDefinition> getTables() {
        return this.tables;
    }

    @Override
    public Map<String, Map<String, Integer>> getTableColumnTypes() {
        return tableColumnTypes;
    }

    private Map<String, TableDefinition> tables = new LinkedHashMap<String, TableDefinition>();
    private Map<String, Map<String, Integer>> tableColumnTypes =
            new TreeMap<String, Map<String, Integer>>(String.CASE_INSENSITIVE_ORDER);

    private TableDefinition currentTable = null;
    private Map<String, Integer> currentColumnTypes = null;
    private String currentColumnName = null;
    private TableDefinition.Column currentColumn = null;
    private String currentKeyName = null;
    private List<String> currentKeyColumns = null;
    private boolean inIndex = false;
    private boolean inUnique = false;

    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes)
            throws SAXException {
        if ("table".equals(name)) {
            this.currentColumnTypes = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        } else if ("index".equals(name)) {
            this.inIndex = true;
            this.currentKeyColumns = new ArrayList<String>();
        } else if ("unique".equals(name)) {
            this.inUnique = true;
            this.currentKeyColumns = new ArrayList<String>();
        }

        this.chars = new StringBuilder();
    }

    /* (non-Javadoc)
     * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        if ("table".equals(name)) {
            this.tables.put(this.currentTable.getName(), this.currentTable);
            this.tableColumnTypes.put(this.currentTable.getName(), this.currentColumnTypes);
            this.currentColumnTypes = null;
            this.currentTable = null;
        } else if ("column".equals(name)) {
            this.currentColumnName = null;
            this.currentColumn = null;
        } else if ("name".equals(name)) {
            final String itemName = this.chars.toString().trim();

            if (this.inIndex || this.inUnique) {
                this.currentKeyName = itemName;
            } else if (this.currentTable == null) {
                this.currentTable = new TableDefinition(itemName);
            } else if (this.currentColumnName == null) {
                this.currentColumnName = itemName;
            }
        } else if ("type".equals(name)) {
            final String sqlTypeName = this.chars.toString().trim();

            final int sqlType = this.getSqlType(sqlTypeName);
            this.currentColumnTypes.put(this.currentColumnName, sqlType);

            this.currentColumn = this.currentTable.addColumn(this.currentColumnName, sqlType);
        } else if ("param".equals(name)) {
            final String param = this.chars.toString().trim();

            final Integer length = Integer.valueOf(param);
            this.currentColumn.setLength(length);
        } else if ("primary-key".equals(name)) {
            final String columnName = this.chars.toString().trim();
            this.currentTable.addPrimaryKeyColumn(columnName);
        } else if ("not-null".equals(name)) {
            final String columnName = this.chars.toString().trim();
            this.currentTable.getColumn(columnName).setNullable(false);
        } else if ("column-ref".equals(name)) {
            final String columnName = this.chars.toString().trim();
            this.currentKeyColumns.add(columnName);
        } else if ("index".equals(name)) {
            this.currentTable.addIndex(this.currentKeyName, this.currentKeyColumns);
            this.inIndex = false;
            this.currentKeyName = null;
            this.currentKeyColumns = null;
        } else if ("unique".equals(name)) {
            this.currentTable.addUniqueKey(this.currentKeyName, this.currentKeyColumns);
            this.inUnique = false;
            this.currentKeyName = null;
            this.currentKeyColumns = null;
        } else if ("key".equals(name)) {
            this.logger.warn(
                    "the 'key' element is ignored, use the table level 'primary-key' element instead");
        }

        this.chars = null;
    }

    protected int getSqlType(final String sqlTypeName) {
        try {
            final Field sqlTypeField = Types.class.getField(sqlTypeName);
            return sqlTypeField.getInt(null);
        } catch (SecurityException e) {
            throw new RuntimeException(
                    "Cannot access field '"
                            + sqlTypeName
                            + "' on "
                            + Types.class
                            + " for column '"
                            + this.currentColumnName
                            + "'",
                    e);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                    "No SQL Type field '"
                            + sqlTypeName
                            + "' on "
                            + Types.class
                            + " for column '"
                            + this.currentColumnName
                            + "'",
                    e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Cannot access field '"
                            + sqlTypeName
                            + "' on "
                            + Types.class
                            + " for column '"
                            + this.currentColumnName
                            + "'",
                    e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Cannot access field '"
                            + sqlTypeName
                            + "' on "
                            + Types.class
                            + " for column '"
                            + this.currentColumnName
                            + "'",
                    e);
        }
    }
}
