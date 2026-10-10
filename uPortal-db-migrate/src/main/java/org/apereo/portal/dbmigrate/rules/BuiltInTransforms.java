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
package org.apereo.portal.dbmigrate.rules;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;

/** Transforms every installation has. */
final class BuiltInTransforms {

    private BuiltInTransforms() {}

    static List<ValueTransform> all() {
        return Arrays.asList(
                // constant value=<text>: a fixed string, or NULL without a value
                named(
                        "constant",
                        args -> {
                            String value = args.get("value");
                            return function(Family.STRING, (v, row) -> value);
                        }),
                // copy-of column=<SOURCE_COLUMN>: the value of another source column
                named(
                        "copy-of",
                        args -> {
                            String column =
                                    RuleSet.required(args, "column", "copy-of")
                                            .toUpperCase(Locale.ROOT);
                            return function(null, (v, row) -> row.get(column));
                        }),
                // timestamp-at-zone zone=<IANA zone>: wall-clock time in that zone -> instant
                // (e.g. Jadira/Joda timestamp columns to java.time OffsetDateTime columns)
                named(
                        "timestamp-at-zone",
                        args -> {
                            ZoneId zone =
                                    ZoneId.of(RuleSet.required(args, "zone", "timestamp-at-zone"));
                            return function(
                                    Family.TIMESTAMP_TZ,
                                    (v, row) ->
                                            v == null
                                                    ? null
                                                    : ((LocalDateTime) v)
                                                            .atZone(zone)
                                                            .toOffsetDateTime());
                        }),
                // timestamp-in-zone zone=<IANA zone>: the reverse, instant -> wall-clock time
                named(
                        "timestamp-in-zone",
                        args -> {
                            ZoneId zone =
                                    ZoneId.of(RuleSet.required(args, "zone", "timestamp-in-zone"));
                            return function(
                                    Family.TIMESTAMP,
                                    (v, row) ->
                                            v == null
                                                    ? null
                                                    : ((OffsetDateTime) v)
                                                            .atZoneSameInstant(zone)
                                                            .toLocalDateTime());
                        }));
    }

    private static ValueTransform named(
            String name, Function<Map<String, String>, ValueTransform.Function> binder) {
        return new ValueTransform() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ValueTransform.Function bind(Map<String, String> arguments) {
                return binder.apply(arguments);
            }
        };
    }

    static ValueTransform.Function function(
            Family family, BiFunction<Object, Map<String, Object>, Object> f) {
        return new ValueTransform.Function() {
            @Override
            public Object apply(Object value, Map<String, Object> row) {
                return f.apply(value, row);
            }

            @Override
            public Family resultFamily() {
                return family;
            }
        };
    }
}
