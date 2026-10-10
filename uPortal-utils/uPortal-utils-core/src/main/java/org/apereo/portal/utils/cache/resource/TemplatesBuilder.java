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
package org.apereo.portal.utils.cache.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.IOUtils;
import org.apereo.portal.xml.ResourceLoaderURIResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/** Parses the provided input stream into a {@link Templates} object. */
@Service
public class TemplatesBuilder implements Loader<Templates>, ResourceLoaderAware {
    /**
     * JAXP API name of the JDK's {@code jdk.xml.enableExtensionFunctions} feature. uPortal's
     * stylesheets call Java helpers through the {@code http://xml.apache.org/xalan/java/}
     * namespace, and JDK 25 turns extension functions off by default. The JDK's built-in XSLTC
     * accepts this name on JDK 11 and later; it rejects the short {@code jdk.xml.*} name on JDK 11.
     */
    private static final String ENABLE_EXTENSION_FUNCTIONS =
            "http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ResourceLoader resourceLoader;
    private Map<String, Object> transformerAttributes;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void setTransformerAttributes(Map<String, Object> transformerAttributes) {
        this.transformerAttributes = transformerAttributes;
    }

    /* (non-Javadoc)
     * @see org.apereo.portal.utils.cache.resource.ResourceBuilder#buildResource(org.springframework.core.io.Resource, java.io.InputStream)
     */
    @Override
    public LoadedResource<Templates> loadResource(Resource resource) throws IOException {
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        enableExtensionFunctions(transformerFactory);

        if (this.transformerAttributes != null) {
            for (final Map.Entry<String, Object> attributeEntry :
                    this.transformerAttributes.entrySet()) {
                transformerFactory.setAttribute(attributeEntry.getKey(), attributeEntry.getValue());
            }
        }

        final ResourceTrackingURIResolver uriResolver =
                new ResourceTrackingURIResolver(this.resourceLoader);
        transformerFactory.setURIResolver(uriResolver);

        final URI uri = resource.getURI();
        final String systemId = uri.toString();

        final InputStream stream = resource.getInputStream();
        final Templates templates;
        try {
            final StreamSource source = new StreamSource(stream, systemId);
            templates = transformerFactory.newTemplates(source);
        } catch (TransformerConfigurationException e) {
            throw new IOException("Failed to parse stream into Templates", e);
        } finally {
            IOUtils.closeQuietly(stream);
        }

        final Map<Resource, Long> resolvedResources = uriResolver.getResolvedResources();

        return new LoadedResourceImpl<Templates>(templates, resolvedResources);
    }

    /**
     * Allow the stylesheets this builder compiles to call Java extension functions. Only this
     * feature is changed: secure processing, the XML limits and the external access settings keep
     * their defaults.
     */
    private void enableExtensionFunctions(TransformerFactory transformerFactory) {
        try {
            transformerFactory.setFeature(ENABLE_EXTENSION_FUNCTIONS, true);
        } catch (TransformerConfigurationException e) {
            // Not the JDK's XSLTC (for example Apache Xalan on the classpath), so the feature
            // does not apply and the processor's own extension function policy is used.
            logger.debug(
                    "{} does not support {}",
                    transformerFactory.getClass().getName(),
                    ENABLE_EXTENSION_FUNCTIONS,
                    e);
        }
    }

    private static class ResourceTrackingURIResolver extends ResourceLoaderURIResolver {
        private final Map<Resource, Long> resolvedResources = new LinkedHashMap<Resource, Long>();

        public ResourceTrackingURIResolver(ResourceLoader resourceLoader) {
            super(resourceLoader);
        }

        @Override
        protected Resource resolveResource(String href, String base) throws TransformerException {
            final Resource resource = super.resolveResource(href, base);

            long lastModified = 0;
            try {
                lastModified = resource.lastModified();
            } catch (IOException e) {
                // Ignore, not all resources can have a valid lastModified returned
            }
            resolvedResources.put(resource, lastModified);

            return resource;
        }

        public Map<Resource, Long> getResolvedResources() {
            return this.resolvedResources;
        }
    }
}
