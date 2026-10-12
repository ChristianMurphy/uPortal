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
package org.apereo.portal.spring.security;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class RemoteUserSettingFilterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    /** The file is read as UTF-8 whatever the JVM's default charset (MOD-I275) */
    @Test
    public void readsTheUserNameAsUtf8() throws Exception {
        final File file = folder.newFile("remoteUser");
        Files.write(file.toPath(), "josé.núñez\n".getBytes(StandardCharsets.UTF_8));
        final MockFilterConfig config = new MockFilterConfig();
        config.addInitParameter("remoteUserFile", file.getAbsolutePath());
        final RemoteUserSettingFilter filter = new RemoteUserSettingFilter();
        filter.init(config);

        final AtomicReference<String> remoteUser = new AtomicReference<>();
        final FilterChain chain =
                (request, response) ->
                        remoteUser.set(((HttpServletRequest) request).getRemoteUser());
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertEquals("josé.núñez", remoteUser.get());
    }
}
