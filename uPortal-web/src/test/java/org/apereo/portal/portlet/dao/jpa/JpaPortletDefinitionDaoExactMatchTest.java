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
package org.apereo.portal.portlet.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.junit.Test;

/**
 * An exact match is case-sensitive on every database (MOD-I142): the query ignores case, which
 * works the same everywhere, and exactMatches keeps the rows equal to the term.
 */
public class JpaPortletDefinitionDaoExactMatchTest {

    private static IPortletDefinition portlet(String name, String title) {
        final IPortletDefinition portlet = mock(IPortletDefinition.class);
        when(portlet.getName()).thenReturn(name);
        when(portlet.getTitle()).thenReturn(title);
        when(portlet.getFName()).thenReturn(name.toLowerCase());
        return portlet;
    }

    private static List<String> fnames(String term, IPortletDefinition... rows) {
        final List<String> result = new ArrayList<>();
        for (IPortletDefinition p :
                JpaPortletDefinitionDao.exactMatches(Arrays.asList(rows), term)) {
            result.add(p.getFName());
        }
        return result;
    }

    @Test
    public void keepsRowsWhoseNameOrTitleEqualsTheTerm() {
        final IPortletDefinition calendar = portlet("Campus Calendar", "Calendar");
        final IPortletDefinition weather = portlet("Weather", "Campus Weather");
        assertEquals(Arrays.asList("campus calendar"), fnames("Calendar", calendar, weather));
        assertEquals(Arrays.asList("weather"), fnames("Weather", calendar, weather));
    }

    /** What MySQL, MariaDB and SQL Server return for "calendar" through their collations */
    @Test
    public void dropsRowsThatDifferOnlyInCase() {
        assertEquals(
                new ArrayList<String>(),
                fnames("calendar", portlet("Campus Calendar", "Calendar")));
    }

    /** What MariaDB and SQL Server return for "Weather" through PAD SPACE */
    @Test
    public void dropsRowsThatDifferInTrailingSpaces() {
        assertEquals(new ArrayList<String>(), fnames("Weather", portlet("Weather ", "Weather ")));
    }
}
