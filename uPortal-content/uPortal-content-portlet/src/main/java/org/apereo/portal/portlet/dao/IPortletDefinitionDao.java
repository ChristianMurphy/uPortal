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
package org.apereo.portal.portlet.dao;

import java.util.List;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionId;

/** Provides APIs for creating, storing and retrieving {@link IPortletDefinition} objects. */
public interface IPortletDefinitionDao {
    /**
     * Persists changes to a {@link IPortletDefinition}.
     *
     * @param portletDefinition The portlet definition to store the changes for
     * @throws IllegalArgumentException if portletDefinition is null.
     */
    IPortletDefinition savePortletDefinition(IPortletDefinition portletDefinition);

    IPortletDefinition mergePortletDefinition(IPortletDefinition portletDefinition);

    void deletePortletDefinition(IPortletDefinition definition);

    /**
     * Get a {@link IPortletDefinition} for the specified {@link IPortletDefinitionId}.
     *
     * @param portletDefinitionId The id to get the definition for.
     * @return The portlet definition for the id, null if no definition exists for the id.
     * @throws IllegalArgumentException if portletDefinitionId is null.
     */
    IPortletDefinition getPortletDefinition(IPortletDefinitionId portletDefinitionId);

    IPortletDefinition getPortletDefinition(String portletDefinitionIdString);

    IPortletDefinition getPortletDefinitionByFname(String fname);

    IPortletDefinition getPortletDefinitionByName(String name);

    List<IPortletDefinition> getPortletDefinitions();

    /** How {@link #searchForPortlets(String, SearchMatch)} compares a term with a name or title */
    enum SearchMatch {
        /** Equal to the term, case-sensitive on every database */
        EXACT,
        /** Equal to the term, ignoring case (MOD-I203) */
        EXACT_IGNORE_CASE,
        /** Starts with the term, ignoring case */
        STARTS_WITH,
        /** Ends with the term, ignoring case */
        ENDS_WITH,
        /** Contains the term, ignoring case */
        CONTAINS
    }

    /**
     * Same as {@link #searchForPortlets(String, SearchMatch)} with {@link SearchMatch#CONTAINS} if
     * {@code allowPartial} is true, and {@link SearchMatch#EXACT} if it is false.
     */
    default List<IPortletDefinition> searchForPortlets(String term, boolean allowPartial) {
        return searchForPortlets(term, allowPartial ? SearchMatch.CONTAINS : SearchMatch.EXACT);
    }

    /**
     * Finds the portlet definitions whose name or title matches {@code term}. Every character of
     * the term is literal: {@code %} and {@code _} are not wildcards.
     */
    List<IPortletDefinition> searchForPortlets(String term, SearchMatch match);
}
