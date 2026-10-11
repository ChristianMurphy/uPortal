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
package org.apereo.portal.rendering.xslt;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apereo.portal.i18n.LocaleDirection;
import org.apereo.portal.i18n.LocaleManager;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.apereo.portal.utils.cache.CacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Presents the user's preferred locale (USER_LANG) and the direction of its text (USER_DIR, rtl or
 * ltr) as transformer parameters
 */
public class LocaleTransformerConfigurationSource extends TransformerConfigurationSourceAdapter {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private IUserInstanceManager userInstanceManager;

    @Autowired
    public void setUserInstanceManager(IUserInstanceManager userInstanceManager) {
        this.userInstanceManager = userInstanceManager;
    }

    private LocaleManager getLocaleManager(HttpServletRequest request) {
        final IUserInstance userInstance = this.userInstanceManager.getUserInstance(request);
        return userInstance.getLocaleManager();
    }

    /* (non-Javadoc)
     * @see org.apereo.portal.rendering.xslt.TransformerConfigurationSource#getParameters(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public Map<String, Object> getParameters(
            HttpServletRequest request, HttpServletResponse response) {
        final LocaleManager localeManager = this.getLocaleManager(request);

        final List<Locale> locales = localeManager.getLocales();
        if (locales != null && locales.size() > 0 && locales.get(0) != null) {
            final String locale = locales.get(0).toString();
            final String xslLocale = locale.replace('_', '-');
            final String direction = LocaleDirection.of(locales.get(0));
            this.logger.debug("Setting USER_LANG to {} and USER_DIR to {}", xslLocale, direction);
            final Map<String, Object> parameters = new HashMap<>();
            parameters.put("USER_LANG", xslLocale);
            parameters.put("USER_DIR", direction);
            return parameters;
        }

        return null;
    }

    /* (non-Javadoc)
     * @see org.apereo.portal.rendering.xslt.TransformerConfigurationSource#getCacheKey(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public CacheKey getCacheKey(HttpServletRequest request, HttpServletResponse response) {
        final LocaleManager localeManager = this.getLocaleManager(request);

        final List<Locale> locales = localeManager.getLocales();
        if (locales != null && locales.size() > 0 && locales.get(0) != null) {
            final String locale = locales.get(0).toString();
            final String xslLocale = locale.replace('_', '-');
            return CacheKey.build(this.getClass().getName(), xslLocale);
        }

        return null;
    }
}
