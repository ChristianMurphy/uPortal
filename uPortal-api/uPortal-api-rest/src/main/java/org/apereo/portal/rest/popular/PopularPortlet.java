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
package org.apereo.portal.rest.popular;

import org.jspecify.annotations.Nullable;

/** A portlet and the number of times users added it to their layouts. */
public final class PopularPortlet {

    private final String fname;
    private final String title;
    private final @Nullable String description;
    private final String url;
    private final int count;

    public PopularPortlet(
            String fname, String title, @Nullable String description, String url, int count) {
        this.fname = fname;
        this.title = title;
        this.description = description;
        this.url = url;
        this.count = count;
    }

    public String getFname() {
        return fname;
    }

    /** The portlet's title in the user's locale. */
    public String getTitle() {
        return title;
    }

    /** The portlet's description in the user's locale, or null. */
    public @Nullable String getDescription() {
        return description;
    }

    /** The portlet's page, {@code <context path>/p/<fname>}. */
    public String getUrl() {
        return url;
    }

    /** How many times users added the portlet to their layouts in the period. */
    public int getCount() {
        return count;
    }
}
