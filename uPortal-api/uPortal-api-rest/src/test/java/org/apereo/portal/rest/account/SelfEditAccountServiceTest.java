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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apereo.portal.i18n.ILocaleStore;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.i18n.LocaleManagerFactory;
import org.apereo.portal.portletpublishing.xml.Preference;
import org.apereo.portal.portlets.StringListAttribute;
import org.apereo.portal.portlets.account.PersonForm;
import org.apereo.portal.portlets.account.UserAccountHelper;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The service's rules: the session's user only, the portlet's permission checks, attribute and
 * password handling, and no password in any answer.
 */
public class SelfEditAccountServiceTest {

    private static final String SECRET = "n3w-Secret!";

    private IPersonManager personManager;
    private UserAccountHelper helper;
    private MessageSource messageSource;
    private SelfEditAccountService service;
    private MockHttpServletRequest request;
    private IPerson student;
    private PersonForm form;

    private static Preference preference(String name) {
        final Preference preference = new Preference();
        preference.setName(name);
        preference.setLabel("attribute.displayName." + name);
        return preference;
    }

    @Before
    public void setUp() {
        personManager = mock(IPersonManager.class);
        helper = mock(UserAccountHelper.class);
        messageSource = mock(MessageSource.class);
        final ILocaleStore localeStore = mock(ILocaleStore.class);
        final LocaleManagerFactory localeManagerFactory = mock(LocaleManagerFactory.class);
        final LocaleManager localeManager = mock(LocaleManager.class);
        when(localeStore.getUserLocales(any())).thenReturn(new Locale[] {Locale.FRANCE});
        when(localeManagerFactory.createLocaleManager(any(), anyList())).thenReturn(localeManager);
        when(localeManager.getLocales()).thenReturn(Collections.singletonList(Locale.FRANCE));
        when(messageSource.getMessage(anyString(), isNull(), anyString(), eq(Locale.FRANCE)))
                .thenAnswer(invocation -> "fr:" + invocation.getArgument(0));

        service =
                new SelfEditAccountService(
                        personManager, helper, messageSource, localeStore, localeManagerFactory);
        final Map<String, String> display = new LinkedHashMap<>();
        display.put("title", "GENERIC");
        display.put("mail", "EMAIL");
        service.setDirectoryDisplayAttributes(display);

        request = new MockHttpServletRequest();
        // A client may name another user; the service must not listen
        request.setParameter("username", "admin");
        student = mock(IPerson.class);
        when(student.getUserName()).thenReturn("student");
        when(student.getAttribute("mail")).thenReturn("student@example.org");
        when(personManager.getPerson(request)).thenReturn(student);

        form = new PersonForm(Collections.<Preference>emptyList());
        form.getAttributes()
                .put("department", new StringListAttribute(Collections.singletonList("Biology")));
        form.getAttributes().put("title", new StringListAttribute(Collections.singletonList("Dr")));
        when(helper.getForm("student")).thenReturn(form);
        when(helper.getEditableUserAttributes(student))
                .thenReturn(Arrays.asList(preference("department"), preference("title")));
    }

    private static void expectRefusal(HttpStatus status, Runnable call) {
        try {
            call.run();
            fail("Expected " + status);
        } catch (AccountException e) {
            assertEquals(status, e.getStatus());
            assertFalse(String.valueOf(e.getMessage()).contains(SECRET));
        }
    }

    private SelfEditAccountService.Update update(Map<String, String> attributes) {
        return new SelfEditAccountService.Update(attributes, null, null);
    }

    private PersonForm savedForm() {
        final ArgumentCaptor<PersonForm> captor = ArgumentCaptor.forClass(PersonForm.class);
        verify(helper).updateAccount(eq(student), captor.capture());
        return captor.getValue();
    }

    @Test
    public void showsTheDetailsWithLocalizedLabels() {
        final Account account = service.getAccount(request);

        assertEquals("student", account.getUsername());
        assertEquals(2, account.getDetails().size());
        final AccountAttribute title = account.getDetails().get(0);
        assertEquals("title", title.getName());
        assertEquals("fr:attribute.displayName.title", title.getLabel());
        assertNull(title.getValue());
        assertEquals("student@example.org", account.getDetails().get(1).getValue());
    }

    @Test
    public void listsNothingToEditWithoutThePermission() {
        final Account account = service.getAccount(request);

        assertFalse(account.isCanEdit());
        assertTrue(account.getEditable().isEmpty());
        verify(helper).canEditUser(student, "student");
        verify(helper, never()).getForm(anyString());
    }

