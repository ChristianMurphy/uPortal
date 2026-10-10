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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apereo.portal.dbmigrate.catalog.GeneratorCatalog;
import org.apereo.portal.dbmigrate.rules.RuleSet;
import org.apereo.portal.dbmigrate.rules.ValueTransform;
import org.apereo.portal.dbmigrate.schema.Column;
import org.apereo.portal.dbmigrate.schema.ForeignKey;
import org.apereo.portal.dbmigrate.schema.Names;
import org.apereo.portal.dbmigrate.schema.SchemaModel;
import org.apereo.portal.dbmigrate.schema.Table;
import org.apereo.portal.dbmigrate.types.ColumnPair;
import org.apereo.portal.dbmigrate.types.ColumnType;

/**
 * Maps the source schema onto the target schema: tables and columns by name unless a rule says
 * otherwise, value types per column pair, load order from the target's foreign keys, and the ID
 * generators to position. Works on metadata only; anything it cannot map is an error, so no data is
 * dropped without a rule saying so.
 */
public final class Planner {

    private static final String NEXT_VAL = "NEXT_VAL";

    private final SchemaProfile sourceProfile;
    private final SchemaProfile targetProfile;
    private final RuleSet rules;
    private final List<String> includes;

    /**
     * @param rules mapping rules between the two schemas (renames, transforms, excludes)
     * @param includes table globs to restrict the copy to; empty for every table
     */
    public Planner(
            SchemaProfile sourceProfile,
            SchemaProfile targetProfile,
            RuleSet rules,
            List<String> includes) {
        this.sourceProfile = sourceProfile;
        this.targetProfile = targetProfile;
        this.rules = rules;
        this.includes = includes;
    }

    /** Hibernate's sequence emulation table: a single {@code next_val} column (MOD-I23). */
    public static boolean isSequenceTable(Table t) {
        return t.columns().size() == 1 && NEXT_VAL.equals(t.columns().get(0).key());
    }

    public MigrationPlan plan(SchemaModel source, SchemaModel target) {
        MigrationPlan plan = new MigrationPlan(source, target);
        Map<String, String> targetToMapping = new HashMap<>();

        for (Table s : source.tables()) {
            if (isSequenceTable(s)) {
                plan.generatorTables().add(s.name());
                continue;
            }
            if (rules.isExcluded(s.name()) || !included(s.name())) {
                plan.excludedTables().add(s.name());
                continue;
            }
            String targetName = rules.targetTableName(s.name());
            Table t = target.table(targetName);
            if (t == null || isSequenceTable(t)) {
                plan.error(
                        "Source table "
                                + s.name()
                                + " has no target table "
                                + targetName
                                + "; exclude it (--exclude) or map it with a rename-table rule");
                continue;
            }
            String already = targetToMapping.get(t.key());
            if (already != null) {
                plan.error(
                        "Source tables "
                                + already
                                + " and "
                                + s.name()
                                + " both map to "
                                + t.name());
                continue;
            }
            TableMapping m = mapTable(plan, s, t);
            if (m != null) {
                plan.addTable(m);
                targetToMapping.put(t.key(), s.key());
            }
        }

        for (Table t : target.tables()) {
            if (!targetToMapping.containsKey(t.key()) && !isSequenceTable(t)) {
                plan.targetOnlyTables().add(t.name());
            }
        }

        order(plan, targetToMapping);
        generators(plan, source, target);
        for (TableMapping m : plan.tables()) {
            for (Column c : m.target().columns()) {
                if (c.autoIncrement()) {
                    plan.identityColumns().add(new MigrationPlan.IdentityColumn(m.target(), c));
                }
            }
        }
        return plan;
    }

    private boolean included(String table) {
        if (includes.isEmpty()) {
            return true;
        }
        for (String glob : includes) {
            if (Names.globMatches(glob, table)) {
                return true;
            }
        }
        return false;
    }

