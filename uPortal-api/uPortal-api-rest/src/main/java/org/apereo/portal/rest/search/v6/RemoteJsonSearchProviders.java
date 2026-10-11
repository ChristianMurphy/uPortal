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
package org.apereo.portal.rest.search.v6;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.spring.context.support.PortalPropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

/**
 * The remote JSON search providers that portal properties configure: one {@link
 * RemoteJsonSearchStrategy} per name in {@code org.apereo.portal.search.remote.providers}
 * (comma-separated), each with its settings under {@code org.apereo.portal.search.remote.<name>.}
 * (see {@link RemoteJsonSearchSettings}). Unusable settings stop the portal from starting, with a
 * message that names the property.
 */
@Component
public class RemoteJsonSearchProviders {

    public static final String PROVIDERS_PROPERTY = RemoteJsonSearchSettings.PREFIX + "providers";

    private final List<RemoteJsonSearchStrategy> strategies;

    public RemoteJsonSearchProviders(PortalPropertySourcesPlaceholderConfigurer properties) {
        this(properties.getPropertyResolver());
    }

    RemoteJsonSearchProviders(PropertyResolver properties) {
        final List<RemoteJsonSearchStrategy> list = new ArrayList<>();
        for (String name : properties.getProperty(PROVIDERS_PROPERTY, "").split(",")) {
            if (!name.trim().isEmpty()) {
                list.add(
                        new RemoteJsonSearchStrategy(
                                RemoteJsonSearchSettings.fromProperties(properties, name.trim())));
            }
        }
        this.strategies = Collections.unmodifiableList(list);
    }

    public List<RemoteJsonSearchStrategy> getStrategies() {
        return strategies;
    }
}
