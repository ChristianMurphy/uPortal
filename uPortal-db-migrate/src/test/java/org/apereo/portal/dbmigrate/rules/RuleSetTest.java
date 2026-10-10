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
package org.apereo.portal.dbmigrate.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;
import org.junit.Test;

public class RuleSetTest {

    private static final String RULES =
            String.join(
                    "\n",
                    "# comment",
                    "exclude      UP_RAW_*",
                    "rename-table up_old UP_NEW   # trailing comment",
                    "rename-column UP_USER.OLD_NAME NEW_NAME",
                    "skip-column  up_user.dropped",
                    "transform    UP_EVENT.WHEN timestamp-at-zone zone=America/Chicago",
                    "derive-column UP_USER.SOURCE constant value=migrated",
                    "lob-oid      UP_RAW_EVENTS.EVENT_DATA",
                    "generator    hibernate_sequence allocation=1 serves=A.ID,b.id",
                    "rename-generator UP_FOO_SEQ UP_FOO_ID_SEQ",
                    "");

    @Test
    public void parsesEveryDirectiveCaseInsensitively() {
        RuleSet r = RuleSet.parse(RULES, "test");
        assertTrue(r.isExcluded("up_raw_events"));
        assertFalse(r.isExcluded("UP_USER"));
        assertEquals("UP_NEW", r.targetTableName("UP_OLD"));
        assertEquals("UP_OTHER", r.targetTableName("UP_OTHER"));
        assertEquals("NEW_NAME", r.targetColumnName("up_user", "old_name"));
        assertEquals("KEPT", r.targetColumnName("UP_USER", "KEPT"));
        assertTrue(r.isSkipped("UP_USER", "DROPPED"));
        assertEquals("timestamp-at-zone", r.transform("up_event", "when").name());
        assertNull(r.transform("UP_EVENT", "OTHER"));
        assertEquals("constant", r.derivedColumns("UP_USER").get("SOURCE").name());
        assertTrue(r.isLobOid("up_raw_events", "event_data"));
        assertEquals(1, r.generators().size());
        assertEquals("[A.ID, B.ID]", r.generators().get(0).serves().toString());
        assertEquals("UP_FOO_ID_SEQ", r.targetGeneratorName("up_foo_seq"));
    }

    @Test
    public void reportsTheLineOfABadRule() {
        try {
            RuleSet.parse("exclude X\nrename-table ONLY_ONE\n", "my.rules");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(
                    e.getMessage(),
                    e.getMessage().startsWith("my.rules:2: expected: rename-table"));
        }
        try {
            RuleSet.parse("frobnicate X\n", "my.rules");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("unknown rule 'frobnicate'"));
        }
        try {
            RuleSet.parse("skip-column NO_DOT\n", "my.rules");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("TABLE.COLUMN"));
        }
    }

    @Test
    public void bindsBuiltInTransforms() {
        RuleSet r = RuleSet.parse(RULES, "test");
        ValueTransform.Function f = RuleSet.bind(r.transform("UP_EVENT", "WHEN"));
        assertEquals(Family.TIMESTAMP_TZ, f.resultFamily());
        OffsetDateTime odt =
                (OffsetDateTime)
                        f.apply(LocalDateTime.of(2026, 1, 15, 8, 0), Collections.emptyMap());
        assertEquals("2026-01-15T08:00-06:00", odt.toString());
        ValueTransform.Function c = RuleSet.bind(r.derivedColumns("UP_USER").get("SOURCE"));
        assertEquals("migrated", c.apply(null, Collections.emptyMap()));
        Map<String, Object> row = new HashMap<>();
        row.put("NAME", "x");
        ValueTransform.Function copy =
                RuleSet.bind(
                        RuleSet.parse("derive-column T.C copy-of column=name", "t")
                                .derivedColumns("T")
                                .get("C"));
        assertEquals("x", copy.apply(null, row));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownTransform() {
        RuleSet.bind(RuleSet.parse("transform T.C no-such-thing", "t").transform("T", "C"));
    }

    @Test
    public void theBuiltInProfileDeclaresTheEventLobColumns() {
        RuleSet r = RuleSet.fromResource("org/apereo/portal/dbmigrate/profiles/5.17/schema.rules");
        assertTrue(r.isLobOid("UP_RAW_EVENTS", "EVENT_DATA"));
        assertTrue(r.isLobOid("up_analytics_events", "event_data"));
        assertEquals(2, r.lobOidColumns().size());
    }
}
