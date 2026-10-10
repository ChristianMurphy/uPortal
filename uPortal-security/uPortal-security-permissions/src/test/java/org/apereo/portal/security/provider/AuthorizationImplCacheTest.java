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
package org.apereo.portal.security.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import org.apereo.portal.permission.IPermissionActivity;
import org.apereo.portal.permission.IPermissionOwner;
import org.apereo.portal.permission.dao.IPermissionOwnerDao;
import org.apereo.portal.permission.target.IPermissionTarget;
import org.apereo.portal.permission.target.IPermissionTargetProvider;
import org.apereo.portal.permission.target.IPermissionTargetProviderRegistry;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IAuthorizationService;
import org.apereo.portal.security.IPermissionPolicy;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.utils.cache.CacheFactory;
import org.apereo.portal.utils.cache.TagTrackingCacheEventListener;
import org.apereo.portal.utils.cache.UsernameTaggedCacheEntryPurger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Behaviour of AuthorizationImpl's caches on a real Ehcache: permission checks are cached per
 * principal, owner, activity and target, and purged with the user's other entries at logout. (The
 * principal cache is not covered here: creating an AuthorizationPrincipalImpl needs the portal's
 * application context.)
 */
public class AuthorizationImplCacheTest {

    private CacheManager ehcacheManager;
    private Ehcache permissionCache;
    private TagTrackingCacheEventListener tagTracker;
    private AuthorizationImpl authorization;
    private IPermissionPolicy policy;
    private IAuthorizationPrincipal student;

    @Before
    public void setUp() {
        ehcacheManager =
                new CacheManager(
                        new Configuration()
                                .name("AuthorizationImplCacheTest-" + UUID.randomUUID()));
        for (String name :
                new String[] {
                    CacheFactory.PRINCIPAL_CACHE,
                    CacheFactory.ENTITY_PARENTS_CACHE,
                    AuthorizationImpl.PRINCIPAL_HAS_PERMISSION_CACHE_NAME
                }) {
            ehcacheManager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration(name, 100)));
        }
        permissionCache =
                ehcacheManager.getEhcache(AuthorizationImpl.PRINCIPAL_HAS_PERMISSION_CACHE_NAME);
        tagTracker = new TagTrackingCacheEventListener();
        permissionCache.getCacheEventNotificationService().registerListener(tagTracker);
        final EhCacheCacheManager springCacheManager = new EhCacheCacheManager(ehcacheManager);
        springCacheManager.afterPropertiesSet();

        authorization = new AuthorizationImpl();
        authorization.setCacheManager(springCacheManager);

        final IPermissionOwnerDao ownerDao = mock(IPermissionOwnerDao.class);
        final IPermissionActivity activity = mock(IPermissionActivity.class);
        when(activity.getTargetProviderKey()).thenReturn("targets");
        when(ownerDao.getPermissionOwner(anyString())).thenReturn(mock(IPermissionOwner.class));
        when(ownerDao.getPermissionActivity(anyString(), anyString())).thenReturn(activity);
        final IPermissionTargetProvider targetProvider = mock(IPermissionTargetProvider.class);
        when(targetProvider.getTarget(anyString())).thenReturn(mock(IPermissionTarget.class));
        final IPermissionTargetProviderRegistry registry =
                mock(IPermissionTargetProviderRegistry.class);
        when(registry.getTargetProvider("targets")).thenReturn(targetProvider);
        ReflectionTestUtils.setField(authorization, "permissionOwnerDao", ownerDao);
        ReflectionTestUtils.setField(authorization, "targetProviderRegistry", registry);

        policy = mock(IPermissionPolicy.class);
        when(policy.doesPrincipalHavePermission(
                        any(IAuthorizationService.class),
                        any(IAuthorizationPrincipal.class),
                        any(),
                        any(),
                        any()))
                .thenReturn(true);
        student = principal("student");
    }

    @After
    public void tearDown() {
        ehcacheManager.shutdown();
    }

    private static IAuthorizationPrincipal principal(String key) {
        final IAuthorizationPrincipal principal = mock(IAuthorizationPrincipal.class);
        when(principal.getKey()).thenReturn(key);
        when(principal.getType()).thenReturn((Class) IPerson.class);
        return principal;
    }

    private boolean check(IAuthorizationPrincipal principal, String target) {
        return authorization.doesPrincipalHavePermission(
                principal, "UP_SYSTEM", "VIEW", target, policy);
    }

    private void verifyPolicyCalls(int count) {
        verify(policy, times(count)).doesPrincipalHavePermission(any(), any(), any(), any(), any());
    }

    @Test
    public void permissionChecksAreCached() {
        assertTrue(check(student, "target1"));
        assertTrue(check(principal("student"), "target1"));

        verifyPolicyCalls(1);
        assertEquals(1, permissionCache.getSize());
    }

    @Test
    public void eachTargetAndPrincipalHasItsOwnEntry() {
        check(student, "target1");
        check(student, "target2");
        check(principal("staff"), "target1");

        verifyPolicyCalls(3);
        assertEquals(3, permissionCache.getSize());
    }

    @Test
    public void logoutPurgeEvictsOnlyThatUsersChecks() {
        check(student, "target1");
        check(principal("staff"), "target1");

        tagTracker.purgeCacheEntries(UsernameTaggedCacheEntryPurger.createCacheEntryTag("student"));
        check(student, "target1");
        check(principal("staff"), "target1");

        verifyPolicyCalls(3);
    }
}
