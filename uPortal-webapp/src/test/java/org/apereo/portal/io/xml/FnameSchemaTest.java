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
package org.apereo.portal.io.xml;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.apereo.portal.dao.usertype.FunctionalNameType;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * The import schema's fname type allows exactly what FunctionalNameType allows (MOD-I271), so an
 * import that passes the schema doesn't fail later in Java.
 */
public class FnameSchemaTest {
    private static final String COMMON_TYPES = "xsd/common-types-4.0.xsd";

    private static boolean schemaAccepts(String fname) throws Exception {
        final URL commonTypes = FnameSchemaTest.class.getClassLoader().getResource(COMMON_TYPES);
        final String wrapper =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'"
                        + " xmlns:up='https://source.jasig.org/schemas/uportal'"
                        + " targetNamespace='urn:fname-test' elementFormDefault='qualified'>"
                        + "<xs:import namespace='https://source.jasig.org/schemas/uportal'"
                        + " schemaLocation='"
                        + commonTypes
                        + "'/>"
                        + "<xs:element name='f' type='up:fname-type'/></xs:schema>";
        final Schema schema =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                        .newSchema(new StreamSource(new StringReader(wrapper)));
        try {
            schema.newValidator()
                    .validate(
                            new StreamSource(
                                    new StringReader(
                                            "<f xmlns='urn:fname-test'>" + fname + "</f>")));
            return true;
        } catch (SAXException e) {
            return false;
        }
    }

    @Test
    public void theSchemaAndJavaAgreeOnFnames() throws Exception {
        for (final String fname :
                new String[] {"weather", "uportal-links", "my_portlet_2", "A-Z_0-9"}) {
            assertTrue(fname, schemaAccepts(fname));
            assertTrue(fname, FunctionalNameType.isValid(fname));
        }
        for (final String fname : new String[] {"université", "北海道", "two words", "a.b", ""}) {
            assertFalse(fname, schemaAccepts(fname));
            assertFalse(fname, FunctionalNameType.isValid(fname));
        }
    }

    @Test
    public void theTwoCopiesOfTheSchemaAreTheSame() throws Exception {
        assertArrayEquals(
                Files.readAllBytes(Paths.get("src/main/resources", COMMON_TYPES)),
                Files.readAllBytes(
                        Paths.get(
                                "../uPortal-io/uPortal-io-jaxb/src/main/resources", COMMON_TYPES)));
    }
}
