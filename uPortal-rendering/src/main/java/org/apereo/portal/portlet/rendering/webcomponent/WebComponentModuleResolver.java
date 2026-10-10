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
package org.apereo.portal.portlet.rendering.webcomponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ServletContextAware;

/**
 * Turns the configured module of a web-component channel into the URL of its ES module script.
 *
 * <p>Modules bundled with uPortal are named by their entry name (for example {@code up-favorites}).
 * The build gives every file a content hash, so the URL changes whenever the code does and browsers
 * may cache the files for a long time; the build manifest ({@value #MANIFEST_PATH}, written by
 * Vite) maps each entry name to its current file. Absolute URLs and paths are returned unchanged.
 */
@Component
public class WebComponentModuleResolver implements ServletContextAware {

    /** Where the bundled modules are served, relative to the portal's context path. */
    public static final String BUNDLE_PATH = "/webcomponents/";

    /** The Vite build manifest of the bundled modules. */
    public static final String MANIFEST_PATH = BUNDLE_PATH + "manifest.json";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ServletContext servletContext;

    /**
     * Entry name to file, read from the manifest on first use; the manifest only changes on deploy.
     */
    private volatile Map<String, String> bundledModules;

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /**
     * @param request the portal request, for its context path
     * @param moduleUrl the configured module (see {@link WebComponentConfig#getModuleUrl()})
     * @return the URL to put in the module script's {@code src} attribute
     * @throws IllegalArgumentException if a bundled module name is not in the manifest
     */
    public String resolve(HttpServletRequest request, String moduleUrl) {
        if (!WebComponentConfig.BUNDLED_MODULE_NAME.matcher(moduleUrl).matches()) {
            return moduleUrl;
        }
        final String file = getBundledModules().get(moduleUrl);
        if (file == null) {
            throw new IllegalArgumentException(
                    "No web-component module named '"
                            + moduleUrl
                            + "' is bundled with uPortal (see "
                            + MANIFEST_PATH
                            + ")");
        }
        return request.getContextPath() + BUNDLE_PATH + file;
    }

    private Map<String, String> getBundledModules() {
        Map<String, String> result = bundledModules;
        if (result == null) {
            result = readManifest();
            bundledModules = result;
        }
        return result;
    }

    private Map<String, String> readManifest() {
        try (InputStream manifest = servletContext.getResourceAsStream(MANIFEST_PATH)) {
            if (manifest == null) {
                logger.warn(
                        "{} is missing, so no bundled web-component module can be resolved",
                        MANIFEST_PATH);
                return Collections.emptyMap();
            }
            final Map<String, String> result = new HashMap<>();
            final JsonNode chunks = objectMapper.readTree(manifest);
            for (final Iterator<JsonNode> it = chunks.elements(); it.hasNext(); ) {
                final JsonNode chunk = it.next();
                if (chunk.path("isEntry").asBoolean(false)
                        && chunk.hasNonNull("name")
                        && chunk.hasNonNull("file")) {
                    result.put(chunk.get("name").asText(), chunk.get("file").asText());
                }
            }
            logger.info(
                    "Read {} bundled web-component modules from {}", result.size(), MANIFEST_PATH);
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + MANIFEST_PATH, e);
        }
    }
}
