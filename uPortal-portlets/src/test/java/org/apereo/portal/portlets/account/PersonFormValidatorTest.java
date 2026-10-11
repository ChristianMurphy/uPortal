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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apereo.portal.persondir.ILocalAccountDao;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.apereo.portal.portletpublishing.xml.Preference;
import org.apereo.portal.security.IPortalPasswordService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.binding.message.DefaultMessageContext;
import org.springframework.binding.message.Message;
import org.springframework.binding.message.MessageContext;
import org.springframework.context.support.StaticMessageSource;

/** The checks Web Flow runs on the edit-account and self-edit-account forms. */
public class PersonFormValidatorTest {

    private static final String HASH = "(SHA256)hash-of-0ld-Secret";

    private PersonFormValidator validator;
    private ILocalAccountDao accountDao;

    @Before
    public void setUp() {
        accountDao = mock(ILocalAccountDao.class);
        final ILocalAccountPerson account = mock(ILocalAccountPerson.class);
        when(account.getPassword()).thenReturn(HASH);
        when(accountDao.getPerson(42L)).thenReturn(account);
        when(accountDao.getPerson("taken")).thenReturn(account);
        final IPortalPasswordService passwordService = mock(IPortalPasswordService.class);
        when(passwordService.validatePassword("0ld-Secret", HASH)).thenReturn(true);

        validator = new PersonFormValidator();
        validator.setLocalAccountDao(accountDao);
        validator.setPortalPasswordService(passwordService);
    }

    private static PersonForm form(
            long id, String password, String confirmPassword, String currentPassword) {
        final PersonForm form = new PersonForm(Collections.<Preference>emptyList());
        form.setId(id);
        form.setUsername("selma");
        form.setPassword(password);
        form.setConfirmPassword(confirmPassword);
        form.setCurrentPassword(currentPassword);
        return form;
    }

    /** The message codes the validator added, in order. */
    private static List<String> codes(MessageContext context) {
        final List<String> codes = new ArrayList<>();
        for (Message message : context.getAllMessages()) {
            codes.add(message.getText());
        }
        return codes;
    }

    private static MessageContext context() {
        final StaticMessageSource source = new StaticMessageSource();
        source.setUseCodeAsDefaultMessage(true);
        return new DefaultMessageContext(source);
    }

    private List<String> editOwnAccount(PersonForm form) {
        final MessageContext context = context();
        validator.validateEditOwnAccount(form, context);
        return codes(context);
    }

    private List<String> editLocalAccount(PersonForm form) {
        final MessageContext context = context();
        validator.validateEditLocalAccount(form, context);
        return codes(context);
    }

    @Test
    public void ownAccountAcceptsANewPasswordWithTheCurrentOne() {
        assertEquals(
                Collections.emptyList(),
                editOwnAccount(form(42L, "n3w-Secret", "n3w-Secret", "0ld-Secret")));
    }

    @Test
    public void ownAccountNeedsNoPasswordToChangeOnlyAttributes() {
        assertEquals(Collections.emptyList(), editOwnAccount(form(42L, "", "", "")));
        assertEquals(Collections.emptyList(), editOwnAccount(form(42L, null, null, null)));
    }

    @Test
    public void ownAccountRefusesANewPasswordWithoutTheCurrentOne() {
        assertEquals(
                Collections.singletonList("please.enter.current.password"),
                editOwnAccount(form(42L, "n3w-Secret", "n3w-Secret", " ")));
    }

    @Test
    public void ownAccountRefusesAWrongCurrentPassword() {
        assertEquals(
                Collections.singletonList("current.password.doesnt.match"),
                editOwnAccount(form(42L, "n3w-Secret", "n3w-Secret", "wrong")));
    }

    @Test
    public void ownAccountRefusesAnAccountItCannotFind() {
        assertEquals(
                Collections.singletonList("current.password.doesnt.match"),
                editOwnAccount(form(7L, "n3w-Secret", "n3w-Secret", "0ld-Secret")));
    }

    @Test
    public void ownAccountRefusesAConfirmationThatDiffers() {
        assertEquals(
                Collections.singletonList("passwords.must.match"),
                editOwnAccount(form(42L, "n3w-Secret", "typo", "0ld-Secret")));
    }

    @Test
    public void userAdministrationNeedsNoCurrentPassword() {
        assertEquals(
                Collections.emptyList(),
                editLocalAccount(form(42L, "n3w-Secret", "n3w-Secret", null)));
    }

    @Test
    public void userAdministrationRefusesATakenNameForANewAccount() {
        final PersonForm form = form(-1L, "", "", null);
        form.setUsername("taken");
        assertEquals(Collections.singletonList("username.in.use"), editLocalAccount(form));
    }

    @Test
    public void userAdministrationRefusesAConfirmationThatDiffers() {
        assertEquals(
                Collections.singletonList("passwords.must.match"),
                editLocalAccount(form(42L, "n3w-Secret", "typo", null)));
    }
}
