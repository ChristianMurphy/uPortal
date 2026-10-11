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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apereo.portal.dbmigrate.catalog.GeneratorCatalog;
import org.apereo.portal.dbmigrate.rules.RuleSet;

/**
 * The facts about one uPortal schema version that the database metadata cannot tell: which
 * PostgreSQL {@code text} columns hold large-object OIDs, and which generator serves which id
 * column. Loaded from {@code org/apereo/portal/dbmigrate/profiles/<name>/}: {@code schema.rules}
 * (rule syntax, see {@link RuleSet}) and {@code generators.tsv} (generated from the entities).
 */
public final class SchemaProfile {

    private static final String BASE = "org/apereo/portal/dbmigrate/profiles/";

    private final String name;
    private final RuleSet rules;
    private final GeneratorCatalog generators;

    public SchemaProfile(String name, RuleSet rules, GeneratorCatalog generators) {
        this.name = name;
        this.rules = rules;
        this.generators = generators;
    }

    public static SchemaProfile load(String name) {
        String dir = BASE + name + "/";
        if (SchemaProfile.class.getClassLoader().getResource(dir + "schema.rules") == null) {
            throw new IllegalArgumentException("Unknown schema profile '" + name + "'");
        }
        RuleSet rules = RuleSet.fromResource(dir + "schema.rules");
        GeneratorCatalog catalog =
                GeneratorCatalog.fromResource(dir + "generators.tsv").addRules(rules);
        return new SchemaProfile(name, rules, catalog);
    }

    /**
     * The rules a copy from {@code sourceProfile} into this profile adds, from {@code
     * from-<source>.rules} next to this profile's {@code schema.rules}, with {@code ${source-zone}}
     * replaced by {@code sourceZone}; null when the copy needs none.
     */
    public RuleSet conversionsFrom(String sourceProfile, String sourceZone) {
        String resource = BASE + name + "/from-" + sourceProfile + ".rules";
        InputStream in = SchemaProfile.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            for (int n = reader.read(buffer); n >= 0; n = reader.read(buffer)) {
                text.append(buffer, 0, n);
            }
            return RuleSet.parse(text.toString().replace("${source-zone}", sourceZone), resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String name() {
        return name;
    }

    public RuleSet rules() {
        return rules;
    }

    public GeneratorCatalog generators() {
        return generators;
    }
}
