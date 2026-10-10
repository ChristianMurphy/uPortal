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
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class LoadOrderTest {

    private static LoadOrder.Edge edge(String child, String parent, boolean nullable) {
        return new LoadOrder.Edge(child, parent, nullable, child + "->" + parent);
    }

    @Test
    public void parentsComeFirstInLevels() {
        LoadOrder o =
                LoadOrder.compute(
                        Arrays.asList("A", "B", "C", "D", "OUT_OF_SCOPE_CHILD"),
                        Arrays.asList(
                                edge("B", "A", false),
                                edge("C", "B", false),
                                edge("D", "A", true),
                                edge("C", "EXCLUDED", false)));
        assertEquals(
                Arrays.asList(
                        Arrays.asList("A", "OUT_OF_SCOPE_CHILD"),
                        Arrays.asList("B", "D"),
                        Arrays.asList("C")),
                o.levels());
        assertTrue(o.deferred().isEmpty());
        assertTrue(o.unbreakableCycles().isEmpty());
        assertEquals("[A]", o.dependencies("B").toString());
    }

    @Test
    public void selfReferencesAreDeferred() {
        LoadOrder o = LoadOrder.compute(Arrays.asList("T"), Arrays.asList(edge("T", "T", true)));
        assertEquals(1, o.deferred().size());
        assertEquals(Arrays.asList(Arrays.asList("T")), o.levels());
        assertTrue(o.unbreakableCycles().isEmpty());
    }

    @Test
    public void cyclesBreakAtANullableForeignKey() {
        LoadOrder o =
                LoadOrder.compute(
                        Arrays.asList("A", "B", "C"),
                        Arrays.asList(
                                edge("A", "B", false),
                                edge("B", "C", true),
                                edge("C", "A", false)));
        assertEquals(1, o.deferred().size());
        assertEquals("B", o.deferred().get(0).child());
        List<String> ordered = o.ordered();
        // B -> C is deferred; what is left is A -> B and C -> A
        assertEquals(Arrays.asList("B", "A", "C"), ordered);
        assertTrue(o.unbreakableCycles().isEmpty());
    }

    @Test
    public void cyclesWithoutNullableKeysAreReported() {
        LoadOrder o =
                LoadOrder.compute(
                        Arrays.asList("A", "B"),
                        Arrays.asList(edge("A", "B", false), edge("B", "A", false)));
        assertEquals(Arrays.asList("A, B"), o.unbreakableCycles());
        LoadOrder self =
                LoadOrder.compute(Arrays.asList("T"), Arrays.asList(edge("T", "T", false)));
        assertEquals(1, self.unbreakableCycles().size());
    }
}
