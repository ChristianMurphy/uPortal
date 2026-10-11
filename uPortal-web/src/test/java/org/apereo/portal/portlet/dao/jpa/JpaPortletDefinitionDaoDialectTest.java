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

import org.apereo.portal.hibernate.DelegatingHibernateIntegrator.HibernateConfiguration;
import org.apereo.portal.jpa.BasePortalJpaDao;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao.SearchMatch;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.SQLServer2005Dialect;
import org.junit.Test;

/** The partial searches escape [ on SQL Server only, where it starts a character class */
public class JpaPortletDefinitionDaoDialectTest {

    private static JpaPortletDefinitionDao on(Dialect dialect) {
        final HibernateConfiguration configuration = mock(HibernateConfiguration.class);
        when(configuration.getDialect()).thenReturn(dialect);
        final JpaPortletDefinitionDao dao = new JpaPortletDefinitionDao();
        dao.setConfiguration(BasePortalJpaDao.PERSISTENCE_UNIT_NAME, configuration);
        return dao;
    }

    @Test
    public void escapesTheBracketOnSqlServer() {
        assertEquals(
                "%![x]%", on(new SQLServer2005Dialect()).likePattern("[x]", SearchMatch.CONTAINS));
    }

    @Test
    public void leavesTheBracketAloneElsewhere() {
        assertEquals("%[x]%", on(new HSQLDialect()).likePattern("[x]", SearchMatch.CONTAINS));
    }

    @Test
    public void caresAboutThePortalDatabaseOnly() {
        final JpaPortletDefinitionDao dao = new JpaPortletDefinitionDao();
        assertEquals(true, dao.supports("PortalDb"));
        assertEquals(false, dao.supports("AggrEventsDb"));
    }
}
