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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.portlet.PortletPreferences;
import javax.servlet.http.HttpServletRequest;
import org.apereo.portal.portlet.container.services.PortletPreferencesFactory;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionId;
import org.apereo.portal.portlet.om.IPortletDefinitionParameter;
import org.apereo.portal.portlet.om.IPortletDescriptorKey;
import org.apereo.portal.portlet.om.IPortletEntity;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.apereo.portal.portlet.registry.IPortletEntityRegistry;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.spring.spel.IPortalSpELService;
import org.apereo.portal.spring.spel.PortalSpELServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

public class BackgroundPreferenceServiceTest {

    private static final String FNAME = "background-preference";
    private static final String GALAXY = "/uPortal/media/skins/common/backgroundImages/galaxy.jpg";
    private static final String MOUNTAIN =
            "/uPortal/media/skins/common/backgroundImages/mountain.jpg";

    private IPersonManager personManager;
    private IPortletDefinitionRegistry definitionRegistry;
    private IPortletEntityRegistry entityRegistry;
    private MobileDeviceRole mobileDeviceRole;
    private IPerson person;
    private IPortletDefinition definition;
    private IPortletDescriptorKey descriptorKey;
    private Preferences preferences;
    private BackgroundPreferenceService service;
    private HttpServletRequest request;

    /** The user's preferences of the channel, in memory. */
    private static final class Preferences implements PortletPreferences {
        final Map<String, String[]> values = new HashMap<>();
        int stores;
        boolean failToStore;

        @Override
        public boolean isReadOnly(String key) {
            return false;
        }

        @Override
        public String getValue(String key, String def) {
            final String[] value = values.get(key);
            return value == null || value.length == 0 ? def : value[0];
        }

        @Override
        public String[] getValues(String key, String[] def) {
            return values.getOrDefault(key, def);
        }

        @Override
        public void setValue(String key, String value) {
            values.put(key, new String[] {value});
        }

        @Override
        public void setValues(String key, String[] value) {
            values.put(key, value);
        }

        @Override
        public Enumeration<String> getNames() {
            return Collections.enumeration(values.keySet());
        }

        @Override
        public Map<String, String[]> getMap() {
            return values;
        }

        @Override
        public void reset(String key) {
            values.remove(key);
        }

        @Override
        public void store() throws java.io.IOException {
            if (failToStore) {
                throw new java.io.IOException("database down");
            }
            stores++;
        }
    }

    @Before
    public void setUp() {
        personManager = mock(IPersonManager.class);
        definitionRegistry = mock(IPortletDefinitionRegistry.class);
        entityRegistry = mock(IPortletEntityRegistry.class);
        mobileDeviceRole = mock(MobileDeviceRole.class);
        final PortletPreferencesFactory preferencesFactory = mock(PortletPreferencesFactory.class);
        final IPortalSpELService spel = mock(IPortalSpELService.class);
        final SpelExpressionParser parser = new SpelExpressionParser();
        when(spel.parseExpression(anyString(), any()))
                .thenAnswer(
                        call ->
                                parser.parseExpression(
                                        call.getArgument(0),
                                        PortalSpELServiceImpl.TemplateParserContext.INSTANCE));

        person = mock(IPerson.class);
        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(person);

        definition = mock(IPortletDefinition.class);
        descriptorKey = mock(IPortletDescriptorKey.class);
        when(definition.getPortletDescriptorKey()).thenReturn(descriptorKey);
        when(definition.getPortletDefinitionId()).thenReturn(mock(IPortletDefinitionId.class));
        final IPortletDefinitionParameter tagName = mock(IPortletDefinitionParameter.class);
        when(tagName.getValue()).thenReturn("up-background-preference");
        when(definition.getParameter("webComponent.tagName")).thenReturn(tagName);
        when(definitionRegistry.getPortletDefinitionByFname(FNAME)).thenReturn(definition);

        final IPortletEntity entity = mock(IPortletEntity.class);
        when(entityRegistry.getOrCreateDefaultPortletEntity(any(), any())).thenReturn(entity);
        preferences = new Preferences();
        when(preferencesFactory.createAPIPortletPreferences(
                        any(), eq(entity), eq(false), eq(false)))
                .thenReturn(preferences);
        preferences.setValues(
                "defaultBackgroundImages",
                new String[] {
                    "${portalContext}/media/skins/common/backgroundImages/galaxy.jpg",
                    "${portalContext}/media/skins/common/backgroundImages/mountain.jpg"
                });
        preferences.setValues(
                "defaultBackgroundThumbnailImages",
                new String[] {
                    "${portalContext}/media/skins/common/backgroundImages/thumbnails/galaxy.jpg",
                    "${portalContext}/media/skins/common/backgroundImages/thumbnails/mountain.jpg"
                });
        preferences.setValues("backgroundImageCaptions", new String[] {"Galaxy", "Mountain"});

        service =
                new BackgroundPreferenceService(
                        personManager,
                        definitionRegistry,
                        entityRegistry,
                        preferencesFactory,
                        spel,
                        mobileDeviceRole);
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setContextPath("/uPortal");
        request = mockRequest;
    }