    private TableMapping mapTable(MigrationPlan plan, Table s, Table t) {
        List<ColumnMapping> columns = new ArrayList<>();
        Map<String, String> filled = new LinkedHashMap<>();
        boolean ok = true;
        List<String> notes = new ArrayList<>();
        for (Column sc : s.columns()) {
            if (rules.isSkipped(s.name(), sc.name())) {
                notes.add("skip-column " + sc.name());
                continue;
            }
            String targetColumn = rules.targetColumnName(s.name(), sc.name());
            Column tc = t.column(targetColumn);
            if (tc == null) {
                plan.error(
                        "Source column "
                                + s.name()
                                + "."
                                + sc.name()
                                + " has no target column "
                                + t.name()
                                + "."
                                + targetColumn
                                + "; add a skip-column or rename-column rule");
                ok = false;
                continue;
            }
            if (filled.containsKey(tc.key())) {
                plan.error(
                        t.name()
                                + "."
                                + tc.name()
                                + " is filled by both "
                                + filled.get(tc.key())
                                + " and "
                                + sc.name());
                ok = false;
                continue;
            }
            RuleSet.TransformRule rule = rules.transform(s.name(), sc.name());
            ColumnMapping cm = column(plan, s, sc, t, tc, rule);
            if (cm == null) {
                ok = false;
                continue;
            }
            filled.put(tc.key(), sc.name());
            columns.add(cm);
        }
        for (Map.Entry<String, RuleSet.TransformRule> d :
                rules.derivedColumns(s.name()).entrySet()) {
            Column tc = t.column(d.getKey());
            if (tc == null) {
                plan.error(
                        "derive-column " + s.name() + "." + d.getKey() + ": no such target column");
                ok = false;
                continue;
            }
            if (filled.containsKey(tc.key())) {
                plan.error("derive-column " + t.name() + "." + tc.name() + " is also copied");
                ok = false;
                continue;
            }
            ColumnMapping cm = column(plan, s, null, t, tc, d.getValue());
            if (cm == null) {
                ok = false;
                continue;
            }
            filled.put(tc.key(), "derived");
            columns.add(cm);
        }
        for (Column tc : t.columns()) {
            if (filled.containsKey(tc.key())) {
                continue;
            }
            if (!tc.nullable() && !tc.hasDefault() && !tc.autoIncrement()) {
                plan.error(
                        "Target column "
                                + t.name()
                                + "."
                                + tc.name()
                                + " is NOT NULL without a default and nothing fills it;"
                                + " add a derive-column rule");
                ok = false;
            } else {
                notes.add("target-only column " + tc.name() + " left to its default");
            }
        }
        if (!ok) {
            return null;
        }
        TableMapping m = new TableMapping(s, t, columns);
        notes.forEach(m::note);
        for (ColumnMapping cm : columns) {
            for (String n : cm.pair().notes()) {
                m.note(cm.target().name() + ": " + n);
            }
        }
        return m;
    }

    private ColumnMapping column(
            MigrationPlan plan,
            Table s,
            Column sc,
            Table t,
            Column tc,
            RuleSet.TransformRule rule) {
        try {
            ValueTransform.Function fn = rule == null ? null : RuleSet.bind(rule);
            ColumnType tType =
                    ColumnType.classify(
                            plan.target().vendor(),
                            tc,
                            plan.target().vendor() == org.apereo.portal.dbmigrate.Vendor.POSTGRESQL
                                    && targetProfile.rules().isLobOid(t.name(), tc.name()));
            ColumnType sType =
                    sc == null
                            ? null
                            : ColumnType.classify(
                                    plan.source().vendor(),
                                    sc,
                                    plan.source().vendor()
                                                    == org.apereo.portal.dbmigrate.Vendor.POSTGRESQL
                                            && sourceProfile.rules().isLobOid(s.name(), sc.name()));
            ColumnPair pair =
                    ColumnPair.resolve(sType, tType, fn == null ? null : fn.resultFamily());
            return new ColumnMapping(sc, tc, pair, fn, rule == null ? null : rule.toString());
        } catch (IllegalArgumentException e) {
            plan.error(t.name() + "." + tc.name() + ": " + e.getMessage());
            return null;
        }
    }

    private static void order(MigrationPlan plan, Map<String, String> targetToMapping) {
        List<LoadOrder.Edge> edges = new ArrayList<>();
        Set<String> keys = new TreeSet<>();
        for (TableMapping m : plan.tables()) {
            keys.add(m.key());
            for (ForeignKey fk : m.target().foreignKeys()) {
                String parent = targetToMapping.get(Names.key(fk.referencedTable()));
                if (parent == null) {
                    Table pt = plan.target().table(fk.referencedTable());
                    plan.warn(
                            "Foreign key "
                                    + fk
                                    + " references "
                                    + fk.referencedTable()
                                    + ", which is not copied"
                                    + (pt == null ? "" : " (its existing target rows must match)"));
                    continue;
                }
                boolean nullable = true;
                for (String c : fk.columns()) {
                    Column col = m.target().column(c);
                    nullable &= col != null && col.nullable();
                }
                edges.add(new LoadOrder.Edge(m.key(), parent, nullable, fk));
            }
        }
        LoadOrder order = LoadOrder.compute(keys, edges);
        for (String cycle : order.unbreakableCycles()) {
            plan.error(
                    "Foreign key cycle without a nullable column cannot be loaded with constraints"
                            + " enabled: "
                            + cycle);
        }
        for (LoadOrder.Edge e : order.deferred()) {
            TableMapping m = plan.table(e.child());
            ForeignKey fk = (ForeignKey) e.payload();
            if (m.target().primaryKey().isEmpty()) {
                plan.error(
                        m.target().name()
                                + " needs a primary key: its foreign key "
                                + fk
                                + " is loaded in a second pass");
            }
            m.deferForeignKey(fk);
            m.note(
                    "foreign key "
                            + fk.columns()
                            + " -> "
                            + fk.referencedTable()
                            + " loaded in a second pass");
        }
        plan.order(order);
    }

