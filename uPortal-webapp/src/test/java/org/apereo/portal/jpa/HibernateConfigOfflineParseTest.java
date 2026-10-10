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
package org.apereo.portal.jpa;

import static org.junit.Assert.assertNotNull;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.stax.WstxInputFactory;
import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.Test;

/**
 * Hibernate 4.3 reads the files named by {@code hibernate.ejb.cfgfile} through StAX, which resolves
 * to Woodstox on the portal classpath. Woodstox ignores Hibernate's local DTD resolver, so a
 * DOCTYPE with an http system id makes every portal start-up download the DTD from
 * www.hibernate.org, and start-up fails where that host is unreachable. These files must parse
 * without resolving any external entity.
 */
public class HibernateConfigOfflineParseTest {

    private static final String[] CONFIG_FILES = {
        "/properties/db/hibernate.cfg.xml",
        "/properties/db/hibernate-raw-events.cfg.xml",
        "/properties/db/hibernate-aggr-events.cfg.xml"
    };

    @Test
    public void configFilesParseWithoutExternalEntities() throws Exception {
        final XMLInputFactory factory = new WstxInputFactory();
        final XMLResolver failingResolver =
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException(
                            "External entity resolution attempted for " + systemId);
                };
        factory.setProperty(WstxInputProperties.P_DTD_RESOLVER, failingResolver);
        factory.setProperty(WstxInputProperties.P_ENTITY_RESOLVER, failingResolver);

        for (final String file : CONFIG_FILES) {
            try (InputStream in = getClass().getResourceAsStream(file)) {
                assertNotNull("Missing " + file, in);
                final XMLStreamReader reader = factory.createXMLStreamReader(in);
                try {
                    while (reader.hasNext()) {
                        reader.next();
                    }
                } finally {
                    reader.close();
                }
            }
        }
    }
}
