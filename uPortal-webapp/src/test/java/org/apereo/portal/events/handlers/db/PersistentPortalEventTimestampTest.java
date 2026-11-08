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
package org.apereo.portal.events.handlers.db;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Instant;
import org.apereo.portal.events.PortalEvent;
import org.apereo.portal.security.IPerson;
import org.junit.Test;

/** A raw event row stores the event's timestamp to the millisecond, as Jadira did */
public class PersistentPortalEventTimestampTest {
    @Test
    public void storesTheEventTimestampToTheMillisecond() throws ReflectiveOperationException {
        // An event whose timestamp isn't on a whole second, so a truncation would show
        TestEvent event = new TestEvent();
        while (event.getTimestamp() % 1000 == 0) {
            event = new TestEvent();
        }
        final PersistentPortalEvent row = new PersistentPortalEvent(event, "{}");
        // The TIMESTAMP column has no getter: read the mapped field
        final Field timestamp = PersistentPortalEvent.class.getDeclaredField("timestamp");
        timestamp.setAccessible(true);
        assertEquals(Instant.ofEpochMilli(event.getTimestamp()), timestamp.get(row));
    }

    private static final class TestEvent extends PortalEvent {
        private static final long serialVersionUID = 1L;

        TestEvent() {
            super(new Builder());
        }

        private static final class Builder extends PortalEventBuilder {
            Builder() {
                super(new Object(), "server", "session", mock(IPerson.class), null);
            }
        }
    }
}
