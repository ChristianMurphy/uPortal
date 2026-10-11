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
package org.apereo.portal.rest.backgroundpreference;

import java.util.ArrayList;
import java.util.List;
import javax.portlet.PortletPreferences;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apereo.portal.portlet.container.services.PortletPreferencesFactory;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionParameter;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.apereo.portal.portlet.registry.IPortletEntityRegistry;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.spring.spel.IPortalSpELService;
import org.apereo.portal.spring.spel.PortalSpELServiceImpl;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reads and saves the signed-in user's background, with the same preferences as the
 * BackgroundPreference portlet, so choices made with the portlet stay.
 *
 * <p>The images are the {@code defaultBackgroundImages}, {@code defaultBackgroundThumbnailImages}
 * and {@code backgroundImageCaptions} preferences of the channel; users in the "Mobile Device
 * Access" group get the {@code mobile*} ones. Paths may contain {@code ${portalContext}}. The
 * choice is the {@code defaultSelectedBackgroundImage} (or {@code mobileSelectedBackgroundImage})
 * preference of the user's own portlet entity for the channel, which only this user's layout uses.
 */
@Service
public class BackgroundPreferenceService {

    /** The framework portlet of channels not converted yet. */
    static final String PORTLET_NAME = "backgroundPreference";

    static final String TAG_NAME = "up-background-preference";
    static final String TAG_NAME_PARAMETER = "webComponent.tagName";
    static final String CAPTIONS_PREFERENCE = "backgroundImageCaptions";

    /**
     * Where the portlet's descriptor (portlet.xml) put the image when the definition says nothing.
     */
    static final String DEFAULT_CONTAINER_SELECTOR = "body.portal #portalPageBody::before";

    static final String MOBILE_CONTAINER_SELECTOR = "body.portal #portalPageBody";

    private final IPersonManager personManager;
    private final IPortletDefinitionRegistry portletDefinitionRegistry;
    private final IPortletEntityRegistry portletEntityRegistry;
    private final PortletPreferencesFactory portletPreferencesFactory;
    private final IPortalSpELService portalSpELService;
    private final MobileDeviceRole mobileDeviceRole;

    public BackgroundPreferenceService(
            IPersonManager personManager,
            IPortletDefinitionRegistry portletDefinitionRegistry,
            IPortletEntityRegistry portletEntityRegistry,
            @Qualifier("portletPreferencesFactory")
                    PortletPreferencesFactory portletPreferencesFactory,
            IPortalSpELService portalSpELService,
            MobileDeviceRole mobileDeviceRole) {
        this.personManager = personManager;
        this.portletDefinitionRegistry = portletDefinitionRegistry;
        this.portletEntityRegistry = portletEntityRegistry;
        this.portletPreferencesFactory = portletPreferencesFactory;
        this.portalSpELService = portalSpELService;
        this.mobileDeviceRole = mobileDeviceRole;
    }

    /** The backgrounds the user may choose on the channel, and the one they chose. */
    public BackgroundPreference getPreference(HttpServletRequest request, String fname) {
        final Channel channel = open(request, fname);
        return channel.describe(channel.preferences.getValue(channel.selectedName(), null));
    }

    /**
     * Saves the user's choice: the URL of one of the offered images, or null (or blank) for none.
     *
     * @throws BackgroundPreferenceException 400 when the URL is not one of the offered images
     */
    public BackgroundPreference setPreference(
            HttpServletRequest request, String fname, @Nullable String selected) {
        final Channel channel = open(request, fname);
        try {
            if (StringUtils.isBlank(selected)) {
                channel.preferences.reset(channel.selectedName());
            } else if (channel.offers(selected)) {
                channel.preferences.setValue(channel.selectedName(), selected);
            } else {
                throw new BackgroundPreferenceException(
                        HttpStatus.BAD_REQUEST, "Not one of the offered backgrounds");
            }
            channel.preferences.store();
        } catch (BackgroundPreferenceException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store the user's background", e);
        }
        return channel.describe(StringUtils.isBlank(selected) ? null : selected);
    }

