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

/** A favorite collection: a tab of the user's layout that a favorites fragment provides. */
public final class FavoriteCollection {

    private final String nodeId;
    private final String name;
    private final String url;
    private final boolean moveAllowed;
    private final boolean deleteAllowed;

    public FavoriteCollection(
            String nodeId, String name, String url, boolean moveAllowed, boolean deleteAllowed) {
        this.nodeId = nodeId;
        this.name = name;
        this.url = url;
        this.moveAllowed = moveAllowed;
        this.deleteAllowed = deleteAllowed;
    }

    /** The layout node, which identifies the favorite in this API. */
    public String getNodeId() {
        return nodeId;
    }

    public String getName() {
        return name;
    }

    /** Where the collection opens: the tab's URL. */
    public String getUrl() {
        return url;
    }

    /**
     * False when the layout does not let the user move the node (for example a locked fragment).
     */
    public boolean isMoveAllowed() {
        return moveAllowed;
    }

    /** False when the layout does not let the user remove the node. */
    public boolean isDeleteAllowed() {
        return deleteAllowed;
    }
}
