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
package org.apereo.portal.persistence.roundtrip;

/** The sets of edge values each entity is round-tripped with; see {@link EdgeValues}. */
enum Variant {
    /**
     * Strings at their mapped maximum length, the largest numbers, the latest dates (9999-12-31,
     * 2038-01-19T03:14:08Z), {@code true}, the last enum constant, three-element collections.
     */
    MAX,
    /**
     * Empty strings where the column is nullable (otherwise the shortest unique value), the
     * smallest numbers, the epoch and the millisecond before it, {@code false}, the first enum
     * constant, one-element collections.
     */
    MIN,
    /**
     * Multi-byte text (accents, CJK, Hebrew and Arabic, emoji, combining marks), instants on the
     * DST transitions of America/New_York and Europe/London, fractional numbers, two-element
     * collections.
     */
    UNICODE,
    /** {@code null} wherever the mapping allows it, empty collections, the repeated DST hour. */
    NULLS,
    /**
     * Plain distinct values for entities that are only built as a prerequisite of the entity under
     * test (dimensions, mappings, portlet types); never round-tripped itself.
     */
    TYPICAL;

    static final Variant[] TESTED = {MAX, MIN, UNICODE, NULLS};
}
