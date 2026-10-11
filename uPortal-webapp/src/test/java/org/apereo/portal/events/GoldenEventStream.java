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
package org.apereo.portal.events;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import javax.xml.namespace.QName;
import org.apereo.portal.mock.portlet.om.MockPortletWindowId;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.provider.PersonImpl;
import org.apereo.portal.url.UrlState;
import org.apereo.portal.url.UrlType;
import org.springframework.context.ApplicationEvent;

/**
 * A deterministic stream of raw portal events for the analytics goldens (MOD-D29 item 3): visits by
 * students, faculty, staff and a guest, each a login, tab renders, portlet renders, actions,
 * resource and event requests, a search, layout changes and (usually) a logout. The visits are
 * placed on the boundaries that time-zone and time-library changes are most likely to move: month,
 * quarter and year ends in UTC and in New York, and the New York DST changes (the repeated hour in
 * November, the skipped hour in March).
 *
 * <p>In this package because the event constructors are package-private.
 */
public final class GoldenEventStream {
    /** A group of visits aggregated on its own, so the closing of intervals stays short */
    public enum Burst {
        /** Oct 31 to Nov 3 2025: month end in UTC and New York, then New York falls back */
        AUTUMN(
                "2025-10-31T23:58:00Z",
                "2025-11-01T00:01:00Z",
                "2025-11-01T03:57:00Z",
                "2025-11-01T04:02:00Z",
                "2025-11-02T04:58:00Z",
                "2025-11-02T05:25:00Z",
                "2025-11-02T05:55:00Z",
                "2025-11-02T06:10:00Z",
                "2025-11-02T06:40:00Z",
                "2025-11-02T07:05:00Z",
                "2025-11-03T04:57:00Z",
                "2025-11-03T05:03:00Z"),
        /** Dec 31 2025 to Jan 1 2026: year and quarter end in UTC, then in New York */
        YEAR_END(
                "2025-12-31T23:55:00Z",
                "2026-01-01T00:02:00Z",
                "2026-01-01T04:56:00Z",
                "2026-01-01T05:04:00Z"),
        /** Mar 8 2026: day end in New York, then New York skips 02:00-03:00 */
        SPRING(
                "2026-03-08T04:57:00Z",
                "2026-03-08T05:03:00Z",
                "2026-03-08T06:50:00Z",
                "2026-03-08T07:05:00Z",
                "2026-03-08T07:40:00Z");

        private final List<Instant> visits = new ArrayList<>();

        Burst(String... visits) {
            for (final String visit : visits) {
                this.visits.add(Instant.parse(visit));
            }
        }

        public List<Instant> visits() {
            return Collections.unmodifiableList(visits);
        }
    }

    /** User name, portal user id and the group keys its login carries (null: no login) */
    private static final String[][] USERS = {
        {"student1", "101", "local.Everyone", "local.Students"},
        {"faculty1", "201", "local.Everyone", "local.Faculty"},
        {"student2", "102", "local.Everyone", "local.Students"},
        {"staff1", "301", "local.Everyone"},
        {"guest", "1", null},
        {"student3", "103", "local.Everyone", "local.Students"},
        {"faculty2", "202", "local.Everyone", "local.Faculty"},
    };

    /** DLM tab ids of fragment owners 5 and 6, an unknown DLM tab, a personal tab, no tab */
    static final String[] TABS = {"u5l1s10", "u6l1s20", "u7l1s30", "s99", null};

    private static final String[] PORTLETS = {"weather", "calendar", "email-preview"};
    private static final String[] SEARCHES = {"library hours", "Library Hours", "parking", ""};

    private GoldenEventStream() {}

    /** The events of a burst, in time order */
    public static List<PortalEvent> events(Burst burst) {
        final List<PortalEvent> events = new ArrayList<>();
        int visitIndex = burst.ordinal() * 100;
        for (final Instant start : burst.visits()) {
            final String[] user = USERS[visitIndex % USERS.length];
            visit(events, visitIndex, start, user);
            if (visitIndex % 3 == 0) {
                // A second, concurrent visit
                visit(
                        events,
                        visitIndex + 1,
                        start.plusSeconds(20),
                        USERS[(visitIndex + 3) % USERS.length]);
            }
            visitIndex++;
        }
        events.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        return events;
    }

