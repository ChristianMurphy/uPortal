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
package org.apereo.portal.groups.pags.dao.jpa;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupDefinition;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupDefinitionDao;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupTestDefinition;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupTestGroupDefinition;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.apereo.portal.test.CacheRace;
import org.hibernate.Cache;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A request that loads a PAGS group definition while a change to it commits must not leave the old
 * tests or the old member groups in the second-level cache (MOD-I311). Group membership, and with
 * it permissions, is evaluated from these definitions.
 *
 * <p>Each test caches the whole definition, then evicts only the part the change touches, so the
 * reader takes the rest from the cache and reads that part's rows. It pauses there, between the
 * read and the cache put, while the change commits.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalCacheRaceTestContext.xml")
public class PagsDefinitionCacheRaceTest extends BasePortalJpaDaoTest {

    private static final String GROUP = "race-group";
    private static final String TESTER =
            "org.apereo.portal.groups.pags.testers.StringEqualsIgnoreCaseTester";

    @Autowired private ApplicationContext applicationContext;

    private IPersonAttributesGroupDefinitionDao dao;
    private EntityManagerFactory emf;
    private Cache cache;

    @Before
    public void createDefinition() {
        dao =
                (IPersonAttributesGroupDefinitionDao)
                        applicationContext
                                .getAutowireCapableBeanFactory()
                                .createBean(JpaPersonAttributesGroupDefinitionDao.class);
        emf = getEntityManager().getEntityManagerFactory();
        cache = emf.unwrap(SessionFactoryImplementor.class).getCache();
        executeInTransaction(
                () -> {
                    final IPersonAttributesGroupDefinition member =
                            dao.createPersonAttributesGroupDefinition("race-member-1", "member");
                    final PersonAttributesGroupDefinitionImpl group =
                            new PersonAttributesGroupDefinitionImpl(GROUP, "staff only");
                    final PersonAttributesGroupTestGroupDefinitionImpl testGroup =
                            new PersonAttributesGroupTestGroupDefinitionImpl(group);
                    testGroup.setTests(
                            Collections.singleton(
                                    new PersonAttributesGroupTestDefinitionImpl(
                                            testGroup, "affiliation", TESTER, "staff")));
                    group.setTestGroups(Collections.singleton(testGroup));
                    group.setMembers(Collections.singleton(member));
                    return dao.updatePersonAttributesGroupDefinition(group);
                });
        CacheRace.evictAll(emf);
    }

    /**
     * Runs before the base class deletes every entity: a stale cached definition would fail that
     * delete with an optimistic-lock error and leave rows for the next test class.
     */
    @After
    public void evictCache() {
        CacheRace.evictAll(emf);
    }

    private IPersonAttributesGroupDefinition definition() {
        return dao.getPersonAttributesGroupDefinitionByName(GROUP).iterator().next();
    }

    /** What membership evaluation reads: the test values and the member groups. */
    private List<String> view() {
        return execute(
                () -> {
                    final IPersonAttributesGroupDefinition group = definition();
                    final Set<String> tests = new TreeSet<>();
                    for (IPersonAttributesGroupTestGroupDefinition testGroup :
                            group.getTestGroups()) {
                        for (IPersonAttributesGroupTestDefinition test : testGroup.getTests()) {
                            tests.add(test.getAttributeName() + "=" + test.getTestValue());
                        }
                    }
                    final Set<String> members = new TreeSet<>();
                    for (IPersonAttributesGroupDefinition member : group.getMembers()) {
                        members.add(member.getName());
                    }
                    return Arrays.asList("tests=" + tests, "members=" + members);
                });
    }

    /** Caches the whole definition, as a request that evaluates membership does. */
    private void cacheDefinition() {
        view();
    }

    @Test
    public void aTestValueChangedDuringALoadTakesEffect() throws Exception {
        raceTestChange(false);
        assertEquals(
                "the definition after the change",
                Arrays.asList("tests=[affiliation=faculty]", "members=[race-member-1]"),
                view());
    }

    @Test
    public void aTestAddedDuringALoadTakesEffect() throws Exception {
        raceTestChange(true);
        assertEquals(
                "the definition after the change",
                Arrays.asList(
                        "tests=[affiliation=staff, department=added]", "members=[race-member-1]"),
                view());
    }

    /** Changes the test value, or adds a test, while a load reads the test group's tests. */
    private void raceTestChange(boolean addTest) throws InterruptedException {
        cacheDefinition();
        // The tests of the test group are read again; the group and test group come from the cache
        cache.evictCollectionRegion(
                PersonAttributesGroupTestGroupDefinitionImpl.class.getName() + ".tests");
        cache.evictEntityRegion(PersonAttributesGroupTestDefinitionImpl.class);
        CacheRace.race(
                emf,
                PersonAttributesGroupTestDefinitionImpl.class,
                this::view,
                () ->
                        executeInTransaction(
                                () -> {
                                    final IPersonAttributesGroupDefinition group = definition();
                                    final IPersonAttributesGroupTestGroupDefinition testGroup =
                                            group.getTestGroups().iterator().next();
                                    final Set<IPersonAttributesGroupTestDefinition> tests =
                                            new HashSet<>(testGroup.getTests());
                                    if (addTest) {
                                        tests.add(
                                                new PersonAttributesGroupTestDefinitionImpl(
                                                        testGroup, "department", TESTER, "added"));
                                    } else {
                                        tests.iterator().next().setTestValue("faculty");
                                    }
                                    testGroup.setTests(tests);
                                    return dao.updatePersonAttributesGroupDefinition(group);
                                }));
    }

    @Test
    public void aMemberAddedDuringALoadTakesEffect() throws Exception {
        final IPersonAttributesGroupDefinition added =
                executeInTransaction(
                        () -> dao.createPersonAttributesGroupDefinition("race-member-2", "member"));
        final long memberId = execute(() -> definition().getMembers().iterator().next().getId());
        CacheRace.evictAll(emf);
        // The reader reads the group, then the member list, and pauses at the member group
        CacheRace.race(
                emf,
                PersonAttributesGroupDefinitionImpl.class,
                memberId,
                this::view,
                () -> addMember(added.getName()));
        assertEquals(
                "the definition after the change",
                Arrays.asList(
                        "tests=[affiliation=staff]", "members=[race-member-1, race-member-2]"),
                view());
    }

    private void addMember(String name) {
        executeInTransaction(
                () -> {
                    final IPersonAttributesGroupDefinition group = definition();
                    final Set<IPersonAttributesGroupDefinition> members =
                            new HashSet<>(group.getMembers());
                    members.add(
                            dao.getPersonAttributesGroupDefinitionByName(name).iterator().next());
                    group.setMembers(members);
                    return dao.updatePersonAttributesGroupDefinition(group);
                });
    }
}
