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
package org.apereo.portal.url;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.apereo.portal.security.mvc.LoginController;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Encode the originally requested URL for use as a RefUrl parameter for the local (uPortal) login
 * use case: the configured login URL (usually the login portlet, {@code /uPortal/p/login}, or the
 * {@code /Login} servlet behind a single sign-on filter) with the requested page appended as an
 * ordinary {@code refUrl} query parameter. The login form posts it to {@code /Login}, which sends
 * the user to that page once signed in.
 *
 * <p>Select it with {@code login.refUrlEncoder=local}.
 */
public class LocalLoginRefUrlEncoder implements LoginRefUrlEncoder {

    private String loginUrl;

    private UrlAuthCustomizerRegistry urlCustomizer;

    /**
     * The login URL, the same as the Sign In button's ({@code
     * org.apereo.portal.channels.CLogin.CasLoginUrl}). It may already have a query string.
     */
    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    @Autowired
    public void setUrlCustomizer(UrlAuthCustomizerRegistry urlCustomizer) {
        this.urlCustomizer = urlCustomizer;
    }

    @Override
    public String encodeLoginAndRefUrl(HttpServletRequest request) {
        final StringBuilder refUrl = new StringBuilder(request.getRequestURI());
        final String queryString = request.getQueryString();
        if (queryString != null) {
            refUrl.append('?').append(queryString);
        }
        return encodeLoginUrl(request, refUrl.toString());
    }

    @Override
    public String encodeLoginFailureUrl(HttpServletRequest request, String refUrl) {
        return encodeLoginUrl(request, refUrl);
    }

    private String encodeLoginUrl(HttpServletRequest request, String refUrl) {
        if (StringUtils.isBlank(loginUrl)) {
            throw new IllegalStateException(
                    "LocalLoginRefUrlEncoder needs a login URL "
                            + "(org.apereo.portal.channels.CLogin.CasLoginUrl)");
        }
        final StringBuilder result = new StringBuilder(loginUrl);
        if (StringUtils.isNotBlank(refUrl)) {
            if (loginUrl.indexOf('?') < 0) {
                result.append('?');
            } else if (!loginUrl.endsWith("?") && !loginUrl.endsWith("&")) {
                result.append('&');
            }
            result.append(LoginController.REFERER_URL_PARAM)
                    .append('=')
                    .append(URLEncoder.encode(refUrl, StandardCharsets.UTF_8));
        }
        return urlCustomizer != null
                ? urlCustomizer.customizeUrl(request, result.toString())
                : result.toString();
    }
}
