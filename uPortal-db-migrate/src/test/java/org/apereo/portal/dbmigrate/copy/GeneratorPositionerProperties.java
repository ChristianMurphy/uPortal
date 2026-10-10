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
package org.apereo.portal.dbmigrate.copy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apereo.portal.dbmigrate.PropertySettings;

/**
 * Generator positions: whatever the copied ids, the source generator and the target's own position,
 * the next value the tool sets must hand out only new ids under each of Hibernate's optimizers
 * (MOD-I72), never move a generator back, and be no higher than that requires.
 */
@AddLifecycleHook(PropertySettings.class)
class GeneratorPositionerProperties {

    @Provide
    Arbitrary<Long> ids() {
        return Arbitraries.longs().between(1, 1L << 40).injectNull(0.1);
    }

    @Provide
    Arbitrary<Integer> allocationSizes() {
        return Arbitraries.of(1, 5, 10, 50, 100, 1000, 10000);
    }

    @Provide
    Arbitrary<Long> positions() {
        return Arbitraries.longs().between(1, 1L << 41).injectNull(0.3);
    }

    @Property(tries = 1000)
    void theNextValueHandsOutOnlyNewIds(
            @ForAll("ids") Long maxId,
            @ForAll("allocationSizes") int allocation,
            @ForAll("positions") Long sourceNext,
            @ForAll("positions") Long targetNext) {
        long next = GeneratorPositioner.position(maxId, allocation, sourceNext, targetNext);
        if (maxId != null) {
            // POOLED: the value v hands out v-allocation+1 .. v
            assertTrue(next - allocation + 1 > maxId);
            // POOLED_LO: v .. v+allocation-1; no optimizer: v
            assertTrue(next > maxId);
        }
        // The second block of each optimizer is higher still, so the first block decides
        if (sourceNext != null) {
            assertTrue("never below the source's position", next >= sourceNext);
        }
        if (targetNext != null) {
            assertTrue("never moved back", next >= targetNext);
        }
        long required = GeneratorPositioner.requiredNext(maxId, allocation);
        long least =
                Math.max(
                        required,
                        Math.max(
                                sourceNext == null ? Long.MIN_VALUE : sourceNext,
                                targetNext == null ? Long.MIN_VALUE : targetNext));
        assertEquals("no higher than needed", least, next);
    }

    @Property(tries = 200)
    void requiredNextIsTheLeastSafeValue(
            @ForAll("ids") Long maxId, @ForAll("allocationSizes") int allocation) {
        long required = GeneratorPositioner.requiredNext(maxId, allocation);
        if (maxId == null) {
            assertEquals(1, required);
        } else {
            // One lower and POOLED would hand out maxId again
            assertEquals(maxId.longValue(), required - allocation);
            assertTrue(required - 1 - allocation + 1 <= maxId);
        }
    }
}
