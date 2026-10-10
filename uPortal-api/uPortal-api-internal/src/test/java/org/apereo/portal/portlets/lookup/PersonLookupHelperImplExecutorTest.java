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
package org.apereo.portal.portlets.lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IPerson;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.apereo.services.persondir.IPersonAttributes;
import org.apereo.services.persondir.support.NamedPersonImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** The person search thread pool must not outlive the Spring context that owns the helper. */
public class PersonLookupHelperImplExecutorTest {

    private static final int SEARCH_RESULTS = 3;

    private AnnotationConfigApplicationContext context;
    private ThreadRecordingPersonLookupHelper helper;

    @Before
    public void setUp() {
        final Set<IPersonAttributes> people = new HashSet<>();
        for (int i = 0; i < SEARCH_RESULTS; i++) {
            final Map<String, List<Object>> attributes =
                    Collections.singletonMap(
                            "displayName", Collections.<Object>singletonList("User " + i));
            people.add(new NamedPersonImpl("user" + i, attributes));
        }
        final IPersonAttributeDao personAttributeDao = mock(IPersonAttributeDao.class);
        when(personAttributeDao.getPeople(anyMap(), any())).thenReturn(people);

        context = new AnnotationConfigApplicationContext();
        context.registerBeanDefinition(
                "personLookupHelper",
                BeanDefinitionBuilder.genericBeanDefinition(ThreadRecordingPersonLookupHelper.class)
                        .addPropertyValue("personAttributeDao", personAttributeDao)
                        .getBeanDefinition());
        context.refresh();
        helper = context.getBean(ThreadRecordingPersonLookupHelper.class);

        final List<IPersonAttributes> results =
                helper.searchForPeople(
                        mock(IPerson.class), Collections.<String, Object>singletonMap("sn", "x"));
        assertEquals(SEARCH_RESULTS, results.size());
        assertFalse(helper.workerThreads.isEmpty());
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testClosingContextStopsSearchThreads() throws InterruptedException {
        context.close();

        for (Thread thread : helper.workerThreads) {
            thread.join(5000);
            assertFalse(thread.getName() + " is still running", thread.isAlive());
        }
    }

    @Test
    public void testSearchThreadsHaveMeaningfulNames() {
        for (Thread thread : helper.workerThreads) {
            assertTrue(thread.getName(), thread.getName().startsWith("uP-PersonLookup-"));
        }
    }

    /** Records the threads that run the per-person visibility checks. */
    public static class ThreadRecordingPersonLookupHelper extends PersonLookupHelperImpl {
        final Set<Thread> workerThreads = ConcurrentHashMap.newKeySet();

        @Override
        protected IAuthorizationPrincipal getPrincipalForUser(IPerson person) {
            return mock(IAuthorizationPrincipal.class);
        }

        @Override
        protected Set<String> getPermittedAttributes(IAuthorizationPrincipal principal) {
            return Collections.singleton("sn");
        }

        @Override
        protected IPersonAttributes getVisiblePerson(
                IAuthorizationPrincipal principal,
                IPersonAttributes person,
                Set<String> generallyPermittedAttributes) {
            workerThreads.add(Thread.currentThread());
            return person;
        }
    }
}
