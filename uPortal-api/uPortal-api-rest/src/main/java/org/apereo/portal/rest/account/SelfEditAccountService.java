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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apereo.portal.i18n.ILocaleStore;
import org.apereo.portal.i18n.LocaleManagerFactory;
import org.apereo.portal.portletpublishing.xml.Preference;
import org.apereo.portal.portlets.StringListAttribute;
import org.apereo.portal.portlets.account.PersonForm;
import org.apereo.portal.portlets.account.UserAccountHelper;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The signed-in user's own account, as the Password Management portlet (the self-edit-account Web
 * Flow) showed and changed it.
 *
 * <p>Every method works on the user of the request's portal session and never takes a user name
 * from the client. The permission rules are those of {@link UserAccountHelper}: the account must be
 * a local account, the user needs {@code UP_USERS EDIT_USER} on {@code SELF} to change it, and
 * {@code EDIT_USER_ATTRIBUTE} on each attribute's name. Passwords are never logged, returned, or
 * put in an error message.
 */
@Service
public class SelfEditAccountService {

    static final String PASSWORDS_MUST_MATCH = "passwords.must.match";

    private final IPersonManager personManager;
    private final UserAccountHelper userAccountHelper;
    private final MessageSource messageSource;
    private final ILocaleStore localeStore;
    private final LocaleManagerFactory localeManagerFactory;
    private Map<String, String> directoryDisplayAttributes = Collections.emptyMap();

    @Autowired
    public SelfEditAccountService(
            IPersonManager personManager,
            UserAccountHelper userAccountHelper,
            MessageSource messageSource,
            ILocaleStore localeStore,
            LocaleManagerFactory localeManagerFactory) {
        this.personManager = personManager;
        this.userAccountHelper = userAccountHelper;
        this.messageSource = messageSource;
        this.localeStore = localeStore;
        this.localeManagerFactory = localeManagerFactory;
    }

    /** The attributes the account details show, in order (userContext.xml). */
    @Resource(name = "directoryDisplayAttributes")
    public void setDirectoryDisplayAttributes(Map<String, String> directoryDisplayAttributes) {
        this.directoryDisplayAttributes = directoryDisplayAttributes;
    }

    /** The changes of {@code PUT /api/v6-0/account}. */
    public static final class Update {
        private final Map<String, String> attributes;
        private final @Nullable String password;
        private final @Nullable String confirmPassword;

        public Update(
                Map<String, String> attributes,
                @Nullable String password,
                @Nullable String confirmPassword) {
            this.attributes = attributes;
            this.password = password;
            this.confirmPassword = confirmPassword;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public @Nullable String getPassword() {
            return password;
        }

        public @Nullable String getConfirmPassword() {
            return confirmPassword;
        }
    }

    public Account getAccount(HttpServletRequest request) {
        final IPerson person = requireUser(request);
        final Locale locale = getLocale(person);
        final String username = person.getUserName();

        final List<AccountAttribute> details = new ArrayList<>();
        for (String name : directoryDisplayAttributes.keySet()) {
            final Object value = person.getAttribute(name);
            details.add(
                    new AccountAttribute(
                            name,
                            label("attribute.displayName." + name, locale),
                            value == null ? null : value.toString()));
        }

        final boolean canEdit = userAccountHelper.canEditUser(person, username);
        final List<AccountAttribute> editable = new ArrayList<>();
        if (canEdit) {
            final PersonForm form = userAccountHelper.getForm(username);
            for (Preference attribute : userAccountHelper.getEditableUserAttributes(person)) {
                editable.add(
                        new AccountAttribute(
                                attribute.getName(),
                                label(attribute.getLabel(), locale),
                                firstValue(form.getAttributes().get(attribute.getName()))));
            }
        }
        return new Account(username, details, canEdit, editable);
    }

    /**
     * Saves the user's changes. An attribute left out keeps its value, an empty one is removed. A
     * non-blank password replaces the current one.
     *
     * @throws AccountException 401 for guests; 403 when the user may not edit their account or
     *     names an attribute they may not edit; 400 with the code {@value #PASSWORDS_MUST_MATCH}
     *     when the password and its confirmation differ
     */
    public void updateAccount(HttpServletRequest request, Update update) {
        final IPerson person = requireUser(request);
        final String username = person.getUserName();
        if (!userAccountHelper.canEditUser(person, username)) {
            throw new AccountException(HttpStatus.FORBIDDEN, "You may not edit your account");
        }

        final Set<String> editable = new LinkedHashSet<>();
        for (Preference attribute : userAccountHelper.getEditableUserAttributes(person)) {
            editable.add(attribute.getName());
        }
        for (String name : update.attributes.keySet()) {
            if (!editable.contains(name)) {
                throw new AccountException(
                        HttpStatus.FORBIDDEN, "You may not edit the attribute " + name);
            }
        }

        if (StringUtils.isNotBlank(update.password)
                || StringUtils.isNotBlank(update.confirmPassword)) {
            if (update.password == null || !update.password.equals(update.confirmPassword)) {
                throw new AccountException(
                        HttpStatus.BAD_REQUEST,
                        "The password and its confirmation differ",
                        PASSWORDS_MUST_MATCH);
            }
        }

        final PersonForm form = userAccountHelper.getForm(username);
        for (Map.Entry<String, String> attribute : update.attributes.entrySet()) {
            final String value = attribute.getValue();
            form.getAttributes()
                    .put(
                            attribute.getKey(),
                            new StringListAttribute(
                                    StringUtils.isBlank(value)
                                            ? Collections.<String>emptyList()
                                            : Collections.singletonList(value)));
        }
        form.setPassword(update.password);
        form.setConfirmPassword(update.confirmPassword);
        userAccountHelper.updateAccount(person, form);
    }

    private IPerson requireUser(HttpServletRequest request) {
        final IPerson person = personManager.getPerson(request);
        if (person == null || person.isGuest()) {
            throw new AccountException(HttpStatus.UNAUTHORIZED, "Sign in to see your account");
        }
        return person;
    }

    private Locale getLocale(IPerson person) {
        final Locale[] userLocales = localeStore.getUserLocales(person);
        return localeManagerFactory
                .createLocaleManager(person, Arrays.asList(userLocales))
                .getLocales()
                .get(0);
    }

    private String label(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }

    private static @Nullable String firstValue(@Nullable StringListAttribute attribute) {
        if (attribute == null || attribute.getValue().isEmpty()) {
            return null;
        }
        return attribute.getValue().get(0);
    }
}
