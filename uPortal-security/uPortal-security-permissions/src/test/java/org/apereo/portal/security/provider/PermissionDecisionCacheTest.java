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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
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
import org.apereo.portal.security.IPermission;
import org.apereo.portal.security.IPermissionPolicy;
import org.apereo.portal.security.IPermissionStore;
import org.apereo.portal.security.IPerson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** A permission change has to reach the next permission check, not only the store. */
public class PermissionDecisionCacheTest {
    private CacheManager cacheManager;
    private AuthorizationImpl authorization;
    private IPermissionPolicy policy;
    private IAuthorizationPrincipal student;

    @Before
    public void setUp() {
        cacheManager =
                new CacheManager(
                        new Configuration()
                                .name("PermissionDecisionCacheTest-" + UUID.randomUUID()));
        final Cache decisions = new Cache(new CacheConfiguration("PRINCIPAL_HAS_PERMISSION", 100));
        cacheManager.addCache(decisions);

        authorization = new AuthorizationImpl();
        authorization.setDoesPrincipalHavePermissionCache(decisions);
        authorization.setPermissionStore(mock(IPermissionStore.class));
        // The permission set cache needs the portal's entity caching service
        authorization.setCachePermissions(false);

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
        student = mock(IAuthorizationPrincipal.class);
        when(student.getKey()).thenReturn("student");
        when(student.getType()).thenReturn((Class) IPerson.class);
    }

    @After
    public void tearDown() {
        cacheManager.shutdown();
    }

    private boolean check() {
        return authorization.doesPrincipalHavePermission(
                student, "UP_SYSTEM", "VIEW", "target1", policy);
    }

    private void policyAnswers(boolean first, boolean second) {
        when(policy.doesPrincipalHavePermission(
                        any(IAuthorizationService.class),
                        any(IAuthorizationPrincipal.class),
                        any(),
                        any(),
                        any()))
                .thenReturn(first, second);
    }

    @Test
    public void revocationIsSeenByTheNextCheck() {
        policyAnswers(true, false);
        assertTrue(check());

        authorization.removePermissions(new IPermission[] {mock(IPermission.class)});

        assertFalse("The revoked permission still applies", check());
    }

    @Test
    public void grantIsSeenByTheNextCheck() {
        policyAnswers(false, true);
        assertFalse(check());

        authorization.addPermissions(new IPermission[] {mock(IPermission.class)});

        assertTrue("The granted permission doesn't apply yet", check());
    }
}