    private static void visit(List<PortalEvent> events, int visit, Instant start, String[] user) {
        final String userName = user[0];
        final IPerson person = person(userName, Integer.parseInt(user[1]));
        final String sessionId = "golden-session-" + visit + "-" + userName;
        final PortalEvent.PortalEventBuilder builder =
                new PortalEvent.PortalEventBuilder(
                        GoldenEventStream.class, "golden.example.edu", sessionId, person, null);
        int second = 0;
        if (user[2] != null) {
            final Set<String> groups =
                    new LinkedHashSet<>(Arrays.asList(user).subList(2, user.length));
            final Map<String, List<String>> attributes = new LinkedHashMap<>();
            attributes.put("username", Collections.singletonList(userName));
            add(events, new LoginEvent(builder, groups, attributes), start, second);
        }
        for (int render = 0; render < 3; render++) {
            second += 7 + render;
            final String tab = TABS[(visit + render) % TABS.length];
            add(
                    events,
                    new PortalRenderEvent(
                            builder,
                            "/f/" + tab + "/normal/render.uP",
                            nanos(visit, render, 40),
                            UrlState.NORMAL,
                            UrlType.RENDER,
                            Collections.<String, List<String>>emptyMap(),
                            tab),
                    start,
                    second);
            for (int portlet = 0; portlet < 2; portlet++) {
                final String fname = PORTLETS[(visit + render + portlet) % PORTLETS.length];
                add(
                        events,
                        new PortletRenderExecutionEvent(
                                portlet(builder, fname, visit, render * 2 + portlet), true, false),
                        start,
                        second + 1);
            }
        }
        second += 20;
        final String fname = PORTLETS[visit % PORTLETS.length];
        add(
                events,
                new PortletActionExecutionEvent(portlet(builder, fname, visit, 10)),
                start,
                second);
        add(
                events,
                new PortletEventExecutionEvent(
                        portlet(builder, fname, visit, 11),
                        new QName("https://source.jasig.org/schemas/uportal", "golden", "g")),
                start,
                second + 1);
        add(
                events,
                new PortletResourceExecutionEvent(
                        portlet(builder, fname, visit, 12), "chart.png", visit % 2 == 0, false),
                start,
                second + 2);
        second += 30;
        final Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("query", Collections.singletonList(SEARCHES[visit % SEARCHES.length]));
        add(
                events,
                new PortletActionExecutionEvent(
                        new PortletExecutionEvent.PortletExecutionEventBuilder(
                                builder,
                                new MockPortletWindowId("search-" + visit),
                                "search",
                                nanos(visit, 13, 90),
                                query,
                                WindowState.MAXIMIZED,
                                PortletMode.VIEW)),
                start,
                second);
        second += 40;
        if (user[2] != null) {
            final String added = PORTLETS[(visit + 1) % PORTLETS.length];
            add(
                    events,
                    new PortletAddedToLayoutPortalEvent(builder, person, 1, "s10", added),
                    start,
                    second);
            add(
                    events,
                    new PortletMovedInLayoutPortalEvent(builder, person, 1, "s10", "s20", added),
                    start,
                    second + 5);
            if (visit % 2 == 1) {
                add(
                        events,
                        new PortletDeletedFromLayoutPortalEvent(builder, person, 1, "s20", added),
                        start,
                        second + 9);
            }
        }
        if (user[2] != null && visit % 4 != 3) {
            // Every fourth visit ends without a logout (the session times out)
            add(events, new LogoutEvent(builder), start, second + 180 + visit % 120);
        }
    }

    private static PortletExecutionEvent.PortletExecutionEventBuilder portlet(
            PortalEvent.PortalEventBuilder builder, String fname, int visit, int index) {
        return new PortletExecutionEvent.PortletExecutionEventBuilder(
                builder,
                new MockPortletWindowId(fname + "-" + visit),
                fname,
                nanos(visit, index, 15),
                Collections.<String, List<String>>emptyMap(),
                WindowState.NORMAL,
                PortletMode.VIEW);
    }

    /** A deterministic execution time between {@code base} and base + 400 milliseconds */
    private static long nanos(int visit, int index, int base) {
        return (base + (visit * 31L + index * 17L) % 400) * 1_000_000L
                + (visit * 7919L + index) % 1000;
    }

    private static IPerson person(String userName, int id) {
        final PersonImpl person = new PersonImpl();
        person.setUserName(userName);
        person.setID(id);
        return person;
    }

    private static void add(
            List<PortalEvent> events, PortalEvent event, Instant start, int second) {
        setTimestamp(event, start.plusSeconds(second).toEpochMilli());
        events.add(event);
    }

    /** {@link ApplicationEvent} stamps the creation time; the stream needs chosen times */
    private static void setTimestamp(ApplicationEvent event, long millis) {
        try {
            final Field field = ApplicationEvent.class.getDeclaredField("timestamp");
            field.setAccessible(true);
            field.set(event, millis);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set the event timestamp", e);
        }
    }
}
