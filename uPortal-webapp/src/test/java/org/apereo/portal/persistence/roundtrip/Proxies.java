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
package org.apereo.portal.persistence.roundtrip;

import org.hibernate.proxy.HibernateProxy;

/**
 * Unwraps lazy-loading proxies so that the comparator can read the entity's fields. {@code
 * HibernateProxy#getHibernateLazyInitializer()} has the same shape from Hibernate 4.3 to 7.
 */
final class Proxies {
    private Proxies() {}

    static Object unproxy(Object value) {
        if (value instanceof HibernateProxy) {
            return ((HibernateProxy) value).getHibernateLazyInitializer().getImplementation();
        }
        return value;
    }
}
