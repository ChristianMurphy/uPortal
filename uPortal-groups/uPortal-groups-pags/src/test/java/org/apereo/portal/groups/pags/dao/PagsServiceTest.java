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
package org.apereo.portal.groups.pags.dao;

import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * PAGS group names and descriptions are text people read, in any script (MOD-D49). A name is also
 * the key in the PAGS REST paths, so it can't hold / or \, which Tomcat refuses in a path.
 */
public class PagsServiceTest {

    private static void refused(Runnable check, String value) {
        try {
            check.run();
            fail("Accepted \"" + value + "\"");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void groupNamesAcceptAnyText() {
        for (final String name :
                new String[] {
                    "Students",
                    "\u00c9tudiants de l'Universit\u00e9",
                    "Faculty & Staff (2026)",
                    "\u674e\u5c0f\u9f8d\u7684\u670b\u53cb",
                    "Zo\u00eb \u00d1and\u00fa \u674e\u5c0f\u9f8d \ud83d\ude00",
                    new String(new char[500]).replace('\0', '\u00e9'),
                }) {
            PagsService.checkGroupName(name);
        }
    }

    @Test
    public void groupNamesRefuseSlashesControlsAndLengths() {
        for (final String name :
                new String[] {
                    "abcd",
                    "     ",
                    new String(new char[501]).replace('\0', '\u00e9'),
                    "Staff/Faculty",
                    "Staff\\Faculty",
                    "Two\nLines",
                }) {
            refused(() -> PagsService.checkGroupName(name), name);
        }
    }

    @Test
    public void descriptionsAcceptAnyText() {
        PagsService.checkDescription(null);
        PagsService.checkDescription("");
        PagsService.checkDescription(
                "Users who are members of (\u00c9tudiants, Faculty & Staff, \u674e\u5c0f\u9f8d)"
                        + " but are not members of (Alumni/Retired)");
        refused(() -> PagsService.checkDescription("Two\nLines"), "Two\nLines");
        final String tooLong = new String(new char[501]).replace('\0', 'x');
        refused(() -> PagsService.checkDescription(tooLong), tooLong);
    }
}
