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
package org.apereo.portal.persistence.queries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apereo.portal.test.golden.DatabaseVendor;
import org.apereo.portal.test.golden.GoldenRenderer;

/**
 * The documented ways a database answers the DAO queries differently from HSQLDB, where {@code
 * queries/portal-dao.txt} is recorded. A golden line is {@code label -> result} or {@code label
 * element}; a difference replaces the result of a label (and its element lines). A new difference
 * fails the test until it is listed here, with the reason.
 */
final class QueryVendorDifferences {
    /**
     * HSQLDB, MariaDB and SQL Server compare strings with PAD SPACE semantics, so 'Trailing Space'
     * equals the stored 'Trailing Space '. PostgreSQL, Oracle and MySQL 8+ (utf8mb4_0900 collations
     * are NO PAD) do not, so the lookup by name finds nothing.
     */
    private static final Set<DatabaseVendor> NO_PAD =
            EnumSet.of(DatabaseVendor.POSTGRESQL, DatabaseVendor.ORACLE, DatabaseVendor.MYSQL);

    /**
     * The default collations of MySQL (utf8mb4_0900_ai_ci), MariaDB (utf8mb4_uca1400_ai_ci) and SQL
     * Server (SQL_Latin1_General_CP1_CI_AS) ignore case, so every lookup by name, code or key
     * matches other spellings, and a code that differs from an existing one only in case violates
     * the unique key. HSQLDB, PostgreSQL and Oracle compare case-sensitively.
     */
    private static final Set<DatabaseVendor> CASE_INSENSITIVE =
            EnumSet.of(DatabaseVendor.MYSQL, DatabaseVendor.MARIADB, DatabaseVendor.SQLSERVER);

    private static final String SEPARATOR = " -> ";
    private static final String ELEMENT = "   ";
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    private QueryVendorDifferences() {}

    /** For each label whose result differs: the result line, then the element lines */
    static Map<String, List<String>> differences(DatabaseVendor vendor, List<String> golden) {
        final Map<String, List<String>> differences = new LinkedHashMap<>();
        if (NO_PAD.contains(vendor)) {
            differences.put(
                    "portletDefinitionDao.getPortletDefinitionByName(\"Trailing Space\")",
                    Arrays.asList("null"));
        }
        if (CASE_INSENSITIVE.contains(vendor)) {
            sameAs(
                    differences,
                    golden,
                    "portletTypeDao.getPortletType(\"web proxy\")",
                    "portletTypeDao.getPortletType(\"Web Proxy\")");
            sameAs(
                    differences,
                    golden,
                    "portletDefinitionDao.getPortletDefinitionByName(\"weather\")",
                    "portletDefinitionDao.getPortletDefinitionByName(\"Weather\")");
            // The partial search upper-cases the term only (MOD-I142), so here it matches
            for (final String term : Arrays.asList("cal", "CAL", "Calendar")) {
                sameAs(
                        differences,
                        golden,
                        "portletDefinitionDao.searchForPortlets(\"" + term + "\", true)",
                        "portletDefinitionDao.searchForPortlets(\"Calendar\", false)");
            }
            sameAs(
                    differences,
                    golden,
                    "portletDefinitionDao.searchForPortlets(\"calendar\", false)",
                    "portletDefinitionDao.searchForPortlets(\"Calendar\", false)");
            // "%\u00dcBER%" matches the title "\u00dcbersicht"
            differences.put(
                    "portletDefinitionDao.searchForPortlets(\"\\u00fcber\", true)",
                    listOf(golden, "portletDefinitionDao.getPortletDefinitionByName(unicode)"));
            // The unique key (CODE, LOCALE) rejects "Greeting" next to "greeting"
            differences.put(
                    "messageDao.createMessage(\"Greeting\", en, ...) next to \"greeting\"",
                    Arrays.asList(
                            "throws on commit"
                                    + " org.springframework.dao.DataIntegrityViolationException"));
            differences.put(
                    "messageDao.getMessage(\"GREETING\", en)",
                    Arrays.asList("MessageImpl{code=\"greeting\", locale=en, value=\"Hello\"}"));
            differences.put(
                    "messageDao.getMessagesByLocale(en)",
                    Arrays.asList(
                            "LinkedHashSet of 2, in order",
                            "[0] MessageImpl{code=\"farewell\", locale=en, value=\"Goodbye\"}",
                            "[1] MessageImpl{code=\"greeting\", locale=en, value=\"Hello\"}"));
            differences.put(
                    "messageDao.getCodes()",
                    Arrays.asList("LinkedHashSet of 2", "- \"farewell\"", "- \"greeting\""));
            sameAs(
                    differences,
                    golden,
                    "stylesheetDescriptorDao.getStylesheetDescriptorByName(\"respondrjs\")",
                    "stylesheetDescriptorDao.getStylesheetDescriptorByName(\"RespondrJS\")");
            sameAs(
                    differences,
                    golden,
                    "localAccountDao.getPerson(\"ALICE\")",
                    "localAccountDao.getPerson(\"alice\")");
            sameAs(
                    differences,
                    golden,
                    "permissionOwnerDao.getPermissionOwner(\"up_portal\")",
                    "permissionOwnerDao.getPermissionOwner(\"UP_PORTAL\")");
            sameAs(
                    differences,
                    golden,
                    "pagsGroupDefinitionDao.getPersonAttributesGroupDefinitionByName(\"students\")",
                    "pagsGroupDefinitionDao.getPersonAttributesGroupDefinitionByName(\"Students\")");
        }
        switch (vendor) {
            case MYSQL:
            case MARIADB:
                // AVG of an integer column is a DECIMAL with 4 decimal places
                differences.put(
                        "after aggregateMarketplaceRating: weather",
                        Arrays.asList("\"rating=3.6667 (Double), usersRated=3 (Long)\""));
                break;
            case SQLSERVER:
                // AVG of an integer column is an integer
                differences.put(
                        "after aggregateMarketplaceRating: weather",
                        Arrays.asList("\"rating=3 (Double), usersRated=3 (Long)\""));
                // The varchar column holds "Zo\u00eb's ??? Portlet" (see storedString), which
                // the nvarchar parameter no longer equals
                differences.put(
                        "portletDefinitionDao.getPortletDefinitionByName(unicode)",
                        Arrays.asList("null"));
                break;
            default:
                break;
        }
        return differences;
    }

