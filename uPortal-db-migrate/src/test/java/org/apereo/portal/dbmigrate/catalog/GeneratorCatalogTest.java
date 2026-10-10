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
package org.apereo.portal.dbmigrate.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.apereo.portal.dbmigrate.plan.SchemaProfile;
import org.apereo.portal.dbmigrate.rules.RuleSet;
import org.junit.Test;

/** The catalog generated from the entity annotations at build time. */
public class GeneratorCatalogTest {

    private final GeneratorCatalog catalog = SchemaProfile.load("5.17").generators();

    @Test
    public void hasEveryDatabaseBackedGenerator() {
        // 51 generated ids in 5.17: 49 sequences (or emulation tables) and 2 UUIDs (MOD-I23)
        assertEquals(49, catalog.size());
    }

    @Test
    public void mapsGeneratorsToIdColumnsWithAllocationSize() {
        GeneratorCatalog.Generator raw = catalog.generator("up_raw_events_seq");
        assertNotNull(raw);
        assertEquals(1000, raw.allocationSize());
        assertEquals(Collections.singletonList("UP_RAW_EVENTS.EVENT_ID"), raw.serves());
        assertEquals(10, catalog.generator("UP_PORTLET_PREF_SEQ").allocationSize());
        assertEquals(1, catalog.generator("UP_DLM_EVALUATOR_SEQ").allocationSize());
        assertEquals(
                Collections.singletonList("UP_PERSON_ATTR.ID"),
                catalog.generator("UP_PERSON_ATTR_SEQ").serves());
        assertTrue(
                catalog.generator("UP_PORTLET_EXEC_AGGR_SEQ")
                        .entities()
                        .get(0)
                        .endsWith("PortletExecutionAggregationImpl"));
    }

    @Test
    public void rulesAddGenerators() {
        GeneratorCatalog c =
                GeneratorCatalog.empty()
                        .addRules(
                                RuleSet.parse(
                                        "generator hibernate_sequence allocation=1 serves=A.ID,B.ID",
                                        "t"));
        assertEquals(2, c.generator("HIBERNATE_SEQUENCE").serves().size());
    }

    @Test(expected = IllegalStateException.class)
    public void conflictingAllocationSizesAreRejected() {
        GeneratorCatalog.empty().add("G", 5, "A.ID", null).add("G", 10, "B.ID", null);
    }
}
