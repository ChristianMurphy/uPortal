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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.apereo.portal.test.golden.DatabaseVendor;

/**
 * Edge values for each {@link Variant}, chosen from what the mapping allows: string lengths come
 * from {@code @Column(length)}, {@code null} only where the column is nullable, functional names
 * only from the characters {@code FunctionalNameType} accepts. Every value carries a short seed tag
 * so that unique and natural-id columns never collide between instances.
 */
final class EdgeValues {

    /**
     * Accents and ligatures, CJK, Hebrew and Arabic (right to left), Greek, a combining mark, emoji
     * (outside the BMP, 4 bytes in UTF-8) and a non-breaking space.
     */
    static final String UNICODE_TEXT =
            "Gr\u00fc\u00dfe Zo\u00eb \ufb01 \u2014 \u65e5\u672c\u8a9e\u30c6\u30ad\u30b9\u30c8 \u00b7 \u05e2\u05d1\u05e8\u05d9\u05ea \u00b7 \u0627\u0644\u0639\u0631\u0628\u064a\u0629 \u00b7 \u03a9\u03bc\u03ad\u03b3\u03b1 \u00b7 e\u0301 \u00b7 \ud83c\udf93\ud83d\udcda\ud83c\udff3\ufe0f\u200d\ud83c\udf08 \u00b7\u00a0end";

    private static final String ASCII_FILL = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String FNAME_FILL = "abcdefghijklmnopqrstuvwxyz0123456789_-";

    /** 9999-12-31 (not at midnight, so rounding to whole seconds stays in year 9999) */
    static final Instant LATEST = Instant.parse("9999-12-31T12:34:56.789Z");

    /** One second after the 32-bit Unix time overflow */
    static final Instant Y2038 = Instant.parse("2038-01-19T03:14:08.789Z");

    static final Instant EPOCH = Instant.EPOCH;
    static final Instant BEFORE_EPOCH = Instant.parse("1969-12-31T23:59:59.999Z");

    /** 01:30 EDT, the first of the two 01:30s in New York on 2026-11-01 */
    static final Instant NEW_YORK_REPEATED_HOUR_FIRST = Instant.parse("2026-11-01T05:30:00.250Z");

    /** 01:30 EST, the second 01:30 in New York on 2026-11-01 */
    static final Instant NEW_YORK_REPEATED_HOUR_SECOND = Instant.parse("2026-11-01T06:30:00.750Z");

    /** 03:00 EDT, the first instant after New York skips 02:00-03:00 on 2026-03-08 */
    static final Instant NEW_YORK_AFTER_GAP = Instant.parse("2026-03-08T07:00:00Z");

    /** 02:00 BST, the first instant after London skips 01:00-02:00 on 2026-03-29 */
    static final Instant LONDON_AFTER_GAP = Instant.parse("2026-03-29T01:00:00.500Z");

    /** 01:30 BST, the first of the two 01:30s in London on 2026-10-25 */
    static final Instant LONDON_REPEATED_HOUR_FIRST = Instant.parse("2026-10-25T00:30:00Z");

    /** 01:30 GMT, the second 01:30 in London on 2026-10-25 */
    static final Instant LONDON_REPEATED_HOUR_SECOND = Instant.parse("2026-10-25T01:30:00Z");

    private static final List<Instant> MAX_INSTANTS = Arrays.asList(LATEST, Y2038);
    private static final List<Instant> MIN_INSTANTS = Arrays.asList(EPOCH, BEFORE_EPOCH);
    private static final List<Instant> DST_INSTANTS =
            Arrays.asList(
                    NEW_YORK_REPEATED_HOUR_FIRST,
                    LONDON_AFTER_GAP,
                    NEW_YORK_AFTER_GAP,
                    LONDON_REPEATED_HOUR_FIRST);
    private static final List<Instant> SECOND_OCCURRENCE_INSTANTS =
            Arrays.asList(NEW_YORK_REPEATED_HOUR_SECOND, LONDON_REPEATED_HOUR_SECOND);

    private final DatabaseVendor vendor;

    EdgeValues(DatabaseVendor vendor) {
        this.vendor = vendor;
    }

    static String seedTag(int seed) {
        final String tag = Integer.toString(seed, 36);
        return tag.length() == 1 ? "0" + tag : tag;
    }

