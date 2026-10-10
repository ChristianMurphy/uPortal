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
package org.apereo.portal.dbmigrate.schema;

import java.util.Locale;
import java.util.regex.Pattern;

/** Case-insensitive identifier handling: databases differ in how they fold unquoted names. */
public final class Names {

    private Names() {}

    /** The key a table or column is looked up by: upper case, locale independent. */
    public static String key(String name) {
        return name == null ? null : name.toUpperCase(Locale.ROOT);
    }

    /** {@code *} and {@code ?} glob matching, case-insensitive. */
    public static boolean globMatches(String glob, String name) {
        return toPattern(glob).matcher(name).matches();
    }

    static Pattern toPattern(String glob) {
        StringBuilder re = new StringBuilder();
        for (char ch : glob.trim().toCharArray()) {
            switch (ch) {
                case '*':
                    re.append(".*");
                    break;
                case '?':
                    re.append('.');
                    break;
                default:
                    re.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE);
    }
}
