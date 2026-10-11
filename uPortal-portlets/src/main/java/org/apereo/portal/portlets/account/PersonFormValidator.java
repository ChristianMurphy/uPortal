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
package org.apereo.portal.portlets.account;

import org.apache.commons.lang.StringUtils;
import org.apereo.portal.persondir.ILocalAccountDao;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.apereo.portal.security.IPortalPasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.binding.message.MessageContext;

public class PersonFormValidator {

    private ILocalAccountDao accountDao;
    private IPortalPasswordService passwordService;

    @Autowired(required = true)
    public void setLocalAccountDao(ILocalAccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired(required = true)
    public void setPortalPasswordService(IPortalPasswordService passwordService) {
        this.passwordService = passwordService;
    }

    public void validateEditLocalAccount(PersonForm person, MessageContext context) {

        // ensure that this username isn't already taken
        if (person.getId() < 0 && accountDao.getPerson(person.getUsername()) != null) {
            context.addMessage(
                    new MessageBuilder()
                            .error()
                            .source("username")
                            .code("username.in.use")
                            .build());
        }

        validatePasswordsMatch(person, context);
    }

    /**
     * Self Edit Account (the self-edit-account flow's editOwnAccount state): the passwords must
     * match, and a new password needs the account's current password.
     */
    public void validateEditOwnAccount(PersonForm person, MessageContext context) {
        validatePasswordsMatch(person, context);

        if (StringUtils.isBlank(person.getPassword())) {
            return;
        }
        if (StringUtils.isBlank(person.getCurrentPassword())) {
            context.addMessage(
                    new MessageBuilder()
                            .error()
                            .source("currentPassword")
                            .code("please.enter.current.password")
                            .build());
            return;
        }
        final ILocalAccountPerson account = accountDao.getPerson(person.getId());
        if (account == null
                || account.getPassword() == null
                || !passwordService.validatePassword(
                        person.getCurrentPassword(), account.getPassword())) {
            context.addMessage(
                    new MessageBuilder()
                            .error()
                            .source("currentPassword")
                            .code("current.password.doesnt.match")
                            .build());
        }
    }

    private static void validatePasswordsMatch(PersonForm person, MessageContext context) {
        if (StringUtils.isNotBlank(person.getPassword())
                || StringUtils.isNotBlank(person.getConfirmPassword())) {
            if (person.getPassword() == null
                    || !person.getPassword().equals(person.getConfirmPassword())) {
                context.addMessage(
                        new MessageBuilder()
                                .error()
                                .source("password")
                                .code("passwords.must.match")
                                .build());
            }
        }
    }
}
