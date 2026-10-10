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
package org.apereo.portal.portlets.webcomponent;

import javax.portlet.GenericPortlet;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

/**
 * The portlet behind the {@code WebComponent} descriptor in {@code WEB-INF/portlet.xml}.
 *
 * <p>Web-component channels are rendered by the portal itself ({@code
 * WebComponentChannelRenderer}), never by the portlet container. The portal's window and entity
 * registries still key every channel on a portlet descriptor, and the container needs a class for
 * each descriptor, so this one exists and renders nothing. Web-component channels have no portlet
 * actions, events or resources: their elements call REST APIs instead.
 */
public class WebComponentPortlet extends GenericPortlet {

    @Override
    protected void doView(RenderRequest request, RenderResponse response) {
        // Intentionally empty: see the class comment.
    }
}