    @Test
    public void listsTheEditableAttributesWithTheirStoredValues() {
        when(helper.canEditUser(student, "student")).thenReturn(true);

        final Account account = service.getAccount(request);

        assertTrue(account.isCanEdit());
        assertEquals(2, account.getEditable().size());
        final AccountAttribute department = account.getEditable().get(0);
        assertEquals("department", department.getName());
        assertEquals("fr:attribute.displayName.department", department.getLabel());
        assertEquals("Biology", department.getValue());
    }

    @Test
    public void reportsAnEmptyStoredAttributeAsNull() {
        when(helper.canEditUser(student, "student")).thenReturn(true);
        form.getAttributes().put("title", new StringListAttribute(Collections.<String>emptyList()));
        form.getAttributes().remove("department");

        final Account account = service.getAccount(request);

        assertNull(account.getEditable().get(0).getValue());
        assertNull(account.getEditable().get(1).getValue());
    }

    @Test
    public void answersGuestsWith401() {
        final IPerson guest = mock(IPerson.class);
        when(guest.isGuest()).thenReturn(true);
        when(personManager.getPerson(request)).thenReturn(guest);

        expectRefusal(HttpStatus.UNAUTHORIZED, () -> service.getAccount(request));
        expectRefusal(
                HttpStatus.UNAUTHORIZED,
                () ->
                        service.updateAccount(
                                request,
                                new SelfEditAccountService.Update(
                                        Collections.<String, String>emptyMap(), SECRET, SECRET)));
        verify(helper, never()).updateAccount(any(), any());
    }

    @Test
    public void answersAMissingPersonWith401() {
        when(personManager.getPerson(request)).thenReturn(null);

        expectRefusal(HttpStatus.UNAUTHORIZED, () -> service.getAccount(request));
    }

    @Test
    public void refusesChangesWithoutThePermission() {
        expectRefusal(
                HttpStatus.FORBIDDEN,
                () ->
                        service.updateAccount(
                                request,
                                new SelfEditAccountService.Update(
                                        Collections.<String, String>emptyMap(), SECRET, SECRET)));
        verify(helper).canEditUser(student, "student");
        verify(helper, never()).updateAccount(any(), any());
    }

    @Test
    public void refusesAnAttributeTheUserMayNotEdit() {
        when(helper.canEditUser(student, "student")).thenReturn(true);
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("department", "Physics");
        attributes.put("sn", "Somebody Else");

        expectRefusal(
                HttpStatus.FORBIDDEN, () -> service.updateAccount(request, update(attributes)));
        verify(helper, never()).updateAccount(any(), any());
    }

    @Test
    public void savesTheSessionUsersAccount() {
        when(helper.canEditUser(student, "student")).thenReturn(true);

        service.updateAccount(request, update(Collections.singletonMap("department", "Physics")));

        verify(helper).getForm("student");
        verify(helper, never()).getForm("admin");
        final PersonForm saved = savedForm();
        assertEquals(
                Collections.singletonList("Physics"),
                saved.getAttributes().get("department").getValue());
        // Left out, so unchanged
        assertEquals(
                Collections.singletonList("Dr"), saved.getAttributes().get("title").getValue());
        assertNull(saved.getPassword());
    }

    @Test
    public void emptiesABlankAttribute() {
        when(helper.canEditUser(student, "student")).thenReturn(true);

        service.updateAccount(request, update(Collections.singletonMap("department", "  ")));

        final List<String> values = savedForm().getAttributes().get("department").getValue();
        assertTrue(values.isEmpty());
    }

    @Test
    public void passesThePasswordToTheHelper() {
        when(helper.canEditUser(student, "student")).thenReturn(true);

        service.updateAccount(
                request,
                new SelfEditAccountService.Update(
                        Collections.<String, String>emptyMap(), SECRET, SECRET));

        final PersonForm saved = savedForm();
        assertEquals(SECRET, saved.getPassword());
        assertEquals(SECRET, saved.getConfirmPassword());
    }

    @Test
    public void savesThePasswordWhateverTheConfirmationSays() {
        // As the portlet did; a later change may refuse this
        when(helper.canEditUser(student, "student")).thenReturn(true);

        service.updateAccount(
                request,
                new SelfEditAccountService.Update(
                        Collections.<String, String>emptyMap(), SECRET, "typo"));

        assertEquals(SECRET, savedForm().getPassword());
    }
}
