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
package org.apereo.portal.tenants;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.IUserIdentityStore;
import org.apereo.portal.persondir.ILocalAccountDao;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.apereo.portal.portlets.account.IPasswordResetNotification;
import org.apereo.portal.portlets.account.UserAccountHelper;
import org.apereo.portal.url.IPortalRequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integrates with the 'forgot-password' framework portlet to grant the tenant admin contact access
 * via local authentication when a new tenant is created.
 *
 * <p>This behavior is completely optional. You can add or remove it by adjusting the
 * 'tenantOperationsListeners' bean in servicesContext.xml.
 *
 * @since 4.1
 */
public final class ResetPasswordTenantOperationsListener extends AbstractTenantOperationsListener {

    public static final String ADMIN_CONTACT_USERNAME = "adminContactUsername";
    public static final String ADMIN_CONTACT_EMAIL = "adminContactEmail";

    /** An email local part: no spaces, control characters or @, in any script (MOD-I273) */
    private static final Pattern LOCAL_PART = Pattern.compile("[^\\s\\p{Cc}@]+");

    /** A domain label: no spaces, control characters, @ or dots, in any script */
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[^\\s\\p{Cc}@.]+");

    /** The SMTP path limit */
    private static final int EMAIL_MAX_LENGTH = 254;

    private static final String TENANT_ADMIN_EMAIL_SENT = "tenant.admin.email.sent";
    private static final String UNABLE_TO_SEND_TENANT_ADMIN_EMAIL =
            "unable.to.send.tenant.admin.email";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired private IUserIdentityStore userIdentityStore;

    @Autowired private ILocalAccountDao localAccountDao;

    @Autowired private IPortalRequestUtils portalRequestUtils;

    @Autowired private UserAccountHelper userAccountHelper;

    private IPasswordResetNotification passwordResetNotification;

    public ResetPasswordTenantOperationsListener() {
        super("reset-password");
    }

    @Autowired
    public void setPasswordResetNotification(IPasswordResetNotification passwordResetNotification) {
        this.passwordResetNotification = passwordResetNotification;
    }

    /** This step is not required */
    @Override
    public boolean isOptional() {
        return true;
    }

    @Override
    public TenantOperationResponse onCreate(final ITenant tenant) {
        return prepareResponse(tenant);
    }

    @Override
    public TenantOperationResponse onUpdate(final ITenant tenant) {
        // Send the same email here as well, in case the contact changes
        return prepareResponse(tenant);
    }

    /**
     * @since 4.3
     */
    @Override
    public Set<ITenantManagementAction> getAvailableActions() {
        ITenantManagementAction result =
                new ITenantManagementAction() {
                    @Override
                    public String getFname() {
                        return "resend-admin-email";
                    }

                    @Override
                    public String getMessageCode() {
                        return "resend.admin.email";
                    }

                    @Override
                    public TenantOperationResponse invoke(final ITenant tenant) {
                        return prepareResponse(tenant);
                    }
                };
        return Collections.singleton(result);
    }

    @Override
    public void validateAttribute(final String key, final String value) throws Exception {
        switch (key) {
            case ADMIN_CONTACT_USERNAME:
                if (!userIdentityStore.validateUsername(value)) {
                    throw new IllegalArgumentException("Invalid username:  " + value);
                }
                break;
            case ADMIN_CONTACT_EMAIL:
                if (value == null || !isEmailAddress(value)) {
                    throw new IllegalArgumentException("Invalid email address:  " + value);
                }
                break;
            default:
                // No problem;  fall through
        }
    }

    /**
     * True when {@code value} is a local part, @ and a domain of two or more labels,
     * internationalized addresses included, at most 254 code points in all (MOD-I273). Each part is
     * checked on its own, so no pattern nests quantifiers.
     */
    private static boolean isEmailAddress(String value) {
        if (value.codePointCount(0, value.length()) > EMAIL_MAX_LENGTH) {
            return false;
        }
        final int at = value.indexOf('@');
        if (at < 0 || !LOCAL_PART.matcher(value.substring(0, at)).matches()) {
            return false;
        }
        final String[] labels = value.substring(at + 1).split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    /*
     * Implementation
     */

    private TenantOperationResponse prepareResponse(final ITenant tenant) {
        try {
            sendResetPasswordEmail(tenant);
        } catch (Exception e) {
            log.error(
                    "Failed to send tenant admin email to address {} for tenant {}",
                    tenant.getAttribute(ADMIN_CONTACT_EMAIL),
                    tenant.getName(),
                    e);
            final TenantOperationResponse error =
                    new TenantOperationResponse(
                            this, TenantOperationResponse.Result.FAIL); // Just a warning
            error.addMessage(
                    createLocalizedMessage(
                            UNABLE_TO_SEND_TENANT_ADMIN_EMAIL,
                            new String[] {tenant.getAttribute(ADMIN_CONTACT_EMAIL)}));
            return error;
        }
        final TenantOperationResponse result =
                new TenantOperationResponse(this, TenantOperationResponse.Result.SUCCESS);
        result.addMessage(
                createLocalizedMessage(
                        TENANT_ADMIN_EMAIL_SENT,
                        new String[] {tenant.getAttribute(ADMIN_CONTACT_EMAIL)}));
        return result;
    }

    private void sendResetPasswordEmail(final ITenant tenant) {
        ILocalAccountPerson admin =
                localAccountDao.getPerson(tenant.getAttribute(ADMIN_CONTACT_USERNAME));
        admin.setAttribute("loginToken", userAccountHelper.getRandomToken());
        final HttpServletRequest http = portalRequestUtils.getCurrentPortalRequest();
        this.userAccountHelper.sendLoginToken(http, admin, passwordResetNotification);
        localAccountDao.updateAccount(admin);
    }
}
