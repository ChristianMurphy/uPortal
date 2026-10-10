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
package org.apereo.portal.events.aggr;

import static org.junit.Assert.fail;

import org.apereo.portal.spring.properties.TemporalAmountEditor;
import org.junit.Test;

/** The dimension buffer must still be at least one day. */
public class PortalEventDimensionPopulatorDimensionBufferTest {

    @Test
    public void acceptsADayOrMore() {
        final PortalEventDimensionPopulatorImpl populator = new PortalEventDimensionPopulatorImpl();
        for (final String text : new String[] {"P1D", "P30D", "PT24H", "PT36H", "P1W", "P1DT1H"}) {
            populator.setDimensionBuffer(TemporalAmountEditor.parse(text));
        }
    }

    @Test
    public void rejectsLessThanADay() {
        final PortalEventDimensionPopulatorImpl populator = new PortalEventDimensionPopulatorImpl();
        for (final String text : new String[] {"PT23H", "PT30S", "P0D", "P-1D"}) {
            try {
                populator.setDimensionBuffer(TemporalAmountEditor.parse(text));
                fail("Accepted " + text);
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }
}
