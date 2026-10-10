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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apereo.portal.dbmigrate.PropertySettings;

/**
 * The load order for any foreign-key graph: with constraints left on, a table may only be loaded
 * after every table it references through a foreign key that is not loaded in a second pass, and
 * only nullable foreign keys may be loaded in a second pass. A graph whose non-nullable foreign
 * keys form a cycle cannot be loaded and must be reported.
 */
@AddLifecycleHook(PropertySettings.class)
class LoadOrderProperties {

    /** A random foreign-key graph: tables T0..Tn-1 and edges child -> parent. */
    static final class Graph {
        final List<String> tables = new ArrayList<>();
        final List<LoadOrder.Edge> edges = new ArrayList<>();

        @Override
        public String toString() {
            return tables + " " + edges;
        }
    }

    @Provide
    Arbitrary<Graph> graphs() {
        Arbitrary<Integer> sizes = Arbitraries.integers().between(1, 12);
        return sizes.flatMap(
                n -> {
                    Arbitrary<int[]> edge =
                            Combinators.combine(
                                            Arbitraries.integers().between(0, n - 1),
                                            Arbitraries.integers().between(0, n - 1),
                                            Arbitraries.integers().between(0, 2))
                                    .as((c, p, nullable) -> new int[] {c, p, nullable});
                    // Up to n^2 + 3 keys: dense graphs have parallel keys and many small cycles
                    return edge.list()
                            .ofMaxSize(n * n + 3)
                            .map(
                                    list -> {
                                        Graph g = new Graph();
                                        for (int i = 0; i < n; i++) {
                                            g.tables.add("T" + i);
                                        }
                                        for (int[] e : list) {
                                            g.edges.add(
                                                    new LoadOrder.Edge(
                                                            "T" + e[0],
                                                            "T" + e[1],
                                                            e[2] > 0,
                                                            e[0] + "->" + e[1]));
                                        }
                                        return g;
                                    });
                });
    }

    @Property(tries = 300)
    void everyTableLoadsOnceAfterTheTablesItReferences(@ForAll("graphs") Graph g) {
        LoadOrder order = LoadOrder.compute(g.tables, g.edges);
        List<String> ordered = order.ordered();
        assertEquals(g.toString(), new TreeSet<>(g.tables), new TreeSet<>(ordered));
        assertEquals(g.toString(), g.tables.size(), ordered.size());

        Map<String, Integer> level = new HashMap<>();
        for (int i = 0; i < order.levels().size(); i++) {
            for (String t : order.levels().get(i)) {
                level.put(t, i);
            }
        }
        Set<LoadOrder.Edge> deferred = new HashSet<>(order.deferred());
        if (order.unbreakableCycles().isEmpty()) {
            for (LoadOrder.Edge e : g.edges) {
                if (!deferred.contains(e)) {
                    assertTrue(
                            g + ": " + e + " loads the child before its parent",
                            level.get(e.parent()) < level.get(e.child()));
                }
            }
        }
        for (LoadOrder.Edge e : order.deferred()) {
            assertTrue(
                    g + ": only nullable keys may wait: " + e,
                    e.nullable || e.child().equals(e.parent()));
        }
    }

    @Property(tries = 300)
    void onlyCyclesOfNotNullKeysAreUnloadable(@ForAll("graphs") Graph g) {
        LoadOrder order = LoadOrder.compute(g.tables, g.edges);
        boolean notNullCycle = hasCycle(g, false);
        assertEquals(
                g + " " + order.unbreakableCycles(),
                notNullCycle,
                !order.unbreakableCycles().isEmpty());
    }

    @Property(tries = 100)
    void selfReferencesAlwaysWaitForTheSecondPass(@ForAll("graphs") Graph g) {
        LoadOrder order = LoadOrder.compute(g.tables, g.edges);
        for (LoadOrder.Edge e : g.edges) {
            if (e.child().equals(e.parent())) {
                assertTrue(g + ": " + e, order.deferred().contains(e));
            }
        }
        for (LoadOrder.Edge e : order.deferred()) {
            assertTrue(g.edges.contains(e));
        }
        for (String t : g.tables) {
            assertFalse(order.dependencies(t).contains(t));
        }
    }

    /**
     * Two tables that reference each other through three nullable keys each, listed alternately:
     * breaking the cycle takes more rounds than there are tables.
     */
    @Example
    void manyParallelNullableKeysStillBreakTheCycle() {
        List<LoadOrder.Edge> edges = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            edges.add(new LoadOrder.Edge("A", "B", true, "a" + i));
            edges.add(new LoadOrder.Edge("B", "A", true, "b" + i));
        }
        LoadOrder order = LoadOrder.compute(Arrays.asList("A", "B"), edges);
        assertEquals(order.unbreakableCycles().toString(), 0, order.unbreakableCycles().size());
        assertEquals(2, order.levels().size());
    }

    /** Whether the edges (all, or only the non-nullable ones) contain a cycle of 2+ tables. */
    private static boolean hasCycle(Graph g, boolean includeNullable) {
        Map<String, List<String>> out = new HashMap<>();
        for (LoadOrder.Edge e : g.edges) {
            if ((includeNullable || !e.nullable)) {
                if (e.child().equals(e.parent())) {
                    // A NOT NULL self-reference can never be loaded either
                    return true;
                }
                out.computeIfAbsent(e.child(), k -> new ArrayList<>()).add(e.parent());
            }
        }
        Map<String, Integer> state = new HashMap<>();
        for (String t : g.tables) {
            if (visit(t, out, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String t, Map<String, List<String>> out, Map<String, Integer> s) {
        Integer st = s.get(t);
        if (st != null) {
            return st == 1;
        }
        s.put(t, 1);
        for (String p : out.getOrDefault(t, new ArrayList<>())) {
            if (visit(p, out, s)) {
                return true;
            }
        }
        s.put(t, 2);
        return false;
    }
}
