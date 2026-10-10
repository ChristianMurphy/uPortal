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
package org.apereo.portal.spring.tx;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;

public class TransactionManagerCachingTransactionInterceptorTest {

    private final PlatformTransactionManager defaultManager =
            mock(PlatformTransactionManager.class);
    private final PlatformTransactionManager eventsManager = mock(PlatformTransactionManager.class);
    private TransactionManagerCachingTransactionInterceptor interceptor;

    @Before
    public void setUp() {
        final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("eventsManager", eventsManager);
        interceptor = new TransactionManagerCachingTransactionInterceptor();
        interceptor.setTransactionManager(defaultManager);
        interceptor.setBeanFactory(beanFactory);
    }

    @Test
    public void withoutAttributeUsesTheDefaultManager() {
        assertSame(defaultManager, interceptor.determineTransactionManager(null));
    }

    @Test
    public void qualifierSelectsAndCachesTheNamedManager() {
        final RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setQualifier("eventsManager");

        assertSame(eventsManager, interceptor.determineTransactionManager(attribute));
        assertSame(eventsManager, interceptor.determineTransactionManager(attribute));
    }
}
