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

import com.google.common.base.Function;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;
import org.apache.commons.lang.Validate;
import org.apereo.portal.hibernate.DelegatingHibernateIntegrator.HibernateConfiguration;
import org.apereo.portal.hibernate.HibernateConfigurationAware;
import org.apereo.portal.jpa.BasePortalJpaDao;
import org.apereo.portal.jpa.OpenEntityManager;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionId;
import org.hibernate.dialect.SQLServerDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Repository;

/** JPA implementation of the portlet definition DAO */
@Repository
public class JpaPortletDefinitionDao extends BasePortalJpaDao
        implements IPortletDefinitionDao, HibernateConfigurationAware {

    private CriteriaQuery<PortletDefinitionImpl> findAllPortletDefinitions;
    private CriteriaQuery<PortletDefinitionImpl> findDefinitionByNameQuery;
    private CriteriaQuery<PortletDefinitionImpl> findDefinitionByNameOrTitleQuery;
    private CriteriaQuery<PortletDefinitionImpl> searchDefinitionByNameOrTitleQuery;
    private ParameterExpression<String> nameParameter;
    private ParameterExpression<String> titleParameter;

    /**
     * The escape character of the partial searches. HSQLDB and Oracle reject an escape character
     * followed by anything but {@code %}, {@code _} or itself, so only those are escaped, and
     * {@code [} on SQL Server.
     */
    static final char LIKE_ESCAPE = '!';

    /** SQL Server reads {@code [} in a LIKE pattern as the start of a character class (MOD-I223) */
    private volatile boolean escapeBracket;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean supports(String persistenceUnit) {
        return PERSISTENCE_UNIT_NAME.equals(persistenceUnit);
    }

    @Override
    public void setConfiguration(
            String persistenceUnit, HibernateConfiguration hibernateConfiguration) {
        this.escapeBracket = hibernateConfiguration.getDialect() instanceof SQLServerDialect;
    }

    /** The LIKE pattern for {@code term} on this persistence unit's database */
    String likePattern(String term, SearchMatch match) {
        return likePattern(term, match, escapeBracket);
    }

    /**
     * The LIKE pattern, escaped with {@link #LIKE_ESCAPE}, that finds {@code term} literally. On
     * SQL Server ({@code escapeBracket}) {@code [} is escaped too.
     */
    static String likePattern(String term, SearchMatch match, boolean escapeBracket) {
        final StringBuilder pattern = new StringBuilder(term.length() + 2);
        if (match == SearchMatch.ENDS_WITH || match == SearchMatch.CONTAINS) {
            pattern.append('%');
        }
        for (int i = 0; i < term.length(); i++) {
            final char c = term.charAt(i);
            if (c == '%' || c == '_' || c == LIKE_ESCAPE || (escapeBracket && c == '[')) {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(c);
        }
        if (match == SearchMatch.STARTS_WITH || match == SearchMatch.CONTAINS) {
            pattern.append('%');
        }
        return pattern.toString();
    }

    @Override
    public void afterPropertiesSet() {
        this.nameParameter = this.createParameterExpression(String.class, "name");
        this.titleParameter = this.createParameterExpression(String.class, "title");

        this.findAllPortletDefinitions =
                this.createCriteriaQuery(
                        new Function<CriteriaBuilder, CriteriaQuery<PortletDefinitionImpl>>() {
                            @Override
                            public CriteriaQuery<PortletDefinitionImpl> apply(CriteriaBuilder cb) {
                                final CriteriaQuery<PortletDefinitionImpl> criteriaQuery =
                                        cb.createQuery(PortletDefinitionImpl.class);
                                final Root<PortletDefinitionImpl> definitionRoot =
                                        criteriaQuery.from(PortletDefinitionImpl.class);
                                criteriaQuery.select(definitionRoot);

                                return criteriaQuery;
                            }
                        });

        this.findDefinitionByNameQuery =
                this.createCriteriaQuery(
                        new Function<CriteriaBuilder, CriteriaQuery<PortletDefinitionImpl>>() {
                            @Override
                            public CriteriaQuery<PortletDefinitionImpl> apply(CriteriaBuilder cb) {
                                final CriteriaQuery<PortletDefinitionImpl> criteriaQuery =
                                        cb.createQuery(PortletDefinitionImpl.class);
                                final Root<PortletDefinitionImpl> definitionRoot =
                                        criteriaQuery.from(PortletDefinitionImpl.class);
                                criteriaQuery.select(definitionRoot);
                                criteriaQuery.where(
                                        cb.equal(
                                                definitionRoot.get(PortletDefinitionImpl_.name),
                                                nameParameter));

                                return criteriaQuery;
                            }
                        });

        this.findDefinitionByNameOrTitleQuery =
                this.createCriteriaQuery(
                        new Function<CriteriaBuilder, CriteriaQuery<PortletDefinitionImpl>>() {
                            @Override
                            public CriteriaQuery<PortletDefinitionImpl> apply(CriteriaBuilder cb) {
                                final CriteriaQuery<PortletDefinitionImpl> criteriaQuery =
                                        cb.createQuery(PortletDefinitionImpl.class);
                                final Root<PortletDefinitionImpl> definitionRoot =
                                        criteriaQuery.from(PortletDefinitionImpl.class);
                                criteriaQuery.select(definitionRoot);
                                // Ignores case on every database; exactMatches then keeps
                                // the rows equal to the term
                                criteriaQuery.where(
                                        cb.or(
                                                cb.equal(
                                                        cb.upper(
                                                                definitionRoot.get(
                                                                        PortletDefinitionImpl_
                                                                                .name)),
                                                        cb.upper(nameParameter)),
                                                cb.equal(
                                                        cb.upper(
                                                                definitionRoot.get(
                                                                        PortletDefinitionImpl_
                                                                                .title)),
                                                        cb.upper(titleParameter))));

                                return criteriaQuery;
                            }
                        });

        this.searchDefinitionByNameOrTitleQuery =
                this.createCriteriaQuery(
                        new Function<CriteriaBuilder, CriteriaQuery<PortletDefinitionImpl>>() {
                            @Override
                            public CriteriaQuery<PortletDefinitionImpl> apply(CriteriaBuilder cb) {
                                final CriteriaQuery<PortletDefinitionImpl> criteriaQuery =
                                        cb.createQuery(PortletDefinitionImpl.class);
                                final Root<PortletDefinitionImpl> definitionRoot =
                                        criteriaQuery.from(PortletDefinitionImpl.class);
                                criteriaQuery.select(definitionRoot);
                                criteriaQuery.where(
                                        cb.or(
                                                cb.like(
                                                        cb.upper(
                                                                definitionRoot.get(
                                                                        PortletDefinitionImpl_
                                                                                .name)),
                                                        cb.upper(nameParameter),
                                                        LIKE_ESCAPE),
                                                cb.like(
                                                        cb.upper(
                                                                definitionRoot.get(
                                                                        PortletDefinitionImpl_
                                                                                .title)),
                                                        cb.upper(titleParameter),
                                                        LIKE_ESCAPE)));

                                return criteriaQuery;
                            }
                        });
    }

    @Override
    @OpenEntityManager(unitName = PERSISTENCE_UNIT_NAME)
    public IPortletDefinition getPortletDefinition(IPortletDefinitionId portletDefinitionId) {
        Validate.notNull(portletDefinitionId, "portletDefinitionId can not be null");

        final long internalPortletDefinitionId = getNativePortletDefinitionId(portletDefinitionId);

        return this.getEntityManager()
                .find(PortletDefinitionImpl.class, internalPortletDefinitionId);
    }

    @Override
    @OpenEntityManager(unitName = PERSISTENCE_UNIT_NAME)
    public IPortletDefinition getPortletDefinition(String portletDefinitionIdString) {
        Validate.notNull(portletDefinitionIdString, "portletDefinitionIdString can not be null");

        PortletDefinitionImpl result = null; // default

        final Long internalPortletDefinitionId =
                getNativePortletDefinitionId(portletDefinitionIdString);
        if (internalPortletDefinitionId != null) {
            result =
                    this.getEntityManager()
                            .find(PortletDefinitionImpl.class, internalPortletDefinitionId);
        }

        return result;
    }

    @Override
    @OpenEntityManager(unitName = PERSISTENCE_UNIT_NAME)
    public IPortletDefinition getPortletDefinitionByFname(String fname) {
        final NaturalIdQuery<PortletDefinitionImpl> query =
                this.createNaturalIdQuery(PortletDefinitionImpl.class);
        query.using(PortletDefinitionImpl_.fname, fname);
        return query.load();
    }

    @Override
    @OpenEntityManager(unitName = PERSISTENCE_UNIT_NAME)
    public IPortletDefinition getPortletDefinitionByName(String name) {
        final TypedQuery<PortletDefinitionImpl> query =
                this.createCachedQuery(this.findDefinitionByNameQuery);
        query.setParameter(this.nameParameter, name);

        final List<PortletDefinitionImpl> portletDefinitions = query.getResultList();
        return DataAccessUtils.uniqueResult(portletDefinitions);
    }

    @Override
    @OpenEntityManager(unitName = PERSISTENCE_UNIT_NAME)
    public List<IPortletDefinition> searchForPortlets(String term, SearchMatch match) {
        final CriteriaQuery<PortletDefinitionImpl> criteriaQuery;
        if (match == SearchMatch.EXACT || match == SearchMatch.EXACT_IGNORE_CASE) {
            criteriaQuery = this.findDefinitionByNameOrTitleQuery;
        } else {
            criteriaQuery = this.searchDefinitionByNameOrTitleQuery;
            term = likePattern(term, match);
        }

        final TypedQuery<PortletDefinitionImpl> query = this.createCachedQuery(criteriaQuery);
        query.setParameter("name", term);
        query.setParameter("title", term);

        final List<PortletDefinitionImpl> portletDefinitions = query.getResultList();
        if (match == SearchMatch.EXACT) {
            return exactMatches(portletDefinitions, term);
        }
        return new ArrayList<>(portletDefinitions);
    }

    /**
     * The rows whose name or title equals {@code term}, case and trailing spaces included. MySQL,
     * MariaDB and SQL Server compare strings ignoring case (and MariaDB and SQL Server ignoring
     * trailing spaces) through their collations, so the database can't decide this the same way
     * everywhere (MOD-I142).
     */
    static List<IPortletDefinition> exactMatches(
            List<? extends IPortletDefinition> rows, String term) {
        final List<IPortletDefinition> result = new ArrayList<>();
        for (IPortletDefinition row : rows) {
            if (term.equals(row.getName()) || term.equals(row.getTitle())) {
                result.add(row);
            }
        }
        return result;
    }

    @Override
    @PortalTransactional
    public void deletePortletDefinition(IPortletDefinition definition) {
        Validate.notNull(definition, "definition can not be null");

        final IPortletDefinition persistentPortletDefinition;
        final EntityManager entityManager = this.getEntityManager();
        if (entityManager.contains(definition)) {
            persistentPortletDefinition = definition;
        } else {
            persistentPortletDefinition = entityManager.merge(definition);
        }

        entityManager.remove(persistentPortletDefinition);
    }

    @Override
    @OpenEntityManager(unitName = PERSISTENCE_UNIT_NAME)
    public List<IPortletDefinition> getPortletDefinitions() {
        final TypedQuery<PortletDefinitionImpl> query =
                this.createCachedQuery(this.findAllPortletDefinitions);

        final List<PortletDefinitionImpl> portletDefinitions = query.getResultList();
        return new ArrayList<>(new LinkedHashSet<IPortletDefinition>(portletDefinitions));
    }

    @Override
    @PortalTransactional
    public IPortletDefinition savePortletDefinition(IPortletDefinition portletDefinition) {
        Validate.notNull(portletDefinition, "portletDefinition can not be null");
        Validate.notNull(
                portletDefinition.getType(), "portletDefinition portlet type can not be null");
        Validate.notEmpty(portletDefinition.getFName(), "portletDefinition fname can not be null");
        Validate.notEmpty(portletDefinition.getName(), "portletDefinition name can not be null");
        Validate.notEmpty(portletDefinition.getTitle(), "portletDefinition title can not be null");
        this.getEntityManager().persist(portletDefinition);
        return portletDefinition;
    }

    @Override
    @PortalTransactional
    public IPortletDefinition mergePortletDefinition(IPortletDefinition portletDefinition) {
        Validate.notNull(portletDefinition, "portletDefinition can not be null");
        Validate.notNull(
                portletDefinition.getType(), "portletDefinition portlet type can not be null");
        Validate.notEmpty(portletDefinition.getFName(), "portletDefinition fname can not be null");
        Validate.notEmpty(portletDefinition.getName(), "portletDefinition name can not be null");
        Validate.notEmpty(portletDefinition.getTitle(), "portletDefinition title can not be null");
        this.getEntityManager().merge(portletDefinition);
        return portletDefinition;
    }

    private long getNativePortletDefinitionId(IPortletDefinitionId portletDefinitionId) {
        return Long.parseLong(portletDefinitionId.getStringId());
    }

    private Long getNativePortletDefinitionId(String portletDefinitionId) {
        Long result = null; // default
        try {
            result = Long.parseLong(portletDefinitionId);
        } catch (NumberFormatException nfe) {
            logger.warn(
                    "The portletDefinitionId '{}' is not parsable to a valid portletId (long);  null will be returned",
                    portletDefinitionId);
        }
        return result;
    }
}
