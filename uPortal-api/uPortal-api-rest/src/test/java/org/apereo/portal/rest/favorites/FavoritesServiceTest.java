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
package org.apereo.portal.rest.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.apereo.portal.IUserPreferencesManager;
import org.apereo.portal.PortalException;
import org.apereo.portal.layout.IUserLayout;
import org.apereo.portal.layout.IUserLayoutManager;
import org.apereo.portal.layout.node.IUserLayoutChannelDescription;
import org.apereo.portal.layout.node.IUserLayoutFolderDescription;
import org.apereo.portal.layout.node.IUserLayoutNodeDescription;
import org.apereo.portal.layout.node.IUserLayoutNodeDescription.LayoutNodeType;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletDefinitionId;
import org.apereo.portal.portlet.om.IPortletType;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.apereo.portal.portlets.favorites.FavoritesUtils;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IAuthorizationService;
import org.apereo.portal.security.IPermission;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.user.IUserInstance;
import org.apereo.portal.user.IUserInstanceManager;
import org.apereo.portal.utils.personalize.IPersonalizer;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

public class FavoritesServiceTest {

    private static final String FAVORITES_FOLDER = "u12l1s4";

    private MockHttpServletRequest request;
    private IPerson person;
    private IUserLayoutManager layoutManager;
    private IUserLayout layout;
    private IAuthorizationPrincipal principal;
    private IPortletDefinitionRegistry registry;
    private FavoritesUtils favoritesUtils;
    private FavoritesService service;

    private final List<IUserLayoutNodeDescription> collections = new ArrayList<>();
    private final List<IUserLayoutNodeDescription> favoriteNodes = new ArrayList<>();

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/uPortal");

        person = mock(IPerson.class);
        when(person.getUserName()).thenReturn("student");
        final IPersonManager personManager = mock(IPersonManager.class);
        when(personManager.getPerson(request)).thenReturn(person);

        layout = mock(IUserLayout.class);
        layoutManager = mock(IUserLayoutManager.class);
        when(layoutManager.getUserLayout()).thenReturn(layout);
        final IUserPreferencesManager preferencesManager = mock(IUserPreferencesManager.class);
        when(preferencesManager.getUserLayoutManager()).thenReturn(layoutManager);
        final IUserInstance userInstance = mock(IUserInstance.class);
        when(userInstance.getPreferencesManager()).thenReturn(preferencesManager);
        final IUserInstanceManager userInstanceManager = mock(IUserInstanceManager.class);
        when(userInstanceManager.getUserInstance(request)).thenReturn(userInstance);

        principal = mock(IAuthorizationPrincipal.class);
        when(principal.canRender(anyString())).thenReturn(true);
        when(principal.hasPermission(anyString(), anyString(), anyString())).thenReturn(true);
        final IAuthorizationService authorizationService = mock(IAuthorizationService.class);
        when(authorizationService.newPrincipal("student", IPerson.class)).thenReturn(principal);

        favoritesUtils = mock(FavoritesUtils.class);
        when(favoritesUtils.getFavoriteCollections(layout)).thenReturn(collections);
        when(favoritesUtils.getFavoritePortletLayoutNodes(layout)).thenReturn(favoriteNodes);
        when(favoritesUtils.getFavoriteTabNodeId(layout)).thenReturn(FAVORITES_FOLDER);

