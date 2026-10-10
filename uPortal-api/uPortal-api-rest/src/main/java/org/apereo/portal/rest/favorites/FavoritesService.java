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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apereo.portal.PortalException;
import org.apereo.portal.layout.IUserLayout;
import org.apereo.portal.layout.IUserLayoutManager;
import org.apereo.portal.layout.node.IUserLayoutChannelDescription;
import org.apereo.portal.layout.node.IUserLayoutFolderDescription;
import org.apereo.portal.layout.node.IUserLayoutNodeDescription;
import org.apereo.portal.layout.node.UserLayoutChannelDescription;
import org.apereo.portal.layout.node.UserLayoutFolderDescription;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.apereo.portal.portlets.favorites.FavoritesUtils;
import org.apereo.portal.security.IAuthorizationPrincipal;
import org.apereo.portal.security.IAuthorizationService;
import org.apereo.portal.security.IPermission;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.IPersonManager;
import org.apereo.portal.security.PermissionHelper;
import org.apereo.portal.user.IUserInstanceManager;
import org.apereo.portal.utils.personalize.IPersonalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The current user's favorites, read from and written to their layout.
 *
 * <p>Favorite portlets are the channels in the layout's {@code favorites} folder, favorite
 * collections the root folders of type {@code favorite_collection} (see {@link FavoritesUtils}).
 * Every change goes through the user's layout manager, so the distributed layout manager's
 * restrictions (locked fragments) apply, and is saved before the method returns.
 */
@Service
public class FavoritesService {

    private static final Logger logger = LoggerFactory.getLogger(FavoritesService.class);

    /** The outcome of {@link #addFavorite}. */
    public static final class AddResult {
        private final FavoritePortlet favorite;
        private final boolean created;

        AddResult(FavoritePortlet favorite, boolean created) {
            this.favorite = favorite;
            this.created = created;
        }

        public FavoritePortlet getFavorite() {
            return favorite;
        }

        /** False when the portlet already was a favorite. */
        public boolean isCreated() {
            return created;
        }
    }

    private final IUserInstanceManager userInstanceManager;
    private final IPersonManager personManager;
    private final IAuthorizationService authorizationService;
    private final IPortletDefinitionRegistry portletDefinitionRegistry;
    private final FavoritesUtils favoritesUtils;
    private final IPersonalizer personalizer;
    private String marketplaceFname;

    @Autowired
    public FavoritesService(
            IUserInstanceManager userInstanceManager,
            IPersonManager personManager,
            IAuthorizationService authorizationService,
            IPortletDefinitionRegistry portletDefinitionRegistry,
            FavoritesUtils favoritesUtils,
            IPersonalizer personalizer) {
        this.userInstanceManager = userInstanceManager;
        this.personManager = personManager;
        this.authorizationService = authorizationService;
        this.portletDefinitionRegistry = portletDefinitionRegistry;
        this.favoritesUtils = favoritesUtils;
        this.personalizer = personalizer;
    }

    /**
     * The functional name of the portlet where users find more favorites (the marketplace), or
     * null; the same property the Favorites portlet reads.
     */
    @Value("${org.apereo.portal.portlets.favorites.MarketplaceFunctionalName:null}")
    public void setMarketplaceFname(String marketplaceFname) {
        this.marketplaceFname =
                StringUtils.isBlank(marketplaceFname) || "null".equalsIgnoreCase(marketplaceFname)
                        ? null
                        : marketplaceFname.trim();
    }

    /** The user's favorites; portlets the user may not render are left out. */
    public Favorites getFavorites(HttpServletRequest request) {
        final IPerson person = requireUser(request);
        final IUserLayout layout = getLayoutManager(request).getUserLayout();
        final String contextPath = request.getContextPath();

        final List<FavoriteCollection> collections = new ArrayList<>();
        for (final IUserLayoutNodeDescription node :
                favoritesUtils.getFavoriteCollections(layout)) {
            collections.add(
                    new FavoriteCollection(
                            node.getId(),
                            node.getName(),
                            contextPath + "/f/" + node.getId() + "/render.uP",
                            node.isMoveAllowed(),
                            node.isDeleteAllowed()));
        }

        final IAuthorizationPrincipal principal = getPrincipal(person);
        final List<IUserLayoutChannelDescription> channels = new ArrayList<>();
        for (final IUserLayoutChannelDescription channel : getFavoriteChannels(layout)) {
            if (principal.canRender(channel.getChannelPublishId())) {
                channels.add(channel);
            }
        }
        final List<FavoritePortlet> portlets = new ArrayList<>();
        for (final IUserLayoutChannelDescription channel :
                FavoritesUtils.filterChannelFavoritesToUnique(channels)) {
            portlets.add(toFavoritePortlet(channel, contextPath));
        }

        final String marketplaceUrl =
                marketplaceFname == null
                        ? null
                        : contextPath + "/p/" + marketplaceFname + "/max/render.uP";
        return new Favorites(collections, portlets, marketplaceUrl);
    }

