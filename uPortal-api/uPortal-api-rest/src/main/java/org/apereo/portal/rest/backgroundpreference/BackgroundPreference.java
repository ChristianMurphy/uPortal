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
package org.apereo.portal.rest.backgroundpreference;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The backgrounds a user may choose and the one they chose, as the API returns them. */
public final class BackgroundPreference {
    private final String containerSelector;
    private final List<BackgroundImage> images;
    private final @Nullable String selected;

    public BackgroundPreference(
            String containerSelector, List<BackgroundImage> images, @Nullable String selected) {
        this.containerSelector = containerSelector;
        this.images = images;
        this.selected = selected;
    }

    /** The CSS selector of the element whose background the image becomes. */
    public String getContainerSelector() {
        return containerSelector;
    }

    public List<BackgroundImage> getImages() {
        return images;
    }

    /** The URL of the chosen image, one of {@link #getImages()}; null for none. */
    public @Nullable String getSelected() {
        return selected;
    }
}
