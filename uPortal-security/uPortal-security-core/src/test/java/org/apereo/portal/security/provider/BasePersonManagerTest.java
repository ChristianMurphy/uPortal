/**
 * Licensed to Apereo under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright ownership. Apereo
 * licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at the
 * following location:
 *
 * <p>
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apereo.portal.security.provider;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.IUserIdentityStore;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.ISecurityContext;
import org.apereo.portal.security.InitialSecurityContextFactory;
import org.apereo.portal.security.PersonFactory;
import org.apereo.portal.security.oauth.IdTokenFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests how {@link BasePersonManager} looks up and caches guest user IDs (MOD-I104). */
public class BasePersonManagerTest {

    private static final int GUESTS = 40;
    private static final String GUEST_PARAMETER = "guest";

    private final List<String> guestNames = new ArrayList<>();
    private final Map<String, AtomicInteger> lookups = new ConcurrentHashMap<>();
    private final IUserIdentityStore userIdentityStore = mock(IUserIdentityStore.class);

    @Before
    public void setUp() {
        for (int i = 0; i < GUESTS; i++) {
            guestNames.add("guest" + i);
        }
        initGuestUsernames(String.join(",", guestNames));
        when(userIdentityStore.getPortalUserId(anyString()))
                .thenAnswer(
                        invocation -> {
                            final String username = invocation.getArgument(0);
                            lookups.computeIfAbsent(username, k -> new AtomicInteger())
                                    .incrementAndGet();
                            return guestNames.contains(username) ? idOf(username) : null;
                        });
    }

    @After
    public void tearDown() {
        initGuestUsernames("guest");
    }

    /**
     * Many requests for many guest users arrive at once on a fresh manager. Every request gets its
     * guest's ID, and each guest's ID is looked up once.
     */
    @Test(timeout = 120000)
    public void concurrentFirstRequestsGetTheirGuestIds() throws Exception {
        final int threads = 8;
        for (int round = 0; round < 25; round++) {
            lookups.clear();
            final BasePersonManager personManager = newPersonManager();
            final CountDownLatch start = new CountDownLatch(1);
            final ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                final List<Future<?>> results = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    final List<String> order = new ArrayList<>(guestNames);
                    Collections.rotate(order, t * GUESTS / threads);
                    results.add(
                            executor.submit(
                                    () -> {
                                        start.await();
                                        for (String guest : order) {
                                            final IPerson person =
                                                    personManager.createPersonForRequest(
                                                            requestFor(guest));
                                            assertEquals(guest, person.getUserName());
                                            assertEquals(idOf(guest), person.getID());
                                        }
                                        return null;
                                    }));
                }
                start.countDown();
                for (Future<?> result : results) {
                    result.get(60, SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
            for (String guest : guestNames) {
                assertEquals(guest + " lookups", 1, lookups.get(guest).get());
            }
        }
    }

    /** A guest account missing from the database fails the request, and isn't cached. */
    @Test
    public void missingGuestAccountIsNotCached() throws Exception {
        initGuestUsernames("guest0,missingGuest");
        final BasePersonManager personManager = newPersonManager();
        for (int i = 0; i < 2; i++) {
            try {
                personManager.createPersonForRequest(requestFor("missingGuest"));
                fail("Expected an IllegalStateException for a guest with no account");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("missingGuest"));
            }
        }
        assertEquals(2, lookups.get("missingGuest").get());
        assertEquals(
                idOf("guest0"), personManager.createPersonForRequest(requestFor("guest0")).getID());
    }

    private BasePersonManager newPersonManager() throws Exception {
        final BasePersonManager result = new BasePersonManager();
        final IdTokenFactory idTokenFactory = mock(IdTokenFactory.class);
        final InitialSecurityContextFactory securityContextFactory =
                mock(InitialSecurityContextFactory.class);
        when(securityContextFactory.getInitialContext()).thenReturn(mock(ISecurityContext.class));
        final IGuestUsernameSelector selector = mock(IGuestUsernameSelector.class);
        when(selector.selectGuestUsername(any()))
                .thenAnswer(
                        invocation ->
                                ((HttpServletRequest) invocation.getArgument(0))
                                        .getParameter(GUEST_PARAMETER));
        ReflectionTestUtils.setField(result, "idTokenFactory", idTokenFactory);
        ReflectionTestUtils.setField(result, "userIdentityStore", userIdentityStore);
        ReflectionTestUtils.setField(
                result, "initialSecurityContextFactory", securityContextFactory);
        ReflectionTestUtils.setField(
                result, "guestUsernameSelectors", Collections.singletonList(selector));
        return result;
    }

    private static HttpServletRequest requestFor(String guest) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(GUEST_PARAMETER, guest);
        return request;
    }

    private static int idOf(String guest) {
        return 1000 + guest.hashCode() % 1000;
    }

    private static void initGuestUsernames(String names) {
        final PersonFactory personFactory = new PersonFactory();
        personFactory.setGuestUsernamesProperty(names);
        personFactory.init();
    }
}
