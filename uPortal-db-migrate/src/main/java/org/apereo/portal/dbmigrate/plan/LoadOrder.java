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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Orders tables so that every table is loaded after the tables its foreign keys reference, with
 * constraints left enabled on the target.
 *
 * <p>Cycles (a self-reference, or tables that reference each other) are broken at foreign keys
 * whose columns are all nullable: those columns are inserted as NULL and filled in by a second pass
 * once every table is loaded ({@link #deferred()}). A cycle with no nullable foreign key cannot be
 * loaded with constraints on, and is reported as an error.
 */
public final class LoadOrder {

    /** A foreign key as a dependency edge: {@code child} references {@code parent}. */
    public static final class Edge {
        final String child;
        final String parent;
        final boolean nullable;
        final Object payload;

        public Edge(String child, String parent, boolean nullable, Object payload) {
            this.child = child;
            this.parent = parent;
            this.nullable = nullable;
            this.payload = payload;
        }

        public String child() {
            return child;
        }

        public String parent() {
            return parent;
        }

        public Object payload() {
            return payload;
        }

        @Override
        public String toString() {
            return child + "->" + parent + (nullable ? "?" : "");
        }
    }

    private final List<List<String>> levels;
    private final List<Edge> deferred;
    private final List<String> unbreakableCycles;
    private final Map<String, Set<String>> dependencies;

    private LoadOrder(
            List<List<String>> levels,
            List<Edge> deferred,
            List<String> unbreakableCycles,
            Map<String, Set<String>> dependencies) {
        this.levels = levels;
        this.deferred = deferred;
        this.unbreakableCycles = unbreakableCycles;
        this.dependencies = dependencies;
    }

    /**
     * Orders {@code tables} by their foreign keys.
     *
     * @param tables the tables to load (keys)
     * @param edges foreign keys between them; edges to tables outside {@code tables} are ignored
     */
    public static LoadOrder compute(Collection<String> tables, Collection<Edge> edges) {
        Set<String> nodes = new TreeSet<>(tables);
        List<Edge> deferred = new ArrayList<>();
        List<Edge> active = new ArrayList<>();
        for (Edge e : edges) {
            if (!nodes.contains(e.child) || !nodes.contains(e.parent)) {
                continue;
            }
            if (e.child.equals(e.parent)) {
                deferred.add(e); // self-reference: always loaded in a second pass
            } else {
                active.add(e);
            }
        }

        // Break multi-table cycles at nullable edges inside each strongly connected component
        List<String> unbreakable = new ArrayList<>();
        for (int round = 0; ; round++) {
            List<Set<String>> cyclic = new ArrayList<>();
            for (Set<String> scc : stronglyConnected(nodes, active)) {
                if (scc.size() > 1) {
                    cyclic.add(scc);
                }
            }
            if (cyclic.isEmpty() || round > nodes.size()) {
                for (Set<String> scc : cyclic) {
                    unbreakable.add(String.join(", ", scc));
                }
                break;
            }
            boolean removed = false;
            for (Set<String> scc : cyclic) {
                for (Edge e : new ArrayList<>(active)) {
                    if (e.nullable && scc.contains(e.child) && scc.contains(e.parent)) {
                        active.remove(e);
                        deferred.add(e);
                        removed = true;
                        break; // one edge per component per round; re-check the components
                    }
                }
            }
            if (!removed) {
                for (Set<String> scc : cyclic) {
                    unbreakable.add(String.join(", ", scc));
                }
                break;
            }
        }
        for (Edge e : deferred) {
            if (!e.nullable) {
                unbreakable.add(e.child + " (non-nullable self-reference)");
            }
        }

        // Kahn's algorithm by levels: a level only depends on earlier levels
        Map<String, Set<String>> parents = new TreeMap<>();
        for (String n : nodes) {
            parents.put(n, new TreeSet<>());
        }
        for (Edge e : active) {
            parents.get(e.child).add(e.parent);
        }
        Map<String, Set<String>> remaining = new HashMap<>();
        parents.forEach((k, v) -> remaining.put(k, new TreeSet<>(v)));
        List<List<String>> levels = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        while (done.size() < nodes.size()) {
            List<String> level = new ArrayList<>();
            for (String n : nodes) {
                if (!done.contains(n) && done.containsAll(remaining.get(n))) {
                    level.add(n);
                }
            }
            if (level.isEmpty()) {
                // Only reachable with an unbreakable cycle; load the rest in name order
                for (String n : nodes) {
                    if (!done.contains(n)) {
                        level.add(n);
                    }
                }
            }
            done.addAll(level);
            levels.add(level);
        }
        return new LoadOrder(levels, deferred, unbreakable, parents);
    }

    private static List<Set<String>> stronglyConnected(Set<String> nodes, List<Edge> edges) {
        Map<String, List<String>> out = new HashMap<>();
        for (Edge e : edges) {
            out.computeIfAbsent(e.child, k -> new ArrayList<>()).add(e.parent);
        }
        // Iterative Tarjan
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new TreeSet<>();
        List<Set<String>> result = new ArrayList<>();
        int[] counter = {0};
        for (String start : nodes) {
            if (index.containsKey(start)) {
                continue;
            }
            Deque<Object[]> work = new ArrayDeque<>();
            work.push(new Object[] {start, 0});
            while (!work.isEmpty()) {
                Object[] frame = work.peek();
                String v = (String) frame[0];
                int i = (Integer) frame[1];
                if (i == 0 && !index.containsKey(v)) {
                    index.put(v, counter[0]);
                    low.put(v, counter[0]);
                    counter[0]++;
                    stack.push(v);
                    onStack.add(v);
                }
                List<String> next = out.getOrDefault(v, Collections.emptyList());
                if (i < next.size()) {
                    frame[1] = i + 1;
                    String w = next.get(i);
                    if (!index.containsKey(w)) {
                        work.push(new Object[] {w, 0});
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                } else {
                    work.pop();
                    if (!work.isEmpty()) {
                        String parent = (String) work.peek()[0];
                        low.put(parent, Math.min(low.get(parent), low.get(v)));
                    }
                    if (low.get(v).equals(index.get(v))) {
                        Set<String> scc = new TreeSet<>();
                        String w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            scc.add(w);
                        } while (!w.equals(v));
                        result.add(scc);
                    }
                }
            }
        }
        return result;
    }

    /** Groups of tables; every table only references tables of earlier groups. */
    public List<List<String>> levels() {
        return levels;
    }

    public List<String> ordered() {
        List<String> all = new ArrayList<>();
        levels.forEach(all::addAll);
        return all;
    }

    /** Foreign keys loaded in a second pass (self-references and cycle breakers). */
    public List<Edge> deferred() {
        return deferred;
    }

    /** Cycles that contain no nullable foreign key; the plan fails on them. */
    public List<String> unbreakableCycles() {
        return unbreakableCycles;
    }

    /** The tables a table must wait for (its non-deferred foreign key parents). */
    public Set<String> dependencies(String table) {
        return dependencies.getOrDefault(table, Collections.emptySet());
    }
}
