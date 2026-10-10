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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The positioning rule against simulations of the Hibernate optimizers uPortal 5.17 uses: POOLED
 * for entity sequences (and their emulation tables), POOLED_LO for the {@code UP_SEQUENCE} counters
 * since 5.17.10 (MOD-I72), none when allocationSize is 1.
 */
public class GeneratorPositionerTest {

    /** Hibernate 4.3 PooledOptimizer, initialValue 1, against a sequence that next returns n. */
    static List<Long> pooled(long n, int increment, int count) {
        List<Long> ids = new ArrayList<>();
        long db = n;
        long value;
        long hi;
        long first = db;
        db += increment;
        if (first == 1) { // the call returned the initial value: take the next one as hi
            hi = db;
            db += increment;
            value = first;
        } else {
            hi = first;
            value = hi - increment + 1;
        }
        while (ids.size() < count) {
            if (value > hi) {
                hi = db;
                db += increment;
                value = hi - increment + 1;
            }
            ids.add(value++);
        }
        return ids;
    }

    /** PooledLoOptimizer: a database value v covers v .. v + increment - 1. */
    static List<Long> pooledLo(long n, int increment, int count) {
        List<Long> ids = new ArrayList<>();
        long db = n;
        while (ids.size() < count) {
            for (long v = db; v < db + increment && ids.size() < count; v++) {
                ids.add(v);
            }
            db += increment;
        }
        return ids;
    }

    @Test
    public void floorIsMaxIdPlusAllocationSize() {
        assertEquals(1, GeneratorPositioner.requiredNext(null, 50));
        assertEquals(21, GeneratorPositioner.requiredNext(20L, 1));
        assertEquals(1030 + 1000, GeneratorPositioner.requiredNext(1030L, 1000));
        assertEquals(6, GeneratorPositioner.requiredNext(5L, 0)); // unknown allocation counts as 1
    }

    @Test
    public void neverMovesBackwards() {
        // PostgreSQL source after the portal ran: max(EVENT_ID) 7712, last_value 8001 (next 9001)
        assertEquals(9001, GeneratorPositioner.position(7712L, 1000, 9001L, 1L));
        assertEquals(8712, GeneratorPositioner.position(7712L, 1000, 8001L, 1L));
        assertEquals(9000, GeneratorPositioner.position(10L, 5, 3L, 9000L));
        assertEquals(15, GeneratorPositioner.position(10L, 5, null, null));
    }

    @Test
    public void noOptimizerCanCollideAfterPositioning() {
        int[] allocations = {1, 5, 10, 50, 100, 1000, 10000};
        long[] maxIds = {1, 2, 9, 10, 11, 93, 7712, 123_456};
        for (int a : allocations) {
            for (long max : maxIds) {
                long n = GeneratorPositioner.position(max, a, null, 1L);
                for (long id : pooled(n, a, 3 * a)) {
                    assertTrue("POOLED alloc " + a + " max " + max + " gave " + id, id > max);
                }
                for (long id : pooledLo(n, a, 3 * a)) {
                    assertTrue("POOLED_LO alloc " + a + " max " + max + " gave " + id, id > max);
                }
            }
        }
    }

    @Test
    public void simulationMatchesTheHarnessSnapshot() {
        // postgres-16 after dataInit: UP_AGGR_GROUP_MAPPING_SEQ increment 10, last_value 11,
        // rows 1 and 2; the next block is 12..21
        assertEquals(1L, (long) pooled(1, 10, 1).get(0));
        assertEquals(12L, (long) pooled(21, 10, 1).get(0));
    }

    @Test
    public void justBelowTheFloorWouldCollide() {
        long max = 100;
        int a = 10;
        long tooLow = GeneratorPositioner.requiredNext(max, a) - 1;
        assertTrue(pooled(tooLow, a, 1).get(0) <= max);
    }
}
