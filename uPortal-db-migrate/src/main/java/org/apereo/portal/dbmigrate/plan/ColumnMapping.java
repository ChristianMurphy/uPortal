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
package org.apereo.portal.dbmigrate.plan;

import org.apereo.portal.dbmigrate.rules.ValueTransform;
import org.apereo.portal.dbmigrate.schema.Column;
import org.apereo.portal.dbmigrate.types.ColumnPair;

/** How one target column gets its value: from a source column, a transform, or both. */
public final class ColumnMapping {

    private final Column source;
    private final Column target;
    private final ColumnPair pair;
    private final ValueTransform.Function transform;
    private final String transformDescription;

    public ColumnMapping(
            Column source,
            Column target,
            ColumnPair pair,
            ValueTransform.Function transform,
            String transformDescription) {
        this.source = source;
        this.target = target;
        this.pair = pair;
        this.transform = transform;
        this.transformDescription = transformDescription;
    }

    /** Null for a derived (target-only) column. */
    public Column source() {
        return source;
    }

    public Column target() {
        return target;
    }

    public ColumnPair pair() {
        return pair;
    }

    /** Null when the value is copied as-is. */
    public ValueTransform.Function transform() {
        return transform;
    }

    public String transformDescription() {
        return transformDescription;
    }

    @Override
    public String toString() {
        return (source == null ? "(derived)" : source.name())
                + " -> "
                + target.name()
                + (transformDescription == null ? "" : " [" + transformDescription + "]");
    }
}
