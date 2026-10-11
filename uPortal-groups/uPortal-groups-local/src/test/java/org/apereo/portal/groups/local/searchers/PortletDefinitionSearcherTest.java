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
package org.apereo.portal.groups.local.searchers;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apereo.portal.groups.IGroupConstants.SearchMethod;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao.SearchMatch;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 * Each search method maps to a portlet search (MOD-I203). The _CI methods used to throw "Unknown
 * search type", so the entity selector's portlet search (CONTAINS_CI) answered 500.
 */
public class PortletDefinitionSearcherTest {
    private IPortletDefinitionRegistry registry;
    private PortletDefinitionSearcher searcher;

    @Before
    public void setUp() {
        registry = mock(IPortletDefinitionRegistry.class);
        when(registry.searchForPortlets(anyString(), any(SearchMatch.class)))
                .thenReturn(Collections.emptyList());
        searcher = new PortletDefinitionSearcher();
        searcher.setPortletDefinitionRegistry(registry);
    }

    private void assertMapsTo(SearchMethod method, SearchMatch match) throws Exception {
        assertEquals(0, searcher.searchForEntities("oOKMARK", method).length);
        verify(registry).searchForPortlets("oOKMARK", match);
    }

    @Test
    public void discreteIsCaseSensitiveExact() throws Exception {
        assertMapsTo(SearchMethod.DISCRETE, SearchMatch.EXACT);
    }

    @Test
    public void discreteCiIsExactIgnoringCase() throws Exception {
        assertMapsTo(SearchMethod.DISCRETE_CI, SearchMatch.EXACT_IGNORE_CASE);
    }

    @Test
    public void startsWith() throws Exception {
        assertMapsTo(SearchMethod.STARTS_WITH, SearchMatch.STARTS_WITH);
    }

    @Test
    public void startsWithCi() throws Exception {
        assertMapsTo(SearchMethod.STARTS_WITH_CI, SearchMatch.STARTS_WITH);
    }

    @Test
    public void endsWith() throws Exception {
        assertMapsTo(SearchMethod.ENDS_WITH, SearchMatch.ENDS_WITH);
    }

    @Test
    public void endsWithCi() throws Exception {
        assertMapsTo(SearchMethod.ENDS_WITH_CI, SearchMatch.ENDS_WITH);
    }

    @Test
    public void contains() throws Exception {
        assertMapsTo(SearchMethod.CONTAINS, SearchMatch.CONTAINS);
    }

    @Test
    public void containsCi() throws Exception {
        assertMapsTo(SearchMethod.CONTAINS_CI, SearchMatch.CONTAINS);
    }
}