    /** The value for a slot, or null; {@link Unsupported} for a type this class cannot build */
    Object value(Slot slot, Variant variant, int seed) {
        final Class<?> type = slot.type;
        if (type == String.class) {
            return string(slot, variant, seed);
        }
        if (type == int.class || type == Integer.class) {
            final Long value = integral(slot, variant, seed, Integer.MAX_VALUE, Integer.MIN_VALUE);
            return value == null ? null : value.intValue();
        }
        if (type == long.class || type == Long.class) {
            return integral(slot, variant, seed, Long.MAX_VALUE, Long.MIN_VALUE);
        }
        if (type == short.class || type == Short.class) {
            final Long value = integral(slot, variant, seed, Short.MAX_VALUE, Short.MIN_VALUE);
            return value == null ? null : value.shortValue();
        }
        if (type == double.class || type == Double.class) {
            return floating(slot, variant, seed);
        }
        if (type == float.class || type == Float.class) {
            final Double d = floating(slot, variant, seed);
            return d == null
                    ? null
                    : (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, d));
        }
        if (type == boolean.class || type == Boolean.class) {
            switch (variant) {
                case MIN:
                    return false;
                case NULLS:
                    return slot.nullable ? null : Boolean.FALSE;
                case TYPICAL:
                    return seed % 2 == 0;
                default:
                    return true;
            }
        }
        if (type.isEnum()) {
            return enumValue(slot, variant, seed);
        }
        if (type == Date.class) {
            final Instant instant = instant(slot, variant, seed);
            return instant == null ? null : new Date(instant.toEpochMilli());
        }
        if (type == Instant.class) {
            return instant(slot, variant, seed);
        }
        if (type == Timestamp.class) {
            final Instant instant = instant(slot, variant, seed);
            return instant == null ? null : new Timestamp(instant.toEpochMilli());
        }
        if (type == Calendar.class) {
            final Instant instant = instant(slot, variant, seed);
            if (instant == null) {
                return null;
            }
            final Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
            calendar.setTimeInMillis(instant.toEpochMilli());
            return calendar;
        }
        if (type == LocalDate.class) {
            return localDate(slot, variant, seed);
        }
        if (type == LocalTime.class) {
            return localTime(slot, variant, seed);
        }
        if (type == MonthDay.class) {
            return monthDay(slot, variant, seed);
        }
        if (type == Locale.class) {
            return locale(slot, variant, seed);
        }
        if (type == Class.class) {
            return ClassValues.forBound(slot.classBound(), variant, seed);
        }
        throw new Unsupported(slot + ": no edge values for " + type.getName());
    }

    private String string(Slot slot, Variant variant, int seed) {
        final String tag = variant.name().charAt(0) + seedTag(seed);
        // uPortal's nullSafeString type stores a '_' prefix, so its columns hold one character
        // less than their declared length (a value of the declared length fails to insert)
        final int length = "nullSafeString".equals(slot.userType) ? slot.length - 1 : slot.length;
        if (slot.isFname()) {
            switch (variant) {
                case MAX:
                    return fill(tag, FNAME_FILL, length);
                case UNICODE:
                    // FunctionalNameType accepts [A-Za-z0-9_-] only
                    return fit(tag + "_Mixed-Case_09", length);
                default:
                    return tag.toLowerCase(Locale.ROOT);
            }
        }
        switch (variant) {
            case MAX:
                return fill(tag, ASCII_FILL, length);
            case MIN:
                return slot.nullable && !slot.unique ? "" : tag;
            case UNICODE:
                return fit(tag + " " + UNICODE_TEXT, length);
            case NULLS:
                return slot.nullable ? null : tag;
            default:
                return "typical-" + tag;
        }
    }

    private Long integral(Slot slot, Variant variant, int seed, long max, long min) {
        switch (variant) {
            case MAX:
                return max;
            case MIN:
                return min;
            case UNICODE:
                return 42L + seed;
            case NULLS:
                return slot.nullable ? null : 0L;
            default:
                return 1000L + seed;
        }
    }

    private Double floating(Slot slot, Variant variant, int seed) {
        switch (variant) {
            case MAX:
                return VendorDifferences.storableDouble(vendor, Double.MAX_VALUE);
            case MIN:
                return VendorDifferences.storableDouble(vendor, -Double.MAX_VALUE);
            case UNICODE:
                // Not exact in binary
                return 0.1 + seed;
            case NULLS:
                return slot.nullable
                        ? null
                        : VendorDifferences.storableDouble(vendor, Double.MIN_NORMAL);
            default:
                return 1.5 + seed;
        }
    }

    private Object enumValue(Slot slot, Variant variant, int seed) {
        final Object[] constants = slot.type.getEnumConstants();
        switch (variant) {
            case MAX:
                return constants[constants.length - 1];
            case MIN:
                return constants[0];
            case UNICODE:
                return constants[constants.length / 2];
            case NULLS:
                return slot.nullable ? null : constants[Math.min(1, constants.length - 1)];
            default:
                return constants[seed % constants.length];
        }
    }

    private Instant instant(Slot slot, Variant variant, int seed) {
        switch (variant) {
            case MAX:
                return MAX_INSTANTS.get(slot.ordinal % MAX_INSTANTS.size());
            case MIN:
                return MIN_INSTANTS.get(slot.ordinal % MIN_INSTANTS.size());
            case UNICODE:
                return DST_INSTANTS.get(slot.ordinal % DST_INSTANTS.size());
            case NULLS:
                return slot.nullable
                        ? null
                        : SECOND_OCCURRENCE_INSTANTS.get(
                                slot.ordinal % SECOND_OCCURRENCE_INSTANTS.size());
            default:
                return Instant.parse("2001-02-03T04:05:06.007Z").plusSeconds(3600L * seed);
        }
    }

    private LocalDate localDate(Slot slot, Variant variant, int seed) {
        switch (variant) {
            case MAX:
                return LocalDate.of(9999, 12, 31);
            case MIN:
                return LocalDate.of(1970, 1, 1);
            case UNICODE:
                // The New York fall-back day
                return LocalDate.of(2026, 11, 1);
            case NULLS:
                // The New York spring-forward day
                return slot.nullable ? null : LocalDate.of(2026, 3, 8);
            default:
                return LocalDate.of(2001, 1, 1).plusDays(seed);
        }
    }

    private LocalTime localTime(Slot slot, Variant variant, int seed) {
        switch (variant) {
            case MAX:
                // Whole seconds: see VendorDifferences for the precision of TIME columns
                return LocalTime.of(23, 59, 59);
            case MIN:
                return LocalTime.MIDNIGHT;
            case UNICODE:
                // The repeated hour in New York in November
                return LocalTime.of(1, 30);
            case NULLS:
                // The skipped hour in New York in March
                return slot.nullable ? null : LocalTime.of(2, 30);
            default:
                return LocalTime.of(10, 0).plusMinutes(seed % 600);
        }
    }

    private MonthDay monthDay(Slot slot, Variant variant, int seed) {
        switch (variant) {
            case MAX:
                return MonthDay.of(12, 31);
            case MIN:
                return MonthDay.of(1, 1);
            case UNICODE:
                return MonthDay.of(2, 29);
            case NULLS:
                return slot.nullable ? null : MonthDay.of(11, 1);
            default:
                return MonthDay.of(1 + seed % 12, 1 + seed % 28);
        }
    }

    private Locale locale(Slot slot, Variant variant, int seed) {
        switch (variant) {
            case MAX:
                return new Locale("zh", "TW", "POSIX");
            case MIN:
                // Not Locale.ROOT: Hibernate stores it as '' and reads that back as null, and
                // Oracle rejects '' (NULL) in a NOT NULL column
                return slot.nullable ? Locale.ROOT : new Locale("en");
            case UNICODE:
                // A locale with a script: Locale#toString gives "sr_RS_#Latn"
                return Locale.forLanguageTag("sr-Latn-RS");
            case NULLS:
                return slot.nullable ? null : Locale.CANADA_FRENCH;
            default:
                return Locale.US;
        }
    }

    /** {@code tag} padded with {@code fill} to exactly {@code length} characters */
    static String fill(String tag, String fill, int length) {
        final StringBuilder result = new StringBuilder(length);
        result.append(tag, 0, Math.min(tag.length(), length));
        while (result.length() < length) {
            result.append(fill.charAt(result.length() % fill.length()));
        }
        return result.toString();
    }

    /** {@code text} cut to at most {@code length} UTF-16 units without splitting a code point */
    static String fit(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        int end = length;
        if (Character.isLowSurrogate(text.charAt(end)) && end > 0) {
            end--;
        }
        return text.substring(0, end);
    }

    /** The default zone's id, for messages */
    static String zone() {
        return java.util.TimeZone.getDefault().getID();
    }

    /** Thrown for a value type the builder has no edge values for */
    static final class Unsupported extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unsupported(String message) {
            super(message);
        }
    }
}
