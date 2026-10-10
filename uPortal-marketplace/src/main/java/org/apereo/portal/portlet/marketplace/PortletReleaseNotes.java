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
package org.apereo.portal.portlet.marketplace;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class PortletReleaseNotes {

    private LocalDateTime releaseDate;
    private LocalDateTime initialReleaseDate;
    private List<String> releaseNotes;

    public PortletReleaseNotes() {}

    /**
     * @return releaseDate, in the portal's time zone. Can be null if not set via portlet
     *     preference.
     */
    @JsonSerialize(using = EpochMillisSerializer.class)
    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    /**
     * @return List of releaseNotes as strings. Can be null if not set via portlet preference.
     */
    public List<String> getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(List<String> releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    /**
     * @return initialReleaseDate, in the portal's time zone. Can be null if not set via portlet
     *     preference.
     */
    @JsonSerialize(using = EpochMillisSerializer.class)
    public LocalDateTime getInitialReleaseDate() {
        return initialReleaseDate;
    }

    public void setInitialReleaseDate(LocalDateTime initialReleaseDate) {
        this.initialReleaseDate = initialReleaseDate;
    }

    /**
     * Writes the date as epoch milliseconds in the portal's default time zone, the JSON the
     * Joda-Time {@code DateTime} properties produced, whichever Jackson modules are registered.
     */
    static final class EpochMillisSerializer extends StdSerializer<LocalDateTime> {
        private static final long serialVersionUID = 1L;

        EpochMillisSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeNumber(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
    }
}
