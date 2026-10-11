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
package org.apereo.portal.dbmigrate;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apereo.portal.dbmigrate.it.DbServer;
import org.apereo.portal.dbmigrate.it.PropColumn;
import org.apereo.portal.dbmigrate.it.PropValues;

/**
 * Every kind of value the copy tool handles, copied between every pair of the run's servers ({@code
 * -PdbMigrateVendors}; HSQLDB is always a source), must arrive exactly, or converted only as
 * documented and reported (see {@link PairCopy} for the rules): integers of each width,
 * DECIMAL(38,10), doubles, booleans, CHAR, VARCHAR of 1, 255 and 4000, CLOB / text, BLOB / bytea,
 * PostgreSQL large objects (OID columns and 5.17's OIDs in text), dates, times, timestamps with 0,
 * 3 and 6 fractional digits, 5.17's own timestamp column, timestamps with time zone and uuid.
 *
 * <p>A push run tries a few row sets with a fixed seed; the nightly run tries ten times as many
 * with a random seed (see {@link PropertySettings}).
 */
@AddLifecycleHook(PropertySettings.class)
class CopyTypeProperties {

    /** Large values stay moderate on push; nightly runs raise them. */
    private static final int MAX_LOB = Integer.getInteger("dbmigrate.pbt.maxLob", 20_000);

    private final PropValues values = new PropValues(DbServer.enabled(), MAX_LOB);

    @Property(tries = 6, shrinking = ShrinkingMode.BOUNDED)
    void everyValueArrivesExactlyOrAsDocumented(@ForAll("rows") List<Map<PropColumn, Object>> rows)
            throws Exception {
        check(rows);
    }

    /** Each listed edge case at least once, on every run. */
    @Example
    void everyEdgeCaseArrivesExactlyOrAsDocumented() throws Exception {
        check(values.edgeRows());
    }

    @Provide
    Arbitrary<List<Map<PropColumn, Object>>> rows() {
        return values.rows(8);
    }

    private static void check(List<Map<PropColumn, Object>> rows) throws Exception {
        List<String> problems = new ArrayList<>();
        for (PairCopy pair : PairCopy.all(DbServer.enabled())) {
            problems.addAll(pair.check(rows));
        }
        // Every pair's one-line problems first, the tools' outputs after them
        problems.sort(java.util.Comparator.comparing(p -> p.contains("\n")));
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }
}
