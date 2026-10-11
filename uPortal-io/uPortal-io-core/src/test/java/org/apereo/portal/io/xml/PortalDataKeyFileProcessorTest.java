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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import javax.xml.namespace.QName;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Checks what {@link PortalDataKeyFileProcessor} logs for a data file that no data type claims: one
 * warning that names the file, and the keys of every data type only at debug level.
 */
public class PortalDataKeyFileProcessorTest {
    private static final String WARNING =
            "No IPortalDataType configured for {}, the resource will be ignored: {}";

    private Logger logger;
    private PortalDataKeyFileProcessor processor;
    private final Resource unknownFile =
            new ByteArrayResource("<portlet-only-data/>".getBytes(UTF_8), "unknown.xml");

    @Before
    public void setUp() {
        logger = mock(Logger.class);
        final Map<PortalDataKey, IPortalDataType> knownTypes =
                Collections.singletonMap(
                        new PortalDataKey(new QName("user"), null, "4.0"),
                        mock(IPortalDataType.class));
        // The processor keeps the logger LoggerFactory gives it when it is constructed
        try (MockedStatic<LoggerFactory> loggerFactory = mockStatic(LoggerFactory.class)) {
            loggerFactory.when(() -> LoggerFactory.getLogger(any(Class.class))).thenReturn(logger);
            processor = new PortalDataKeyFileProcessor(knownTypes, null);
        }
    }

    @Test
    public void testUnknownFileIsOneWarningWithoutTheKeys() {
        assertNull(processor.apply(unknownFile));

        verify(logger).warn(eq(WARNING), any(Object.class), eq(unknownFile));
        verify(logger, never()).debug(any(String.class), any(Object.class));
    }

    @Test
    public void testUnknownFileListsTheKeysAtDebugLevel() {
        when(logger.isDebugEnabled()).thenReturn(true);

        assertNull(processor.apply(unknownFile));

        verify(logger).warn(eq(WARNING), any(Object.class), eq(unknownFile));
        verify(logger)
                .debug(
                        eq("{}"),
                        argThat(
                                (Object keys) ->
                                        keys.toString().contains("Potential Keys To Match")
                                                && keys.toString().contains("user")));
    }
}
