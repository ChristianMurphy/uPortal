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

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Removes the text that jqwik's engine prints for AI agents before running properties (MOD-D39,
 * MOD-I179). The owner treats that text as prompt injection: it addresses agents and tells them to
 * ignore test results. This listener wraps the test JVM's standard output when the JUnit Platform
 * session opens, before any engine runs, and drops whatever jqwik prints from the one method that
 * prints that text. All other output, jqwik's own reports included, passes through.
 *
 * <p>Registered in {@code META-INF/services/org.junit.platform.launcher.LauncherSessionListener},
 * which the unit tests and the integration tests share.
 */
public final class JqwikAgentNoticeFilter implements LauncherSessionListener {

    static final String JQWIK_EXECUTOR = "net.jqwik.engine.execution.JqwikExecutor";
    static final String NOTICE_METHOD = "printMessageForCodingAgents";

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        install();
    }

    static void install() {
        if (!(System.out instanceof OutputFromMethodFilter)) {
            System.setOut(new OutputFromMethodFilter(System.out, JQWIK_EXECUTOR, NOTICE_METHOD));
        }
    }
}
