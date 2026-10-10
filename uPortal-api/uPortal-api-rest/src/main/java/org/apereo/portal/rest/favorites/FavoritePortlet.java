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
package org.apereo.portal.rest.favorites;

import org.jspecify.annotations.Nullable;

/** A favorite portlet: a channel in the favorites folder of the user's layout. */
public final class FavoritePortlet {

    private final String nodeId;
    private final String fname;
    private final String name;
    private final @Nullable String description;
    private final String url;
    private final @Nullable String target;
    private final @Nullable String iconUrl;
    private final boolean moveAllowed;
    private final boolean deleteAllowed;

    public FavoritePortlet(
            String nodeId,
            String fname,
            String name,
            @Nullable String description,
            String url,
            @Nullable String target,
            @Nullable String iconUrl,
            boolean moveAllowed,
            boolean deleteAllowed) {
        this.nodeId = nodeId;
        this.fname = fname;
        this.name = name;
        this.description = description;
        this.url = url;
        this.target = target;
        this.iconUrl = iconUrl;
        this.moveAllowed = moveAllowed;
        this.deleteAllowed = deleteAllowed;
    }

    /** The layout node, which identifies the favorite in this API. */
    public String getNodeId() {
        return nodeId;
    }

    public String getFname() {
        return fname;
    }

    public String getName() {
        return name;
    }

    /** The portlet's description, or null. */
    public @Nullable String getDescription() {
        return description;
    }

    /** Where the favorite opens: the portlet, or its alternative maximized link. */
    public String getUrl() {
        return url;
    }

    /** The window to open {@link #getUrl()} in, or null for the portal's own window. */
    public @Nullable String getTarget() {
        return target;
    }

    /** The portlet's icon, or null. */
    public @Nullable String getIconUrl() {
        return iconUrl;
    }

    /** False when the layout does not let the user move the node. */
    public boolean isMoveAllowed() {
        return moveAllowed;
    }

    /** False when the layout does not let the user remove the node. */
    public boolean isDeleteAllowed() {
        return deleteAllowed;
    }
}
