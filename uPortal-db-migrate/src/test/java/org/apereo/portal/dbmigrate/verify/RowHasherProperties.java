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
package org.apereo.portal.dbmigrate.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apereo.portal.dbmigrate.PropertySettings;

/**
 * The table hash the verification compares: it must not depend on row order, and any one change a
 * corrupted copy can show (a value, a missing, extra or duplicated row, values moved between
 * columns or rows, NULL versus empty) must change it.
 */
@AddLifecycleHook(PropertySettings.class)
class RowHasherProperties {

    /** Tables of 1..20 rows of 3 columns; values are short byte strings or NULL. */
    @Provide
    Arbitrary<List<byte[][]>> tables() {
        Arbitrary<byte[]> value =
                Arbitraries.bytes().array(byte[].class).ofMaxSize(6).injectNull(0.15);
        return value.array(byte[][].class).ofSize(3).list().ofMinSize(1).ofMaxSize(20);
    }

    private static String hash(List<byte[][]> rows) {
        RowHasher h = new RowHasher();
        rows.forEach(h::add);
        return h.value() + "/" + h.rows();
    }

    private static List<byte[][]> copy(List<byte[][]> rows) {
        List<byte[][]> out = new ArrayList<>();
        for (byte[][] r : rows) {
            byte[][] c = new byte[r.length][];
            for (int i = 0; i < r.length; i++) {
                c[i] = r[i] == null ? null : r[i].clone();
            }
            out.add(c);
        }
        return out;
    }

    @Property(tries = 300)
    void rowOrderDoesNotMatter(@ForAll("tables") List<byte[][]> rows, @ForAll long seed) {
        List<byte[][]> shuffled = copy(rows);
        Collections.shuffle(shuffled, new Random(seed));
        assertEquals(hash(rows), hash(shuffled));
    }

    @Property(tries = 300)
    void oneChangedValueChangesTheHash(
            @ForAll("tables") List<byte[][]> rows,
            @ForAll int row,
            @ForAll int column,
            @ForAll byte change) {
        List<byte[][]> changed = copy(rows);
        byte[][] r = changed.get(Math.floorMod(row, changed.size()));
        int c = Math.floorMod(column, r.length);
        if (r[c] == null) {
            r[c] = new byte[0];
        } else if (r[c].length == 0 || change == 0) {
            r[c] = null;
        } else {
            r[c][Math.floorMod(change, r[c].length)] ^= change;
        }
        assertNotEquals(hash(rows), hash(changed));
    }

    @Property(tries = 200)
    void aMissingExtraOrDuplicatedRowChangesTheHash(
            @ForAll("tables") List<byte[][]> rows, @ForAll int index) {
        int i = Math.floorMod(index, rows.size());
        List<byte[][]> missing = copy(rows);
        missing.remove(i);
        List<byte[][]> duplicated = copy(rows);
        duplicated.add(copy(rows).get(i));
        assertNotEquals(hash(rows), hash(missing));
        assertNotEquals(hash(rows), hash(duplicated));
    }

    @Property(tries = 200)
    void valuesMovedBetweenColumnsOrRowsChangeTheHash(
            @ForAll("tables") List<byte[][]> rows, @ForAll int index) {
        byte[][] r = rows.get(Math.floorMod(index, rows.size()));
        if (!java.util.Arrays.equals(r[0], r[1])) {
            List<byte[][]> swapped = copy(rows);
            byte[][] s = swapped.get(Math.floorMod(index, rows.size()));
            byte[] t = s[0];
            s[0] = s[1];
            s[1] = t;
            assertNotEquals(hash(rows), hash(swapped));
        }
        if (rows.size() > 1) {
            int a = Math.floorMod(index, rows.size());
            int b = (a + 1) % rows.size();
            if (!java.util.Arrays.equals(rows.get(a)[2], rows.get(b)[2])
                    && !java.util.Arrays.deepEquals(rows.get(a), rows.get(b))) {
                // The same multiset of values per column, but paired differently
                List<byte[][]> crossed = copy(rows);
                byte[] t = crossed.get(a)[2];
                crossed.get(a)[2] = crossed.get(b)[2];
                crossed.get(b)[2] = t;
                if (!java.util.Arrays.deepEquals(crossed.get(a), rows.get(b))) {
                    assertNotEquals(hash(rows), hash(crossed));
                }
            }
        }
    }

    @Property(tries = 100)
    void valueBoundariesAreUnambiguous(@ForAll("tables") List<byte[][]> rows) {
        byte[] ab = "ab".getBytes(StandardCharsets.UTF_8);
        byte[] c = "c".getBytes(StandardCharsets.UTF_8);
        byte[] a = "a".getBytes(StandardCharsets.UTF_8);
        byte[] bc = "bc".getBytes(StandardCharsets.UTF_8);
        List<byte[][]> one = copy(rows);
        one.add(new byte[][] {ab, c, null});
        List<byte[][]> two = copy(rows);
        two.add(new byte[][] {a, bc, null});
        assertNotEquals(hash(one), hash(two));
        // NULL is not the empty value, nor the bytes of the NULL marker
        List<byte[][]> nul = copy(rows);
        nul.add(new byte[][] {null, null, null});
        List<byte[][]> empty = copy(rows);
        empty.add(new byte[][] {new byte[0], null, null});
        List<byte[][]> marker = copy(rows);
        marker.add(new byte[][] {new byte[] {(byte) 0xFF, 'N', 'U', 'L', 'L'}, null, null});
        assertNotEquals(hash(nul), hash(empty));
        assertNotEquals(hash(nul), hash(marker));
    }
}