    private Channel open(HttpServletRequest request, String fname) {
        final @Nullable IPerson person = personManager.getPerson(request);
        if (person == null || person.isGuest()) {
            throw new BackgroundPreferenceException(
                    HttpStatus.UNAUTHORIZED, "Sign in to choose a background");
        }
        final @Nullable IPortletDefinition definition =
                portletDefinitionRegistry.getPortletDefinitionByFname(fname);
        if (definition == null || !isBackgroundPreference(definition)) {
            throw new BackgroundPreferenceException(
                    HttpStatus.NOT_FOUND, "No background preference channel " + fname);
        }
        final IPortletEntity entity;
        try {
            // The entity of the channel in this user's layout: the one the portlet used
            entity =
                    portletEntityRegistry.getOrCreateDefaultPortletEntity(
                            request, definition.getPortletDefinitionId());
        } catch (IllegalArgumentException e) {
            // The user may not render the channel, or it is not in their layout
            throw new BackgroundPreferenceException(
                    HttpStatus.NOT_FOUND, "No background preference channel " + fname);
        }
        final PortletPreferences preferences =
                portletPreferencesFactory.createAPIPortletPreferences(
                        request, entity, false, false);
        final String prefix = mobileDeviceRole.includes(person) ? "mobile" : "default";
        return new Channel(preferences, prefix, request.getContextPath());
    }

    private static boolean isBackgroundPreference(IPortletDefinition definition) {
        final @Nullable IPortletDefinitionParameter tagName =
                definition.getParameter(TAG_NAME_PARAMETER);
        if (tagName != null) {
            return TAG_NAME.equals(tagName.getValue());
        }
        return definition.getPortletDescriptorKey().isFrameworkPortlet()
                && PORTLET_NAME.equals(definition.getPortletDescriptorKey().getPortletName());
    }

    /** The user's preferences of the channel, for the default or mobile set. */
    private final class Channel {
        final PortletPreferences preferences;
        final String prefix;
        final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

        Channel(PortletPreferences preferences, String prefix, String contextPath) {
            this.preferences = preferences;
            this.prefix = prefix;
            evaluationContext.setRootObject(new RootObject(contextPath));
        }

        String selectedName() {
            return prefix + "SelectedBackgroundImage";
        }

        List<BackgroundImage> images() {
            final String[] urls = values(prefix + "BackgroundImages");
            final String[] thumbnails = values(prefix + "BackgroundThumbnailImages");
            final String[] captions = values(CAPTIONS_PREFERENCE);
            final List<BackgroundImage> images = new ArrayList<>();
            for (int i = 0; i < urls.length; i++) {
                final String url = evaluate(urls[i]);
                final String thumbnail = i < thumbnails.length ? evaluate(thumbnails[i]) : url;
                final String caption =
                        i < captions.length && StringUtils.isNotBlank(captions[i])
                                ? captions[i]
                                : StringUtils.substringAfterLast(url, "/");
                images.add(new BackgroundImage(url, thumbnail, caption));
            }
            return images;
        }

        boolean offers(String url) {
            return images().stream().anyMatch(image -> image.getUrl().equals(url));
        }

        BackgroundPreference describe(@Nullable String selected) {
            final @Nullable String selector =
                    preferences.getValue(prefix + "BackgroundContainerSelector", null);
            final String containerSelector =
                    StringUtils.isNotBlank(selector)
                            ? selector
                            : "mobile".equals(prefix)
                                    ? MOBILE_CONTAINER_SELECTOR
                                    : DEFAULT_CONTAINER_SELECTOR;
            final List<BackgroundImage> images = images();
            // Only an offered image: a choice the definition no longer offers counts as none
            final @Nullable String offered =
                    selected != null
                                    && images.stream()
                                            .anyMatch(image -> image.getUrl().equals(selected))
                            ? selected
                            : null;
            return new BackgroundPreference(containerSelector, images, offered);
        }

        private String[] values(String name) {
            final String[] values = preferences.getValues(name, null);
            return values == null ? new String[0] : values;
        }

        private String evaluate(String path) {
            final String result =
                    portalSpELService
                            .parseExpression(
                                    path, PortalSpELServiceImpl.TemplateParserContext.INSTANCE)
                            .getValue(evaluationContext, String.class);
            return result == null ? path : result;
        }
    }

    /** Lets image paths say {@code ${portalContext}}, as with the portlet. */
    private static final class RootObject {
        private final String portalContext;

        RootObject(String portalContext) {
            this.portalContext = portalContext;
        }

        @SuppressWarnings("unused")
        public String getPortalContext() {
            return portalContext;
        }
    }
}
