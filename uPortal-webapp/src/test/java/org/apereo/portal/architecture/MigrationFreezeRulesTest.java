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
package org.apereo.portal.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static org.junit.Assert.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Ratchets for the modernization (MOD-D29): code that still uses an API being migrated away from is
 * recorded in {@code src/test/resources/archunit_store}, and any <em>new</em> use fails the build.
 * Each rule names the migration that removes the API.
 *
 * <p>The store only shrinks: once a usage is removed, the test fails until the store is updated, so
 * the recorded debt always matches the code. To record the smaller set, run
 *
 * <pre>
 * ./gradlew :uPortal-webapp:test --tests '*MigrationFreezeRulesTest' \
 *     -Darchunit.freeze.store.default.allowStoreUpdate=true
 * </pre>
 *
 * and commit the changed store files. {@code -Darchunit.freeze.refreeze=true} records every current
 * violation, including new ones. The store must never grow (MOD-D43): CI's {@code
 * .github/scripts/check-freeze-store.py} fails when a rule records more uses than before, so new
 * code has to use the modern API instead.
 *
 * <p>Type names are given as strings, so the rules compile whichever Hibernate, Ehcache or Joda
 * version is on the classpath. The rules read the main classes of every uPortal module this module
 * depends on (they are on its test classpath as jars); test classes are not checked.
 */
public class MigrationFreezeRulesTest {

    /** Packages where uPortal's persistence code lives and may use Hibernate's native API. */
    private static final String[] PERSISTENCE_PACKAGES = {
        "..dao..", "..jpa..", "org.apereo.portal.hibernate..", "org.apereo.portal.tools.dbloader.."
    };

    /**
     * One class from each module whose main code the rules must see; guards against a classpath
     * change silently shrinking what is checked.
     */
    private static final List<String> REQUIRED_CLASSES =
            Arrays.asList(
                    "org.apereo.portal.events.aggr.PortalRawEventsAggregatorImpl", // events
                    "org.apereo.portal.jpa.BaseJpaDao", // utils-core
                    "org.apereo.portal.jpa.BaseAggrEventsJpaDao", // rdbm
                    "org.apereo.portal.portlet.dao.jpa.JpaPortletDefinitionDao", // web
                    "org.apereo.portal.layout.dlm.FragmentDefinitionDao", // layout-impl
                    "org.apereo.portal.layout.dao.jpa.JpaStylesheetDescriptorDao", // layout-core
                    "org.apereo.portal.groups.pags.dao.jpa.JpaPersonAttributesGroupDefinitionDao",
                    "org.apereo.portal.i18n.dao.jpa.JpaMessageDao", // i18n
                    "org.apereo.portal.persondir.dao.jpa.JpaLocalAccountDaoImpl", // persondir
                    "org.apereo.portal.tenants.JpaTenantDao", // tenants
                    "org.apereo.portal.version.dao.jpa.JpaVersionDao", // tools
                    "org.apereo.portal.concurrency.locking.JpaClusterLockDao", // concurrency
                    "org.apereo.portal.dao.portletlist.jpa.PortletListDao", // api-rest
                    "org.apereo.portal.utils.cache.TaggedCacheEntryPurger", // utils-core
                    "org.apereo.portal.rendering.DynamicRenderingPipeline", // rendering
                    "org.apereo.portal.portlets.account.UserAccountHelper"); // portlets

    /** The Hibernate session types that declare createCriteria (by name: see the class javadoc) */
    private static final DescribedPredicate<HasName> HIBERNATE_SESSIONS =
            HasName.Predicates.nameMatching(
                    "org\\.hibernate\\.(Session|SharedSessionContract|StatelessSession)");

    private static JavaClasses portalClasses;

    @BeforeClass
    public static void importPortalClasses() {
        portalClasses =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("org.apereo.portal");
    }

    @Test
    public void importsTheMainClassesOfEveryModule() {
        final List<String> missing =
                REQUIRED_CLASSES.stream()
                        .filter(name -> !portalClasses.contain(name))
                        .collect(Collectors.toList());
        assertTrue(
                "Classes the freeze rules must check were not imported: " + missing,
                missing.isEmpty());
        // Test classes would only add noise to the store
        assertTrue(
                portalClasses.stream()
                        .map(JavaClass::getName)
                        .noneMatch(n -> n.endsWith("MigrationFreezeRulesTest")));
    }

    /** Joda-Time → java.time. */
    @Test
    public void noNewJodaTime() {
        freeze(
                        noClasses()
                                .should()
                                .dependOnClassesThat()
                                .resideInAPackage("org.joda.time..")
                                .as("no new org.joda.time usage")
                                .because("Joda-Time is being replaced by java.time (MOD-D29)"))
                .check(portalClasses);
    }

    /** Ehcache 2 → JCache (JSR-107). */
    @Test
    public void noNewEhcache2() {
        final ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "net.sf.ehcache..",
                                "org.springframework.cache.ehcache..",
                                "org.hibernate.cache.ehcache..")
                        .as("no new Ehcache 2 (net.sf.ehcache) usage")
                        .because(
                                "Ehcache 2 is being replaced by JCache; Spring 6 and Hibernate 6"
                                        + " dropped their Ehcache 2 integrations (MOD-D29)");
        freeze(rule).check(portalClasses);
    }

    /** The legacy Hibernate Criteria API was deprecated in 5.2 and removed in 6.0. */
    @Test
    public void noNewLegacyHibernateCriteria() {
        final ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("org.hibernate.criterion..")
                        .orShould()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("org.hibernate.Criteria")
                        .orShould()
                        .callMethodWhere(
                                target(name("createCriteria"))
                                        .and(target(owner(HIBERNATE_SESSIONS))))
                        .as("no new legacy Hibernate Criteria usage")
                        .because(
                                "org.hibernate.Criteria and Session#createCriteria were removed in"
                                        + " Hibernate 6; use JPA criteria queries (MOD-D29)");
        freeze(rule).check(portalClasses);
    }

    /** Native Hibernate sessions belong in the persistence layer only. */
    @Test
    public void noNewHibernateSessionOutsidePersistence() {
        final ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackages(PERSISTENCE_PACKAGES)
                        .should()
                        .dependOnClassesThat()
                        .areAssignableTo("org.hibernate.Session")
                        .as("no new org.hibernate.Session use outside persistence/DAO packages")
                        .because(
                                "the Session API changes in every Hibernate major (5.2, 6, 7);"
                                        + " keep it behind JPA in DAO packages (MOD-D29)");
        freeze(rule).check(portalClasses);
    }
}