    private void generators(MigrationPlan plan, SchemaModel source, SchemaModel target) {
        GeneratorCatalog catalog = targetProfile.generators();
        // Source generator objects, by the target name they correspond to
        Map<String, Object[]> sourceObjects = new TreeMap<>();
        for (String seq : source.sequences()) {
            sourceObjects.put(
                    Names.key(rules.targetGeneratorName(seq)),
                    new Object[] {seq, GeneratorTarget.Storage.SEQUENCE, null});
        }
        for (Table t : source.tables()) {
            if (isSequenceTable(t)) {
                sourceObjects.put(
                        Names.key(rules.targetGeneratorName(t.name())),
                        new Object[] {t.name(), GeneratorTarget.Storage.TABLE, t});
            }
        }
        Map<String, String> names = new TreeMap<>();
        for (GeneratorCatalog.Generator g : catalog.generators()) {
            names.put(Names.key(g.name()), g.name());
        }
        for (String seq : target.sequences()) {
            names.put(Names.key(seq), seq);
        }
        for (Table t : target.tables()) {
            if (isSequenceTable(t)) {
                names.put(t.key(), t.name());
            }
        }
        for (Map.Entry<String, String> e : names.entrySet()) {
            String key = e.getKey();
            GeneratorTarget.Storage storage = GeneratorTarget.Storage.NONE;
            Table targetTable = null;
            String name = e.getValue();
            if (target.sequence(name) != null) {
                storage = GeneratorTarget.Storage.SEQUENCE;
                name = target.sequence(name);
            } else if (target.table(name) != null && isSequenceTable(target.table(name))) {
                storage = GeneratorTarget.Storage.TABLE;
                targetTable = target.table(name);
                name = targetTable.name();
            }
            Object[] src = sourceObjects.get(key);
            GeneratorCatalog.Generator g = catalog.generator(key);
            GeneratorTarget gt =
                    new GeneratorTarget(
                            name,
                            storage,
                            targetTable,
                            src == null ? null : (String) src[0],
                            src == null
                                    ? GeneratorTarget.Storage.NONE
                                    : (GeneratorTarget.Storage) src[1],
                            src == null ? null : (Table) src[2],
                            g == null ? 0 : g.allocationSize(),
                            g == null
                                    ? "database"
                                    : (g.entities().isEmpty() ? "rule" : "entities"));
            boolean servesCopiedRows = false;
            if (g != null) {
                for (String ref : g.serves()) {
                    int dot = ref.lastIndexOf('.');
                    Table t = target.table(ref.substring(0, dot));
                    String col = ref.substring(dot + 1);
                    if (t != null && t.column(col) != null) {
                        gt.addServed(new GeneratorTarget.Served(t, t.column(col).name()));
                        servesCopiedRows = true;
                    }
                }
            }
            if (storage == GeneratorTarget.Storage.NONE) {
                if (servesCopiedRows && !g.entities().isEmpty()) {
                    plan.error(
                            "The target has no sequence or sequence table "
                                    + name
                                    + " for "
                                    + gt.served()
                                    + "; is it a "
                                    + targetProfile.name()
                                    + " schema?");
                } else if (servesCopiedRows) {
                    // A rule-described generator (e.g. a portlet's) need not exist on every
                    // vendor: MySQL-family dialects use AUTO_INCREMENT columns instead
                    List<String> notGenerated = new ArrayList<>();
                    for (GeneratorTarget.Served sv : gt.served()) {
                        if (!sv.table().column(sv.column()).autoIncrement()) {
                            notGenerated.add(sv.toString());
                        }
                    }
                    if (!notGenerated.isEmpty()) {
                        plan.warn(
                                "Generator "
                                        + name
                                        + " is not in the target, and "
                                        + notGenerated
                                        + " are not identity columns there; whatever assigns"
                                        + " their ids must start above the copied ones");
                    }
                }
                continue;
            }
            if (g == null && src == null) {
                plan.warn(
                        "Generator "
                                + name
                                + " exists only in the target and no rule says which ids it"
                                + " serves; it is left as it is");
            }
            plan.generators().add(gt);
        }
    }
}
