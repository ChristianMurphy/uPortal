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
package org.apereo.portal.utils.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Analytics event types fit the 200-character EVENT_TYPE column (MOD-I272) */
public class InputValidatorTest {

    private static String repeat(char c, int times) {
        return new String(new char[times]).replace('\0', c);
    }

    @Test
    public void acceptsEventTypesUpToTheColumnSize() {
        assertEquals(
                "page-view", InputValidator.validateAsWordCharacters("page-view", "eventType"));
        final String longest = repeat('x', 200);
        assertEquals(longest, InputValidator.validateAsWordCharacters(longest, "eventType"));
    }

    @Test
    public void refusesLongerEventTypesAndSaysWhichCharacters() {
        for (final String eventType : new String[] {repeat('x', 201), "page view", "café"}) {
            try {
                InputValidator.validateAsWordCharacters(eventType, "eventType");
                fail("Accepted the event type \"" + eventType + "\"");
            } catch (IllegalArgumentException expected) {
                assertTrue(
                        expected.getMessage(),
                        expected.getMessage()
                                .contains("1 to 200 of the characters A-Z, a-z, 0-9, _ and -"));
            }
        }
    }
}