    /** Adds the portlet to the user's favorites, unless it already is one. */
    public AddResult addFavorite(HttpServletRequest request, String fname) {
        final IPerson person = requireUser(request);
        final IPortletDefinition portletDefinition =
                StringUtils.isBlank(fname)
                        ? null
                        : portletDefinitionRegistry.getPortletDefinitionByFname(fname);
        if (portletDefinition == null) {
            throw new FavoritesException(HttpStatus.NOT_FOUND, "No portlet named '" + fname + "'");
        }
        final IAuthorizationPrincipal principal = getPrincipal(person);
        if (!principal.canRender(portletDefinition.getPortletDefinitionId().getStringId())
                || !principal.hasPermission(
                        IPermission.PORTAL_SYSTEM,
                        IPermission.PORTLET_FAVORITE_ACTIVITY,
                        PermissionHelper.permissionTargetIdForPortletDefinition(
                                portletDefinition))) {
            logger.warn("User '{}' may not favorite portlet '{}'", person.getUserName(), fname);
            throw new FavoritesException(HttpStatus.FORBIDDEN, "Favorite not permitted");
        }

        final IUserLayoutManager layoutManager = getLayoutManager(request);
        final String contextPath = request.getContextPath();
        for (final IUserLayoutChannelDescription channel :
                getFavoriteChannels(layoutManager.getUserLayout())) {
            if (fname.equals(channel.getFunctionalName())) {
                return new AddResult(toFavoritePortlet(channel, contextPath), false);
            }
        }

        final String favoritesFolderId =
                favoritesUtils.getFavoriteTabNodeId(layoutManager.getUserLayout());
        if (favoritesFolderId == null) {
            throw new FavoritesException(HttpStatus.CONFLICT, "The layout has no favorites folder");
        }
        final IUserLayoutChannelDescription channel =
                new UserLayoutChannelDescription(
                        person, portletDefinition, request.getSession(), personalizer);
        final IUserLayoutNodeDescription added =
                addToFolder(layoutManager, channel, favoritesFolderId);
        if (added == null) {
            throw new FavoritesException(
                    HttpStatus.FORBIDDEN, "The favorites folder takes no more portlets");
        }
        save(layoutManager);
        return new AddResult(
                toFavoritePortlet((IUserLayoutChannelDescription) added, contextPath), true);
    }

    /**
     * Removes a favorite collection or portlet; for a portlet, also every other copy of it in the
     * favorites folder.
     */
    public void removeFavorite(HttpServletRequest request, String nodeId) {
        requireUser(request);
        final IUserLayoutManager layoutManager = getLayoutManager(request);
        final IUserLayout layout = layoutManager.getUserLayout();
        final IUserLayoutNodeDescription node = findFavorite(layout, nodeId);
        if (!node.isDeleteAllowed()) {
            throw new FavoritesException(HttpStatus.FORBIDDEN, "The favorite may not be removed");
        }

        final List<String> nodeIds = new ArrayList<>();
        if (node instanceof IUserLayoutChannelDescription) {
            final String fname = ((IUserLayoutChannelDescription) node).getFunctionalName();
            for (final IUserLayoutChannelDescription channel : getFavoriteChannels(layout)) {
                if (Objects.equals(fname, channel.getFunctionalName())) {
                    nodeIds.add(channel.getId());
                }
            }
        } else {
            nodeIds.add(nodeId);
        }
        for (final String id : nodeIds) {
            if (!layoutManager.deleteNode(id)) {
                throw new FavoritesException(
                        HttpStatus.FORBIDDEN, "The layout refused to remove the favorite");
            }
        }
        save(layoutManager);
    }

    /**
     * Moves a favorite just before or just after another favorite of the same kind (portlet or
     * collection). Exactly one of {@code before} and {@code after} must be given.
     */
    public void moveFavorite(
            HttpServletRequest request, String nodeId, String before, String after) {
        requireUser(request);
        if ((before == null) == (after == null)) {
            throw new FavoritesException(
                    HttpStatus.BAD_REQUEST, "Give exactly one of 'before' and 'after'");
        }
        final IUserLayoutManager layoutManager = getLayoutManager(request);
        final IUserLayout layout = layoutManager.getUserLayout();
        final IUserLayoutNodeDescription node = findFavorite(layout, nodeId);
        final String targetId = before != null ? before : after;
        final IUserLayoutNodeDescription target = findFavorite(layout, targetId);
        if (node.getType() != target.getType()) {
            throw new FavoritesException(
                    HttpStatus.BAD_REQUEST,
                    "A favorite moves among favorites of its own kind (portlet or collection)");
        }
        if (!node.isMoveAllowed()) {
            throw new FavoritesException(HttpStatus.FORBIDDEN, "The favorite may not be moved");
        }

        final String nextSiblingId =
                before != null ? before : layoutManager.getNextSiblingId(after);
        if (nodeId.equals(nextSiblingId) || nodeId.equals(targetId)) {
            return; // Already in place
        }
        if (!layoutManager.moveNode(nodeId, layoutManager.getParentId(targetId), nextSiblingId)) {
            throw new FavoritesException(
                    HttpStatus.FORBIDDEN, "The layout refused to move the favorite");
        }
        save(layoutManager);
    }

