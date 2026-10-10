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
import org.apereo.portal.dbmigrate.schema.Table;

/**
 * An ID generator of the target, where its position is stored on both sides, and the id columns it
 * must stay above.
 */
public final class GeneratorTarget {

    public enum Storage {
        /** A database sequence. */
        SEQUENCE,
        /** Hibernate's one-row emulation table with a {@code next_val} column (MOD-I23). */
        TABLE,
        /** Not present. */
        NONE
    }

    /** An id column the generator fills. */
    public static final class Served {
        private final Table table;
        private final String column;

        public Served(Table table, String column) {
            this.table = table;
            this.column = column;
        }

        public Table table() {
            return table;
        }

        public String column() {
            return column;
        }

        @Override
        public String toString() {
            return table.name() + "." + column;
        }
    }

    private final String name;
    private final Storage targetStorage;
    private final Table targetTable;
    private final String sourceName;
    private final Storage sourceStorage;
    private final Table sourceTable;
    private final int allocationSize;
    private final String origin;
    private final List<Served> served = new ArrayList<>();

    public GeneratorTarget(
            String name,
            Storage targetStorage,
            Table targetTable,
            String sourceName,
            Storage sourceStorage,
            Table sourceTable,
            int allocationSize,
            String origin) {
        this.name = name;
        this.targetStorage = targetStorage;
        this.targetTable = targetTable;
        this.sourceName = sourceName;
        this.sourceStorage = sourceStorage;
        this.sourceTable = sourceTable;
        this.allocationSize = allocationSize;
        this.origin = origin;
    }

    public String name() {
        return name;
    }

    public Storage targetStorage() {
        return targetStorage;
    }

    /** The emulation table on the target, for {@link Storage#TABLE}. */
    public Table targetTable() {
        return targetTable;
    }

    public String sourceName() {
        return sourceName;
    }

    public Storage sourceStorage() {
        return sourceStorage;
    }

    public Table sourceTable() {
        return sourceTable;
    }

    /**
     * Hibernate's allocationSize: with the pooled optimizers one database value covers this many
     * ids, so the target's next value must be at least {@code max(id) + allocationSize}.
     */
    public int allocationSize() {
        return allocationSize;
    }

    /** Where the mapping came from: entity annotations, a rule, or the databases themselves. */
    public String origin() {
        return origin;
    }

    public List<Served> served() {
        return Collections.unmodifiableList(served);
    }

    public void addServed(Served s) {
        served.add(s);
    }

    @Override
    public String toString() {
        return name
                + " ("
                + targetStorage
                + ", alloc "
                + allocationSize
                + ", serves "
                + served
                + ")";
    }
}
