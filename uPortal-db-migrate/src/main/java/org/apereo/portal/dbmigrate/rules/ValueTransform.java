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

import java.util.Map;
import org.apereo.portal.dbmigrate.types.ColumnType.Family;

/**
 * A named value conversion that rule files can apply to a column ({@code transform}) or use to fill
 * a target-only column ({@code derive-column}).
 *
 * <p>Implementations are found with {@link java.util.ServiceLoader} (list them in {@code
 * META-INF/services/org.apereo.portal.dbmigrate.rules.ValueTransform}), next to the built-in ones
 * in {@link BuiltInTransforms}. Schema changes such as 6.0's {@code java.time} columns plug in
 * here, one rule per column.
 */
public interface ValueTransform {

    /** The name rule files use. */
    String name();

    /** Configures the transform for one column from the rule's {@code key=value} arguments. */
    Function bind(Map<String, String> arguments);

    /** A configured transform. */
    interface Function {
        /**
         * Returns the target value.
         *
         * @param value the source value in its canonical Java form (see {@link
         *     org.apereo.portal.dbmigrate.types.ValueCodec}); null for a derived column
         * @param row the whole source row, keyed by upper-case column name
         */
        Object apply(Object value, Map<String, Object> row);

        /**
         * The family of the values it returns; null when it keeps the input family. {@link
         * Family#OTHER} lets a transform fill a column type the copy has no family for (a
         * PostgreSQL {@code jsonb} or {@code uuid}, for example): the value is then bound with
         * {@code setObject}.
         */
        Family resultFamily();

        /**
         * Brings a value to the form verification compares: called on the source value after {@link
         * #apply} and on the value read back from the target. The default keeps it as is; a
         * JSON-to-{@code jsonb} transform would re-serialize both sides canonically, because the
         * database reformats JSON.
         */
        default Object canonical(Object value) {
            return value;
        }
    }
}