        final IPersonalizer personalizer = mock(IPersonalizer.class);
        when(personalizer.personalize(any(), any(), any(HttpSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        registry = mock(IPortletDefinitionRegistry.class);
        service =
                new FavoritesService(
                        userInstanceManager,
                        personManager,
                        authorizationService,
                        registry,
                        favoritesUtils,
                        personalizer);
    }

    private static IUserLayoutChannelDescription channel(
            String nodeId, String fname, String publishId, Map<String, String> parameters) {
        final IUserLayoutChannelDescription channel = mock(IUserLayoutChannelDescription.class);
        when(channel.getId()).thenReturn(nodeId);
        when(channel.getFunctionalName()).thenReturn(fname);
        when(channel.getChannelPublishId()).thenReturn(publishId);
        when(channel.getName()).thenReturn("Name of " + fname);
        when(channel.getDescription()).thenReturn("About " + fname);
        when(channel.getParameterMap()).thenReturn(parameters);
        when(channel.getType()).thenReturn(LayoutNodeType.PORTLET);
        when(channel.isMoveAllowed()).thenReturn(true);
        when(channel.isDeleteAllowed()).thenReturn(true);
        return channel;
    }

    private IUserLayoutChannelDescription favorite(String nodeId, String fname, String publishId) {
        final IUserLayoutChannelDescription channel =
                channel(nodeId, fname, publishId, Collections.emptyMap());
        favoriteNodes.add(channel);
        return channel;
    }

    private IUserLayoutFolderDescription collection(String nodeId, String name) {
        final IUserLayoutFolderDescription folder = mock(IUserLayoutFolderDescription.class);
        when(folder.getId()).thenReturn(nodeId);
        when(folder.getName()).thenReturn(name);
        when(folder.getType()).thenReturn(LayoutNodeType.FOLDER);
        when(folder.isMoveAllowed()).thenReturn(true);
        when(folder.isDeleteAllowed()).thenReturn(true);
        collections.add(folder);
        return folder;
    }

    private IPortletDefinition definition(String fname, String id) {
        final IPortletDefinition definition = mock(IPortletDefinition.class);
        final IPortletDefinitionId definitionId = mock(IPortletDefinitionId.class);
        when(definitionId.getStringId()).thenReturn(id);
        when(definition.getPortletDefinitionId()).thenReturn(definitionId);
        when(definition.getFName()).thenReturn(fname);
        when(definition.getName()).thenReturn("Name of " + fname);
        when(definition.getTitle()).thenReturn("Title of " + fname);
        final IPortletType type = mock(IPortletType.class);
        when(type.getId()).thenReturn(1);
        when(definition.getType()).thenReturn(type);
        when(registry.getPortletDefinitionByFname(fname)).thenReturn(definition);
        return definition;
    }

    private HttpStatus refusal(Runnable call) {
        return assertThrows(FavoritesException.class, call::run).getStatus();
    }

    // getFavorites

    @Test
    public void refusesGuests() {
        when(person.isGuest()).thenReturn(true);

        assertEquals(HttpStatus.UNAUTHORIZED, refusal(() -> service.getFavorites(request)));
        assertEquals(
                HttpStatus.UNAUTHORIZED, refusal(() -> service.addFavorite(request, "calendar")));
        assertEquals(HttpStatus.UNAUTHORIZED, refusal(() -> service.removeFavorite(request, "n1")));
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                refusal(() -> service.moveFavorite(request, "n1", "n2", null)));
    }

    @Test
    public void refusesARequestWithoutAPerson() {
        final MockHttpServletRequest anonymous = new MockHttpServletRequest();

        assertEquals(HttpStatus.UNAUTHORIZED, refusal(() -> service.getFavorites(anonymous)));
    }

    @Test
    public void listsCollectionsAndRenderablePortletsOncePerPortlet() {
        final IUserLayoutFolderDescription news = collection("u12l1s7", "News");
        when(news.isMoveAllowed()).thenReturn(false);
        favorite("u12l1n20", "calendar", "20");
        favorite("u12l1n21", "secret", "21");
        favorite("u12l1n22", "calendar", "20");
        final Map<String, String> parameters = new HashMap<>();
        parameters.put(IPortletDefinition.ALT_MAX_LINK_PARAM, " https://mail.example.edu ");
        parameters.put("iconUrl", "/icons/mail.png");
        favoriteNodes.add(channel("u12l1n23", "mail", "23", parameters));
        when(principal.canRender("21")).thenReturn(false);

        final Favorites favorites = service.getFavorites(request);

        assertEquals(1, favorites.getCollections().size());
        final FavoriteCollection collection = favorites.getCollections().get(0);
        assertEquals("u12l1s7", collection.getNodeId());
        assertEquals("News", collection.getName());
        assertEquals("/uPortal/f/u12l1s7/render.uP", collection.getUrl());
        assertFalse(collection.isMoveAllowed());
        assertTrue(collection.isDeleteAllowed());

        assertEquals(2, favorites.getPortlets().size());
        final FavoritePortlet calendar = favorites.getPortlets().get(0);
        assertEquals("u12l1n20", calendar.getNodeId());
        assertEquals("calendar", calendar.getFname());
        assertEquals("Name of calendar", calendar.getName());
        assertEquals("About calendar", calendar.getDescription());
        assertEquals("/uPortal/p/calendar/render.uP", calendar.getUrl());
        assertNull(calendar.getTarget());
        assertNull(calendar.getIconUrl());
        assertTrue(calendar.isMoveAllowed());
        assertTrue(calendar.isDeleteAllowed());

        final FavoritePortlet mail = favorites.getPortlets().get(1);
        assertEquals("https://mail.example.edu", mail.getUrl());
        assertEquals("_blank", mail.getTarget());
        assertEquals("/icons/mail.png", mail.getIconUrl());

        assertNull(favorites.getMarketplaceUrl());
    }

    @Test
    public void keepsTheAlternativeLinksTarget() {
        final Map<String, String> parameters = new HashMap<>();
        parameters.put(IPortletDefinition.ALT_MAX_LINK_PARAM, "https://mail.example.edu");
        parameters.put(IPortletDefinition.ALT_MAX_LINK_TARGET_PARAM, "_self");
        final IUserLayoutChannelDescription mail = channel("u12l1n23", "mail", "23", parameters);
        when(mail.getDescription()).thenReturn("  ");
        favoriteNodes.add(mail);

        final FavoritePortlet favorite = service.getFavorites(request).getPortlets().get(0);

        assertEquals("_self", favorite.getTarget());
        assertNull(favorite.getDescription());
    }

    @Test
    public void ignoresChannelsWithoutParametersAndOtherNodes() {
        favoriteNodes.add(channel("u12l1n20", "calendar", "20", null));
        favoriteNodes.add(mock(IUserLayoutFolderDescription.class));

        final List<FavoritePortlet> portlets = service.getFavorites(request).getPortlets();

        assertEquals(1, portlets.size());
        assertNull(portlets.get(0).getIconUrl());
    }

    @Test
    public void linksToTheMarketplaceWhenThereIsOne() {
        service.setMarketplaceFname(" portletmarketplace ");
        assertEquals(
                "/uPortal/p/portletmarketplace/max/render.uP",
                service.getFavorites(request).getMarketplaceUrl());

        service.setMarketplaceFname("null");
        assertNull(service.getFavorites(request).getMarketplaceUrl());

        service.setMarketplaceFname("");
        assertNull(service.getFavorites(request).getMarketplaceUrl());
    }

    // addFavorite

    @Test
    public void addsAPortletToTheFirstColumnThatTakesIt() {
        definition("calendar", "20");
        when(layoutManager.getChildIds(FAVORITES_FOLDER))
                .thenReturn(Collections.enumeration(Arrays.asList("u12l1s5", "u12l1s6")));
        final IUserLayoutChannelDescription added =
                channel("u12l1n30", "calendar", "20", Collections.emptyMap());
        when(layoutManager.addNode(
                        any(IUserLayoutChannelDescription.class), eq("u12l1s5"), isNull()))
                .thenReturn(null);
        when(layoutManager.addNode(
                        any(IUserLayoutChannelDescription.class), eq("u12l1s6"), isNull()))
                .thenReturn(added);

        final FavoritesService.AddResult result = service.addFavorite(request, "calendar");

        assertTrue(result.isCreated());
        assertEquals("u12l1n30", result.getFavorite().getNodeId());
        verify(principal)
                .hasPermission(
                        IPermission.PORTAL_SYSTEM,
                        IPermission.PORTLET_FAVORITE_ACTIVITY,
                        IPermission.PORTLET_PREFIX + "20");
        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void makesAColumnWhenTheFavoritesFolderHasNone() {
        definition("calendar", "20");
        when(layoutManager.getChildIds(FAVORITES_FOLDER))
                .thenReturn(Collections.<String>emptyEnumeration());
        final IUserLayoutFolderDescription column = mock(IUserLayoutFolderDescription.class);
        when(column.getId()).thenReturn("u12l1s9");
        when(layoutManager.addNode(
                        any(IUserLayoutFolderDescription.class), eq(FAVORITES_FOLDER), isNull()))
                .thenReturn(column);
        final IUserLayoutChannelDescription added =
                channel("u12l1n30", "calendar", "20", Collections.emptyMap());
        when(layoutManager.addNode(
                        any(IUserLayoutChannelDescription.class), eq("u12l1s9"), isNull()))
                .thenReturn(added);

        assertEquals(
                "u12l1n30", service.addFavorite(request, "calendar").getFavorite().getNodeId());
        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void refusesWhenTheFavoritesFolderTakesNothing() {
        definition("calendar", "20");
        when(layoutManager.getChildIds(FAVORITES_FOLDER))
                .thenReturn(Collections.<String>emptyEnumeration());

        assertEquals(HttpStatus.FORBIDDEN, refusal(() -> service.addFavorite(request, "calendar")));

        when(layoutManager.getChildIds(FAVORITES_FOLDER))
                .thenReturn(Collections.enumeration(Collections.singletonList("u12l1s5")));
        assertEquals(HttpStatus.FORBIDDEN, refusal(() -> service.addFavorite(request, "calendar")));
        verify(layoutManager, never()).saveUserLayout();
    }

    @Test
    public void returnsAnExistingFavoriteInsteadOfAddingIt() {
        definition("calendar", "20");
        favorite("u12l1n20", "calendar", "20");

        final FavoritesService.AddResult result = service.addFavorite(request, "calendar");

        assertFalse(result.isCreated());
        assertEquals("u12l1n20", result.getFavorite().getNodeId());
        verify(layoutManager, never()).saveUserLayout();
    }

    @Test
    public void refusesUnknownPortlets() {
        assertEquals(HttpStatus.NOT_FOUND, refusal(() -> service.addFavorite(request, "nope")));
        assertEquals(HttpStatus.NOT_FOUND, refusal(() -> service.addFavorite(request, " ")));
        assertEquals(HttpStatus.NOT_FOUND, refusal(() -> service.addFavorite(request, null)));
    }

    @Test
    public void refusesPortletsTheUserMayNotFavorite() {
        definition("calendar", "20");
        when(principal.hasPermission(anyString(), anyString(), anyString())).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN, refusal(() -> service.addFavorite(request, "calendar")));

        when(principal.hasPermission(anyString(), anyString(), anyString())).thenReturn(true);
        when(principal.canRender("20")).thenReturn(false);
        assertEquals(HttpStatus.FORBIDDEN, refusal(() -> service.addFavorite(request, "calendar")));
    }

    @Test
    public void reportsALayoutWithoutAFavoritesFolder() {
        definition("calendar", "20");
        when(favoritesUtils.getFavoriteTabNodeId(layout)).thenReturn(null);

        assertEquals(HttpStatus.CONFLICT, refusal(() -> service.addFavorite(request, "calendar")));
    }

    @Test
    public void reportsALayoutThatCannotBeSaved() {
        definition("calendar", "20");
        when(layoutManager.getChildIds(FAVORITES_FOLDER))
                .thenReturn(Collections.enumeration(Collections.singletonList("u12l1s5")));
        final IUserLayoutChannelDescription added =
                channel("u12l1n30", "calendar", "20", Collections.emptyMap());
        when(layoutManager.addNode(
                        any(IUserLayoutChannelDescription.class), eq("u12l1s5"), isNull()))
                .thenReturn(added);
        doThrow(new PortalException("database down")).when(layoutManager).saveUserLayout();

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                refusal(() -> service.addFavorite(request, "calendar")));
    }

    // removeFavorite

    @Test
    public void removesEveryCopyOfAPortlet() {
        favorite("u12l1n20", "calendar", "20");
        favorite("u12l1n21", "mail", "21");
        favorite("u12l1n22", "calendar", "20");
        when(layoutManager.deleteNode(anyString())).thenReturn(true);

        service.removeFavorite(request, "u12l1n20");

        verify(layoutManager).deleteNode("u12l1n20");
        verify(layoutManager).deleteNode("u12l1n22");
        verify(layoutManager, never()).deleteNode("u12l1n21");
        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void removesACollection() {
        collection("u12l1s7", "News");
        when(layoutManager.deleteNode("u12l1s7")).thenReturn(true);

        service.removeFavorite(request, "u12l1s7");

        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void refusesToRemoveWhatIsNotAFavoriteOrIsLocked() {
        assertEquals(HttpStatus.NOT_FOUND, refusal(() -> service.removeFavorite(request, "u1")));

        final IUserLayoutChannelDescription locked = favorite("u12l1n20", "calendar", "20");
        when(locked.isDeleteAllowed()).thenReturn(false);
        assertEquals(
                HttpStatus.FORBIDDEN, refusal(() -> service.removeFavorite(request, "u12l1n20")));

        when(locked.isDeleteAllowed()).thenReturn(true);
        when(layoutManager.deleteNode("u12l1n20")).thenReturn(false);
        assertEquals(
                HttpStatus.FORBIDDEN, refusal(() -> service.removeFavorite(request, "u12l1n20")));
        verify(layoutManager, never()).saveUserLayout();
    }

    // moveFavorite

    @Test
    public void movesAFavoriteBeforeAnother() {
        favorite("u12l1n20", "calendar", "20");
        favorite("u12l1n21", "mail", "21");
        when(layoutManager.getParentId("u12l1n20")).thenReturn("u12l1s5");
        when(layoutManager.moveNode("u12l1n21", "u12l1s5", "u12l1n20")).thenReturn(true);

        service.moveFavorite(request, "u12l1n21", "u12l1n20", null);

        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void movesAFavoriteAfterAnother() {
        favorite("u12l1n20", "calendar", "20");
        favorite("u12l1n21", "mail", "21");
        favorite("u12l1n22", "news", "22");
        when(layoutManager.getParentId("u12l1n21")).thenReturn("u12l1s5");
        when(layoutManager.getNextSiblingId("u12l1n21")).thenReturn("u12l1n22");
        when(layoutManager.moveNode("u12l1n20", "u12l1s5", "u12l1n22")).thenReturn(true);

        service.moveFavorite(request, "u12l1n20", null, "u12l1n21");

        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void movesACollection() {
        collection("u12l1s7", "News");
        collection("u12l1s8", "Sports");
        when(layoutManager.getParentId("u12l1s7")).thenReturn("root");
        when(layoutManager.moveNode("u12l1s8", "root", "u12l1s7")).thenReturn(true);

        service.moveFavorite(request, "u12l1s8", "u12l1s7", null);

        verify(layoutManager).saveUserLayout();
    }

    @Test
    public void leavesAFavoriteThatIsAlreadyInPlace() {
        favorite("u12l1n20", "calendar", "20");
        favorite("u12l1n21", "mail", "21");
        when(layoutManager.getNextSiblingId("u12l1n20")).thenReturn("u12l1n21");

        service.moveFavorite(request, "u12l1n21", null, "u12l1n20");
        service.moveFavorite(request, "u12l1n21", "u12l1n21", null);

        verify(layoutManager, never()).moveNode(any(), any(), any());
        verify(layoutManager, never()).saveUserLayout();
    }

    @Test
    public void refusesInvalidMoves() {
        favorite("u12l1n20", "calendar", "20");
        final IUserLayoutChannelDescription mail = favorite("u12l1n21", "mail", "21");
        collection("u12l1s7", "News");

        assertEquals(
                HttpStatus.BAD_REQUEST,
                refusal(() -> service.moveFavorite(request, "u12l1n20", null, null)));
        assertEquals(
                HttpStatus.BAD_REQUEST,
                refusal(() -> service.moveFavorite(request, "u12l1n20", "u12l1n21", "u12l1n21")));
        assertEquals(
                HttpStatus.BAD_REQUEST,
                refusal(() -> service.moveFavorite(request, "u12l1n20", "u12l1s7", null)));
        assertEquals(
                HttpStatus.NOT_FOUND,
                refusal(() -> service.moveFavorite(request, "u12l1n20", "u9", null)));
        assertEquals(
                HttpStatus.NOT_FOUND,
                refusal(() -> service.moveFavorite(request, "u9", "u12l1n20", null)));

        when(mail.isMoveAllowed()).thenReturn(false);
        assertEquals(
                HttpStatus.FORBIDDEN,
                refusal(() -> service.moveFavorite(request, "u12l1n21", "u12l1n20", null)));

        when(mail.isMoveAllowed()).thenReturn(true);
        when(layoutManager.getParentId("u12l1n20")).thenReturn("u12l1s5");
        when(layoutManager.moveNode("u12l1n21", "u12l1s5", "u12l1n20")).thenReturn(false);
        assertEquals(
                HttpStatus.FORBIDDEN,
                refusal(() -> service.moveFavorite(request, "u12l1n21", "u12l1n20", null)));
        verify(layoutManager, never()).saveUserLayout();
    }
}
