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
package org.apereo.portal.dbmigrate.testsupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class OutputFromMethodFilterTest {

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final OutputFromMethodFilter filter =
            new OutputFromMethodFilter(
                    new PrintStream(captured, true, StandardCharsets.UTF_8),
                    OutputFromMethodFilterTest.class.getName(),
                    "printFromFilteredMethod");

    private void printFromFilteredMethod(String text) {
        filter.print(text);
        filter.println(text);
    }

    private void printFromAnotherMethod(String text) {
        filter.print(text);
        filter.println(text);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void dropsTextPrintedFromTheFilteredMethod() {
        printFromFilteredMethod("dropped");
        assertEquals("", output());
    }

    @Test
    public void passesTextPrintedFromOtherMethods() {
        printFromAnotherMethod("kept");
        assertEquals("kept" + "kept" + System.lineSeparator(), output());
    }

    @Test
    public void installWrapsStandardOutputOnceForJqwiksNoticeMethod() {
        PrintStream original = System.out;
        try {
            JqwikAgentNoticeFilter.install();
            JqwikAgentNoticeFilter.install();
            assertTrue(System.out instanceof OutputFromMethodFilter);
            OutputFromMethodFilter installed = (OutputFromMethodFilter) System.out;
            assertEquals(JqwikAgentNoticeFilter.JQWIK_EXECUTOR, installed.className());
            assertEquals(JqwikAgentNoticeFilter.NOTICE_METHOD, installed.methodName());
        } finally {
            System.setOut(original);
        }
    }
}
