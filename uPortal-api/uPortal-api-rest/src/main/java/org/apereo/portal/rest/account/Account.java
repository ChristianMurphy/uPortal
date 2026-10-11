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
package org.apereo.portal.rest.account;

import java.util.Collections;
import java.util.List;

/**
 * The body of {@code GET /api/v6-0/account}: what the signed-in user sees of their account.
 *
 * <p>{@code details} are the directory attributes the portlet showed, read from the user's session,
 * so changes appear at the next login. {@code editable} are the local account's attributes the user
 * may change, with their stored values; it is empty when {@code canEdit} is false. The password is
 * never part of it.
 */
public final class Account {

    private final String username;
    private final List<AccountAttribute> details;
    private final boolean canEdit;
    private final List<AccountAttribute> editable;

    public Account(
            String username,
            List<AccountAttribute> details,
            boolean canEdit,
            List<AccountAttribute> editable) {
        this.username = username;
        this.details = Collections.unmodifiableList(details);
        this.canEdit = canEdit;
        this.editable = Collections.unmodifiableList(editable);
    }

    public String getUsername() {
        return username;
    }

    public List<AccountAttribute> getDetails() {
        return details;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public List<AccountAttribute> getEditable() {
        return editable;
    }
}