    /**
     * Maps each golden line to what {@code vendor} is documented to return: the listed differences,
     * then every quoted string of a result as the vendor stores it ({@link
     * DatabaseVendor#storedString}).
     */
    static UnaryOperator<String> goldenLines(DatabaseVendor vendor, List<String> golden) {
        final Map<String, List<String>> differences = differences(vendor, golden);
        return line -> {
            final List<String> mapped = new ArrayList<>();
            final String label = label(line);
            final List<String> difference = label == null ? null : differences.get(label);
            if (difference == null) {
                mapped.add(line);
            } else if (line.startsWith(label + SEPARATOR)) {
                mapped.add(label + SEPARATOR + difference.get(0));
                for (final String element : difference.subList(1, difference.size())) {
                    mapped.add(label + ELEMENT + element);
                }
            }
            // else an element of a replaced result
            final List<String> stored = new ArrayList<>();
            for (final String mappedLine : mapped) {
                stored.add(storedStrings(vendor, mappedLine));
            }
            return stored.isEmpty() ? null : String.join("\n", stored);
        };
    }

    /** The label of a golden line: the text before " -> " or before the element indent */
    static String label(String line) {
        final int result = line.indexOf(SEPARATOR);
        final int element = line.indexOf(ELEMENT);
        final int end = result < 0 ? element : element < 0 ? result : Math.min(result, element);
        return end < 0 ? null : line.substring(0, end);
    }

    /** The result and element lines of {@code label}, in golden order */
    private static List<String> resultOf(List<String> golden, String label) {
        final List<String> result = new ArrayList<>();
        for (final String line : golden) {
            if (line.startsWith(label + SEPARATOR)) {
                result.add(0, line.substring(label.length() + SEPARATOR.length()));
            } else if (line.startsWith(label + ELEMENT)) {
                result.add(line.substring(label.length() + ELEMENT.length()));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No golden result for " + label);
        }
        return result;
    }

    private static void sameAs(
            Map<String, List<String>> differences,
            List<String> golden,
            String label,
            String sameAsLabel) {
        differences.put(label, resultOf(golden, sameAsLabel));
    }

    /** An ArrayList holding the single results of {@code labels} */
    private static List<String> listOf(List<String> golden, String... labels) {
        final List<String> result = new ArrayList<>();
        result.add("ArrayList of " + labels.length);
        for (final String label : labels) {
            result.add("- " + resultOf(golden, label).get(0));
        }
        return result;
    }

    /** The quoted strings after the label, as {@code vendor} stores them */
    private static String storedStrings(DatabaseVendor vendor, String line) {
        final String label = label(line);
        if (label == null
                || (vendor != DatabaseVendor.ORACLE && vendor != DatabaseVendor.SQLSERVER)) {
            return line;
        }
        final String rest = line.substring(label.length());
        final Matcher matcher = QUOTED.matcher(rest);
        final StringBuffer result = new StringBuffer(label);
        while (matcher.find()) {
            final String stored = vendor.storedString(GoldenRenderer.unescape(matcher.group(1)));
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(
                            stored == null ? "null" : '"' + GoldenRenderer.escape(stored) + '"'));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
