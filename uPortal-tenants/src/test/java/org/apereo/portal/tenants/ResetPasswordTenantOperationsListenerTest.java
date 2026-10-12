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
package org.apereo.portal.tenants;

import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The tenant admin contact email accepts internationalized addresses: a local part and a domain in
 * any script (MOD-I273).
 */
public class ResetPasswordTenantOperationsListenerTest {
    private final ResetPasswordTenantOperationsListener listener =
            new ResetPasswordTenantOperationsListener();

    @Test
    public void acceptsInternationalizedAddresses() throws Exception {
        for (final String email :
                new String[] {
                    "admin@uportal.org",
                    "jane.doe+portal@sub.example.edu",
                    "josé@universidad.es",
                    "用户@例子.广告",
                    "иван@пример.рф",
                }) {
            listener.validateAttribute("adminContactEmail", email);
        }
    }

    @Test
    public void refusesWhatIsNotAnAddress() {
        for (final String email :
                new String[] {
                    "no-at-sign",
                    "two@@example.org",
                    "a space@example.org",
                    "user@nodot",
                    "@example.org",
                    "user@",
                    "user@example.",
                    "user@.example.org",
                    "new\nline@example.org",
                }) {
            try {
                listener.validateAttribute("adminContactEmail", email);
                fail("Accepted the email \"" + email + "\"");
            } catch (Exception expected) {
                // refused
            }
        }
    }
}
