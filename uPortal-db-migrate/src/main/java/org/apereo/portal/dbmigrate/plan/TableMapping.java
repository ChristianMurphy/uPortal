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
package org.apereo.portal.dbmigrate.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.dbmigrate.schema.ForeignKey;
import org.apereo.portal.dbmigrate.schema.Names;
import org.apereo.portal.dbmigrate.schema.Table;

/** One source table copied into one target table. */
public final class TableMapping {

    private final Table source;
    private final Table target;
    private final List<ColumnMapping> columns;
    private final List<ForeignKey> deferredForeignKeys = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private String sourceFilter;
    private long sourceRows = -1;
    private long targetRows = -1;

    public TableMapping(Table source, Table target, List<ColumnMapping> columns) {
        this.source = source;
        this.target = target;
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    }

    public Table source() {
        return source;
    }

    public Table target() {
        return target;
    }

    /** Key used for ordering and reports: the upper-case source table name. */
    public String key() {
        return source.key();
    }

    public List<ColumnMapping> columns() {
        return columns;
    }

    /** Columns that read a source column, in select order. */
    public List<ColumnMapping> sourcedColumns() {
        List<ColumnMapping> out = new ArrayList<>();
        for (ColumnMapping c : columns) {
            if (c.source() != null) {
                out.add(c);
            }
        }
        return out;
    }

    public boolean needsRowMap() {
        for (ColumnMapping c : columns) {
            if (c.transform() != null) {
                return true;
            }
        }
        return false;
    }

    public void deferForeignKey(ForeignKey fk) {
        deferredForeignKeys.add(fk);
    }

    /** Foreign keys whose columns are inserted as NULL first and filled by a second pass. */
    public List<ForeignKey> deferredForeignKeys() {
        return Collections.unmodifiableList(deferredForeignKeys);
    }

    /** Whether a target column is loaded in the second pass. */
    public boolean isDeferred(String targetColumn) {
        for (ForeignKey fk : deferredForeignKeys) {
            for (String c : fk.columns()) {
                if (Names.key(c).equals(Names.key(targetColumn))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A WHERE condition (without the keyword) applied to every read of the source; or null. */
    public String sourceFilter() {
        return sourceFilter;
    }

    public void sourceFilter(String filter) {
        this.sourceFilter = filter;
    }

    public List<String> notes() {
        return notes;
    }

    public void note(String note) {
        notes.add(note);
    }

    public long sourceRows() {
        return sourceRows;
    }

    public void sourceRows(long rows) {
        this.sourceRows = rows;
    }

    public long targetRows() {
        return targetRows;
    }

    public void targetRows(long rows) {
        this.targetRows = rows;
    }

    @Override
    public String toString() {
        return source.name() + " -> " + target.name();
    }
}
