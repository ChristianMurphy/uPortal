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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apereo.portal.dbmigrate.Vendor;

/** The tables and sequences of one schema, looked up case-insensitively. */
public final class SchemaModel {

    private final Dialect dialect;
    private final String productName;
    private final String productVersion;
    private final String driver;
    private final TreeMap<String, Table> tables = new TreeMap<>();
    private final Set<String> sequences = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public SchemaModel(Dialect dialect, String productName, String productVersion, String driver) {
        this.dialect = dialect;
        this.productName = productName;
        this.productVersion = productVersion;
        this.driver = driver;
    }

    public Dialect dialect() {
        return dialect;
    }

    public Vendor vendor() {
        return dialect.vendor();
    }

    public String productName() {
        return productName;
    }

    public String productVersion() {
        return productVersion;
    }

    public String driver() {
        return driver;
    }

    public SchemaModel addTable(Table t) {
        tables.put(t.key(), t);
        return this;
    }

    public SchemaModel addSequence(String name) {
        sequences.add(name);
        return this;
    }

    public Table table(String name) {
        return tables.get(Names.key(name));
    }

    public Collection<Table> tables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    /** The actual (database-cased) name of a sequence, or null. */
    public String sequence(String name) {
        for (String s : sequences) {
            if (s.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public Set<String> sequences() {
        return Collections.unmodifiableSet(sequences);
    }
}
