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

import java.util.Arrays;
import java.util.Collection;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao.SearchMatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** The LIKE patterns of the partial portlet searches escape every wildcard of the term */
@RunWith(Parameterized.class)
public class JpaPortletDefinitionDaoLikePatternTest {
    @Parameters(name = "{1} \"{0}\" -> {2}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(
                new Object[][] {
                    {"cal", SearchMatch.CONTAINS, "%cal%"},
                    {"MiXeD", SearchMatch.CONTAINS, "%MiXeD%"},
                    {"cal", SearchMatch.STARTS_WITH, "cal%"},
                    {"cal", SearchMatch.ENDS_WITH, "%cal"},
                    {"", SearchMatch.CONTAINS, "%%"},
                    {"%", SearchMatch.CONTAINS, "%!%%"},
                    {"_", SearchMatch.STARTS_WITH, "!_%"},
                    {"!", SearchMatch.ENDS_WITH, "%!!"},
                    {"100%_Done!", SearchMatch.CONTAINS, "%100!%!_Done!!%"},
                    {"!%!_", SearchMatch.STARTS_WITH, "!!!%!!!_%"},
                    {"a\\b[c]", SearchMatch.CONTAINS, "%a\\b[c]%"},
                });
    }

    private final String term;
    private final SearchMatch match;
    private final String pattern;

    public JpaPortletDefinitionDaoLikePatternTest(String term, SearchMatch match, String pattern) {
        this.term = term;
        this.match = match;
        this.pattern = pattern;
    }

    @Test
    public void escapesTheWildcardsAndTheEscapeCharacter() {
        assertEquals(pattern, JpaPortletDefinitionDao.likePattern(term, match));
    }
}