    private IPerson requireUser(HttpServletRequest request) {
        final IPerson person = personManager.getPerson(request);
        if (person == null || person.isGuest()) {
            throw new FavoritesException(HttpStatus.UNAUTHORIZED, "Sign in to use favorites");
        }
        return person;
    }

    private IAuthorizationPrincipal getPrincipal(IPerson person) {
        return authorizationService.newPrincipal(person.getUserName(), IPerson.class);
    }

    private IUserLayoutManager getLayoutManager(HttpServletRequest request) {
        return userInstanceManager
                .getUserInstance(request)
                .getPreferencesManager()
                .getUserLayoutManager();
    }

    private List<IUserLayoutChannelDescription> getFavoriteChannels(IUserLayout layout) {
        final List<IUserLayoutChannelDescription> result = new ArrayList<>();
        for (final IUserLayoutNodeDescription node :
                favoritesUtils.getFavoritePortletLayoutNodes(layout)) {
            if (node instanceof IUserLayoutChannelDescription) {
                result.add((IUserLayoutChannelDescription) node);
            }
        }
        return result;
    }

    /** The favorite collection or portlet with the node ID; 404 for any other node. */
    private IUserLayoutNodeDescription findFavorite(IUserLayout layout, String nodeId) {
        for (final IUserLayoutNodeDescription node :
                favoritesUtils.getFavoriteCollections(layout)) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        for (final IUserLayoutChannelDescription channel : getFavoriteChannels(layout)) {
            if (channel.getId().equals(nodeId)) {
                return channel;
            }
        }
        throw new FavoritesException(HttpStatus.NOT_FOUND, "No favorite with node " + nodeId);
    }

    /** Adds the channel to the first column of the folder that takes it, making one if needed. */
    private static IUserLayoutNodeDescription addToFolder(
            IUserLayoutManager layoutManager,
            IUserLayoutChannelDescription channel,
            String folderId) {
        final Enumeration<String> columns = layoutManager.getChildIds(folderId);
        if (!columns.hasMoreElements()) {
            final IUserLayoutFolderDescription column = new UserLayoutFolderDescription();
            column.setName("Column");
            column.setId("tbd");
            column.setFolderType(IUserLayoutFolderDescription.REGULAR_TYPE);
            column.setHidden(false);
            column.setUnremovable(false);
            column.setImmutable(false);
            final IUserLayoutNodeDescription added = layoutManager.addNode(column, folderId, null);
            return added == null ? null : layoutManager.addNode(channel, added.getId(), null);
        }
        while (columns.hasMoreElements()) {
            final IUserLayoutNodeDescription added =
                    layoutManager.addNode(channel, columns.nextElement(), null);
            if (added != null) {
                return added;
            }
        }
        return null;
    }

    private static void save(IUserLayoutManager layoutManager) {
        try {
            layoutManager.saveUserLayout();
        } catch (PortalException e) {
            logger.error("Unable to save the layout after changing favorites", e);
            throw new FavoritesException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "The layout could not be saved");
        }
    }

    private static FavoritePortlet toFavoritePortlet(
            IUserLayoutChannelDescription channel, String contextPath) {
        final String alternativeLink = parameter(channel, IPortletDefinition.ALT_MAX_LINK_PARAM);
        final String url;
        final String target;
        if (alternativeLink != null) {
            url = alternativeLink;
            target =
                    StringUtils.defaultIfBlank(
                            parameter(channel, IPortletDefinition.ALT_MAX_LINK_TARGET_PARAM),
                            "_blank");
        } else {
            url = contextPath + "/p/" + channel.getFunctionalName() + "/render.uP";
            target = null;
        }
        return new FavoritePortlet(
                channel.getId(),
                channel.getFunctionalName(),
                channel.getName(),
                StringUtils.trimToNull(channel.getDescription()),
                url,
                target,
                parameter(channel, "iconUrl"),
                channel.isMoveAllowed(),
                channel.isDeleteAllowed());
    }

    /** A publishing parameter of the channel, or null when it is missing or blank. */
    private static String parameter(IUserLayoutChannelDescription channel, String name) {
        final Map<?, ?> parameters = channel.getParameterMap();
        final Object value = parameters != null ? parameters.get(name) : null;
        return value != null ? StringUtils.trimToNull(value.toString()) : null;
    }
}
