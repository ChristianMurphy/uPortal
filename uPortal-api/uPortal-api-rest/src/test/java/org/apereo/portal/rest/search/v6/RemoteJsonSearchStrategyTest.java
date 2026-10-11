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
package org.apereo.portal.rest.search.v6;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.Cookie;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/** The remote JSON provider against a real HTTP server on the loopback interface. */
public class RemoteJsonSearchStrategyTest {

    private static final String GOOGLE_STYLE =
            "{\"items\":["
                    + "{\"title\":\"Library hours\",\"link\":\"https://example.edu/library\","
                    + "\"snippet\":\"Open 8 to 10\"},"
                    + "{\"title\":\"  \",\"link\":\"https://example.edu/blank\"},"
                    + "{\"title\":\"No link\",\"link\":\"javascript:alert(1)\"},"
                    + "{\"title\":\"Gym\",\"link\":\"http://example.edu/gym\"}"
                    + "]}";

    /** What the server received, and how it answers. */
    private static final class Exchange {
        volatile String query;
        volatile Headers headers;
        volatile int status = 200;
        volatile String body = GOOGLE_STYLE;
        volatile long delayMillis;
        volatile String location;
    }

    private HttpServer server;
    private final Exchange exchange = new Exchange();
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::answer);
        server.start();
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    private void answer(HttpExchange http) throws IOException {
        requests.add(http.getRequestURI().getPath());
        exchange.query = http.getRequestURI().getRawQuery();
        exchange.headers = http.getRequestHeaders();
        try {
            Thread.sleep(exchange.delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (exchange.location != null) {
            http.getResponseHeaders().add("Location", exchange.location);
        }
        final byte[] bytes = exchange.body.getBytes(StandardCharsets.UTF_8);
        http.sendResponseHeaders(exchange.status, bytes.length);
        try (OutputStream out = http.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private RemoteJsonSearchStrategy strategy(Map<String, String> headers, Duration timeout) {
        return new RemoteJsonSearchStrategy(
                new RemoteJsonSearchSettings(
                        "web",
                        "Campus web",
                        base() + "/search?key=k1&q={query}",
                        headers,
                        "/items",
                        "/title",
                        "/link",
                        "/snippet",
                        timeout));
    }

    private RemoteJsonSearchStrategy strategy() {
        return strategy(Collections.emptyMap(), Duration.ofSeconds(5));
    }

    /** A request as the browser sends it: with the session cookie and a session. */
    private static MockHttpServletRequest userRequest() {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("JSESSIONID", "secret-session"));
        request.addHeader("Cookie", "JSESSIONID=secret-session");
        request.addHeader("Authorization", "Bearer user-token");
        request.setSession(new MockHttpSession());
        return request;
    }

    @Test
    public void mapsTheResultsAndLeavesOutUnusableOnes() {
        final List<?> results = strategy().search("library", userRequest());

        assertEquals(3, results.size());
        final Map<String, String> library = new LinkedHashMap<>();
        library.put("title", "Library hours");
        library.put("url", "https://example.edu/library");
        library.put("description", "Open 8 to 10");
        assertEquals(library, results.get(0));
        // A link that is not http(s) is dropped, the result kept
        assertEquals(Collections.singletonMap("title", "No link"), results.get(1));
        assertEquals("http://example.edu/gym", ((Map<?, ?>) results.get(2)).get("url"));
    }

    @Test
    public void encodesTheQueryIntoTheUrl() {
        strategy().search("tea & cake/ü", userRequest());

        assertEquals("key=k1&q=tea+%26+cake%2F%C3%BC", exchange.query);
        assertEquals(Collections.singletonList("/search"), requests);
    }

    @Test
    public void sendsAUnicodeQueryEncodedOnceAndReadsUnicodeResults() {
        final String query = SearchServiceTest.UNICODE_QUERY;
        exchange.body =
                "{\"items\": [{\"title\": \""
                        + query
                        + "\", \"link\": \"https://example.edu/z\", \"snippet\": \"\u674e\"}]}";

        final List<?> results = strategy().search(query, userRequest());

        // Encoded once as UTF-8: decoding once gives the query back, and no %25 (an encoded %)
        final String sent = exchange.query.substring(exchange.query.indexOf("&q=") + 3);
        assertFalse(sent, sent.contains("%25"));
        assertEquals(query, URLDecoder.decode(sent, StandardCharsets.UTF_8));
        assertEquals(query, ((Map<?, ?>) results.get(0)).get("title"));
        assertEquals("\u674e", ((Map<?, ?>) results.get(0)).get("description"));
    }

    @Test
    public void sendsTheConfiguredHeadersAndNothingOfTheUser() {
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Api-Key", "abc");
        strategy(headers, Duration.ofSeconds(5)).search("x", userRequest());

        assertEquals("abc", exchange.headers.getFirst("X-Api-Key"));
        assertEquals("application/json", exchange.headers.getFirst("Accept"));
        assertFalse(exchange.headers.containsKey("Cookie"));
        assertFalse(exchange.headers.containsKey("Authorization"));
        for (List<String> values : exchange.headers.values()) {
            for (String value : values) {
                assertFalse(value, value.contains("secret-session"));
            }
        }
    }

    @Test
    public void treatsMissingResultsAsNone() {
        exchange.body = "{\"searchInformation\":{\"totalResults\":\"0\"}}";

        assertTrue(strategy().search("x", userRequest()).isEmpty());

        exchange.body = "{\"items\":null}";
        assertTrue(strategy().search("x", userRequest()).isEmpty());
    }

    @Test
    public void readsOtherFieldMappings() {
        exchange.body =
                "{\"data\":{\"hits\":[{\"name\":{\"full\":\"Ann Lee\"},\"profile\":"
                        + "\"https://dir.example.edu/ann\",\"dept\":\"Physics\"}]}}";
        final RemoteJsonSearchStrategy directory =
                new RemoteJsonSearchStrategy(
                        new RemoteJsonSearchSettings(
                                "directory",
                                "directory",
                                base() + "/people?q={query}",
                                Collections.emptyMap(),
                                "/data/hits",
                                "/name/full",
                                "/profile",
                                "/dept",
                                Duration.ofSeconds(5)));

        final Map<?, ?> ann = (Map<?, ?>) directory.search("ann", userRequest()).get(0);

        assertEquals("Ann Lee", ann.get("title"));
        assertEquals("https://dir.example.edu/ann", ann.get("url"));
        assertEquals("Physics", ann.get("description"));
        assertEquals("directory", directory.getResultTypeName());
    }

    @Test
    public void failsOnAnErrorStatus() {
        exchange.status = 500;

        expectFailure("answered 500");
    }

    @Test
    public void failsOnABodyThatIsNotJson() {
        exchange.body = "<html>Service unavailable</html>";

        expectFailure("not JSON");
    }

    @Test
    public void failsWhenTheResultsAreNotAnArray() {
        exchange.body = "{\"items\":{\"title\":\"x\"}}";

        expectFailure("/items is not an array");
    }

    @Test
    public void doesNotFollowRedirects() {
        exchange.status = 302;
        exchange.location = base() + "/elsewhere";

        expectFailure("answered 302");
        assertEquals(Collections.singletonList("/search"), requests);
    }

    @Test
    public void givesUpAfterTheTimeout() {
        exchange.delayMillis = 3000;
        final long start = System.nanoTime();

        final Map<String, String> headers = Collections.singletonMap("X-Api-Key", "secret-key");
        try {
            strategy(headers, Duration.ofMillis(200)).search("x", userRequest());
            fail("No timeout");
        } catch (RemoteSearchException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("web: "));
            assertKeepsSecrets(e);
        }
        assertTrue(
                "took " + (System.nanoTime() - start) / 1_000_000 + " ms",
                System.nanoTime() - start < 2_000_000_000L);
    }

    @Test
    public void failsWhenNothingListens() {
        server.stop(0);

        try {
            strategy().search("x", userRequest());
            fail("Searched without a server");
        } catch (RemoteSearchException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("web: "));
            assertKeepsSecrets(e);
        }
    }

    private void expectFailure(String message) {
        try {
            strategy(Collections.singletonMap("X-Api-Key", "secret-key"), Duration.ofSeconds(5))
                    .search("x", userRequest());
            fail("Expected a failure with " + message);
        } catch (RemoteSearchException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
            assertKeepsSecrets(e);
        }
    }

    /** Neither the URL's API key nor a header value appears in the error or its causes. */
    private static void assertKeepsSecrets(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            final String text = String.valueOf(e.getMessage());
            assertFalse(text, text.contains("key=k1"));
            assertFalse(text, text.contains("secret-key"));
            assertFalse(text, text.contains("127.0.0.1"));
        }
    }
}