    private static List<String> urls(BackgroundPreference preference) {
        return preference.getImages().stream()
                .map(BackgroundImage::getUrl)
                .collect(Collectors.toList());
    }

    private void expectRefusal(HttpStatus status, Runnable call) {
        try {
            call.run();
            fail("expected " + status);
        } catch (BackgroundPreferenceException e) {
            assertEquals(status, e.getStatus());
        }
    }

    @Test
    public void listsTheImagesWithThePortalContextAndNothingChosen() {
        final BackgroundPreference preference = service.getPreference(request, FNAME);

        assertEquals("body.portal #portalPageBody::before", preference.getContainerSelector());
        assertEquals(Arrays.asList(GALAXY, MOUNTAIN), urls(preference));
        final BackgroundImage galaxy = preference.getImages().get(0);
        assertEquals(
                "/uPortal/media/skins/common/backgroundImages/thumbnails/galaxy.jpg",
                galaxy.getThumbnailUrl());
        assertEquals("Galaxy", galaxy.getCaption());
        assertNull(preference.getSelected());
    }

    @Test
    public void answersTheUsersChoice() {
        preferences.setValue("defaultSelectedBackgroundImage", MOUNTAIN);

        assertEquals(MOUNTAIN, service.getPreference(request, FNAME).getSelected());
    }

    @Test
    public void treatsAChoiceThatIsNoLongerOfferedAsNone() {
        preferences.setValue("defaultSelectedBackgroundImage", "/elsewhere/old.jpg");

        assertNull(service.getPreference(request, FNAME).getSelected());
    }

    @Test
    public void usesTheConfiguredContainerSelector() {
        preferences.setValue("defaultBackgroundContainerSelector", "body.portal");

        assertEquals("body.portal", service.getPreference(request, FNAME).getContainerSelector());
    }

    @Test
    public void fallsBackToTheImageForMissingThumbnailsAndCaptions() {
        preferences.reset("defaultBackgroundThumbnailImages");
        preferences.reset("backgroundImageCaptions");

        final BackgroundImage galaxy = service.getPreference(request, FNAME).getImages().get(0);

        assertEquals(GALAXY, galaxy.getThumbnailUrl());
        assertEquals("galaxy.jpg", galaxy.getCaption());
    }

    @Test
    public void givesMobileUsersTheMobileSet() {
        when(mobileDeviceRole.includes(person)).thenReturn(true);
        preferences.setValues("mobileBackgroundImages", new String[] {"/m/one.jpg"});
        preferences.setValue("mobileSelectedBackgroundImage", "/m/one.jpg");

        final BackgroundPreference preference = service.getPreference(request, FNAME);

        assertEquals("body.portal #portalPageBody", preference.getContainerSelector());
        assertEquals(Collections.singletonList("/m/one.jpg"), urls(preference));
        assertEquals("/m/one.jpg", preference.getSelected());

        service.setPreference(request, FNAME, null);
        assertNull(preferences.getValue("mobileSelectedBackgroundImage", null));
    }

