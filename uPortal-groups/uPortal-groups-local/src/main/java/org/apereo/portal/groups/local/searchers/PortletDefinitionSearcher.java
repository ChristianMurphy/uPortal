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

import java.util.List;
import java.util.ListIterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apereo.portal.EntityIdentifier;
import org.apereo.portal.IBasicEntity;
import org.apereo.portal.groups.GroupsException;
import org.apereo.portal.groups.local.ITypedEntitySearcher;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao.SearchMatch;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Searches the portal DB for channels. Used by EntitySearcherImpl */
@Service
public class PortletDefinitionSearcher implements ITypedEntitySearcher {
    private static final Log log = LogFactory.getLog(PortletDefinitionSearcher.class);

    private IPortletDefinitionRegistry portletDefinitionRegistry;

    @Autowired
    public void setPortletDefinitionRegistry(IPortletDefinitionRegistry portletDefinitionRegistry) {
        this.portletDefinitionRegistry = portletDefinitionRegistry;
    }

    /**
     * The partial searches ignore case whatever the method (MOD-I142), so each _CI method maps to
     * the same search as its plain one. DISCRETE is case-sensitive and DISCRETE_CI isn't
     * (MOD-I203).
     */
    @Override
    public EntityIdentifier[] searchForEntities(String query, SearchMethod method)
            throws GroupsException {
        final SearchMatch match;
        switch (method) {
            case DISCRETE:
                match = SearchMatch.EXACT;
                break;
            case DISCRETE_CI:
                match = SearchMatch.EXACT_IGNORE_CASE;
                break;
            case STARTS_WITH:
            case STARTS_WITH_CI:
                match = SearchMatch.STARTS_WITH;
                break;
            case ENDS_WITH:
            case ENDS_WITH_CI:
                match = SearchMatch.ENDS_WITH;
                break;
            case CONTAINS:
            case CONTAINS_CI:
                match = SearchMatch.CONTAINS;
                break;
            default:
                throw new GroupsException("Unknown search type");
        }

        // get the list of matching portlet definitions
        final List<IPortletDefinition> definitions =
                this.portletDefinitionRegistry.searchForPortlets(query, match);
        if (log.isDebugEnabled()) {
            log.debug("Found " + definitions.size() + " matching definitions for query " + query);
        }

        // initialize an appropriately-sized array of EntityIdentifiers
        final EntityIdentifier[] identifiers = new EntityIdentifier[definitions.size()];

        // add an identifier for each matching portlet
        for (final ListIterator<IPortletDefinition> defIter = definitions.listIterator();
                defIter.hasNext(); ) {
            final IPortletDefinition definition = defIter.next();
            identifiers[defIter.previousIndex()] =
                    new EntityIdentifier(
                            definition.getPortletDefinitionId().getStringId(), this.getType());
        }

        return identifiers;
    }

    @Override
    public Class<? extends IBasicEntity> getType() {
        return IPortletDefinition.class;
    }
}
