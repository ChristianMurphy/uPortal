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
package org.apereo.portal.url;

import static org.junit.Assert.assertEquals;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** MOD-I180: a guest's deep link reaches the local login form with an ordinary refUrl. */
public class LocalLoginRefUrlEncoderTest {

    private LocalLoginRefUrlEncoder encoder;

    @Before
    public void setUp() {
        encoder = new LocalLoginRefUrlEncoder();
        encoder.setLoginUrl("/uPortal/p/login");
    }

    private static MockHttpServletRequest request(String uri, String queryString) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/uPortal");
        request.setQueryString(queryString);
        return request;
    }

    @Test
    public void appendsTheRequestedPageAsARefUrlParameter() throws Exception {
        assertEquals(
                "/uPortal/p/login?refUrl=%2FuPortal%2Fp%2Fuser-administration",
                encoder.encodeLoginAndRefUrl(request("/uPortal/p/user-administration", null)));
    }

    @Test
    public void encodesTheQueryStringOnce() throws Exception {
        final String encoded =
                encoder.encodeLoginAndRefUrl(
                        request(
                                "/uPortal/p/announcements/max/render.uP",
                                "pP_action=displayFullAnnouncement&pP_announcementId=1834"));
        assertEquals(
                "/uPortal/p/login?refUrl=%2FuPortal%2Fp%2Fannouncements%2Fmax%2Frender.uP"
                        + "%3FpP_action%3DdisplayFullAnnouncement%26pP_announcementId%3D1834",
                encoded);
        // The login portlet receives the page after a single decoding
        assertEquals(
                "/uPortal/p/announcements/max/render.uP"
                        + "?pP_action=displayFullAnnouncement&pP_announcementId=1834",
                URLDecoder.decode(
                        encoded.substring(encoded.indexOf("refUrl=") + 7), StandardCharsets.UTF_8));
    }

    @Test
    public void addsToALoginUrlThatHasAQueryString() throws Exception {
        encoder.setLoginUrl("/uPortal/p/login?profile=respondr");
        assertEquals(
                "/uPortal/p/login?profile=respondr&refUrl=%2FuPortal%2Fp%2Fuser-administration",
                encoder.encodeLoginAndRefUrl(request("/uPortal/p/user-administration", null)));
    }

    @Test
    public void appliesTheServerNameCustomizer() throws Exception {
        encoder.setLoginUrl("https://_CURRENT_SERVER_NAME_/uPortal/p/login");
        final UrlMultiServerNameCustomizer customizer = new UrlMultiServerNameCustomizer();
        customizer.setAllServerNames(new LinkedHashSet<>(Arrays.asList("myhost", "theirhost")));
        final UrlAuthCustomizerRegistry registry = new UrlAuthCustomizerRegistry();
        registry.setRegistry(List.of(customizer));
        encoder.setUrlCustomizer(registry);

        final MockHttpServletRequest request = request("/uPortal/p/user-administration", null);
        request.addHeader("Host", "theirhost");
        assertEquals(
                "https://theirhost/uPortal/p/login?refUrl=%2FuPortal%2Fp%2Fuser-administration",
                encoder.encodeLoginAndRefUrl(request));
    }
}