    @Test
    public void savesAnOfferedImage() {
        final BackgroundPreference preference = service.setPreference(request, FNAME, GALAXY);

        assertEquals(GALAXY, preference.getSelected());
        assertArrayEquals(
                new String[] {GALAXY},
                preferences.getValues("defaultSelectedBackgroundImage", null));
        assertEquals(1, preferences.stores);
    }

    @Test
    public void refusesAnImageThatIsNotOffered() {
        preferences.setValue("defaultSelectedBackgroundImage", MOUNTAIN);

        expectRefusal(
                HttpStatus.BAD_REQUEST,
                () -> service.setPreference(request, FNAME, "https://evil.example/x.jpg"));
        expectRefusal(
                HttpStatus.BAD_REQUEST,
                () ->
                        service.setPreference(
                                request,
                                FNAME,
                                "/uPortal/media/skins/common/backgroundImages/thumbnails/galaxy.jpg"));

        assertEquals(MOUNTAIN, preferences.getValue("defaultSelectedBackgroundImage", null));
        assertEquals(0, preferences.stores);
    }

    @Test
    public void clearsTheChoiceForNullOrBlank() {
        preferences.setValue("defaultSelectedBackgroundImage", MOUNTAIN);

        assertNull(service.setPreference(request, FNAME, null).getSelected());
        assertNull(preferences.getValue("defaultSelectedBackgroundImage", null));

        preferences.setValue("defaultSelectedBackgroundImage", MOUNTAIN);
        assertNull(service.setPreference(request, FNAME, " ").getSelected());
        assertNull(preferences.getValue("defaultSelectedBackgroundImage", null));
        assertEquals(2, preferences.stores);
    }

    @Test
    public void reportsAChoiceThatCannotBeStored() {
        preferences.failToStore = true;
        try {
            service.setPreference(request, FNAME, GALAXY);
            fail("expected the failure to store");
        } catch (IllegalStateException e) {
            assertTrue(e.getCause() instanceof java.io.IOException);
        }
    }

    @Test
    public void refusesGuestsAndRequestsWithoutAPerson() {
        when(person.isGuest()).thenReturn(true);
        expectRefusal(HttpStatus.UNAUTHORIZED, () -> service.getPreference(request, FNAME));
        expectRefusal(HttpStatus.UNAUTHORIZED, () -> service.setPreference(request, FNAME, GALAXY));

        when(personManager.getPerson(any(HttpServletRequest.class))).thenReturn(null);
        expectRefusal(HttpStatus.UNAUTHORIZED, () -> service.getPreference(request, FNAME));
        assertEquals(0, preferences.stores);
    }

    @Test
    public void acceptsTheOldPortlet() {
        when(definition.getParameter("webComponent.tagName")).thenReturn(null);
        when(descriptorKey.isFrameworkPortlet()).thenReturn(true);
        when(descriptorKey.getPortletName()).thenReturn("backgroundPreference");

        assertEquals(2, service.getPreference(request, FNAME).getImages().size());
    }

    @Test
    public void refusesChannelsThatAreNotBackgroundPreferences() {
        expectRefusal(HttpStatus.NOT_FOUND, () -> service.getPreference(request, "unknown"));

        final IPortletDefinitionParameter otherTag = mock(IPortletDefinitionParameter.class);
        when(otherTag.getValue()).thenReturn("up-favorites");
        when(definition.getParameter("webComponent.tagName")).thenReturn(otherTag);
        expectRefusal(HttpStatus.NOT_FOUND, () -> service.setPreference(request, FNAME, GALAXY));

        when(definition.getParameter("webComponent.tagName")).thenReturn(null);
        when(descriptorKey.isFrameworkPortlet()).thenReturn(true);
        when(descriptorKey.getPortletName()).thenReturn("Favorites");
        expectRefusal(HttpStatus.NOT_FOUND, () -> service.getPreference(request, FNAME));
        assertEquals(0, preferences.stores);
    }

    @Test
    public void refusesAChannelTheUserCannotUse() {
        when(entityRegistry.getOrCreateDefaultPortletEntity(any(), any()))
                .thenThrow(new IllegalArgumentException("No layout node ID found"));

        expectRefusal(HttpStatus.NOT_FOUND, () -> service.getPreference(request, FNAME));
    }
}
