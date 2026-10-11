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
package org.apereo.portal.shell;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;

public class PortalShellTest {

    /** The shell's context is closed, so its beans' destroy callbacks run and the JVM can exit. */
    @Test
    public void closeContextRunsTheDestroyCallbacks() {
        final AtomicBoolean destroyed = new AtomicBoolean();
        final GenericApplicationContext context = new GenericApplicationContext();
        context.getDefaultListableBeanFactory()
                .registerDisposableBean("jgroupsChannel", () -> destroyed.set(true));
        context.refresh();

        PortalShell.closeContext(context);

        assertTrue(destroyed.get());
        assertFalse(context.isActive());
    }
}
