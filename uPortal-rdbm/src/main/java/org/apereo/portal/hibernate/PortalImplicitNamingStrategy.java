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
package org.apereo.portal.hibernate;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitForeignKeyNameSource;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl;
import org.hibernate.boot.model.naming.ImplicitUniqueKeyNameSource;

/**
 * Names tables, columns and constraints exactly as uPortal's databases were created under Hibernate
 * 4.3, so that existing databases match the mappings without any rename.
 *
 * <p>Table, column and join names come from {@link ImplicitNamingStrategyLegacyJpaImpl}, the
 * Hibernate 5 equivalent of the {@code EJB3NamingStrategy} that Hibernate 4.3 used for JPA. That
 * strategy hashes foreign key and unique key names differently from Hibernate 4.3 (a {@code FK} or
 * {@code UK} prefix without the underscore, and the referenced table in the foreign key hash), so
 * this class restores the Hibernate 4.3 algorithm for both: the prefix {@code FK_} or {@code UK_}
 * followed by the base-35 MD5 hash of the table name and the column names in alphabetical order
 * ({@code org.hibernate.mapping.Constraint#generateName} in Hibernate 4.3).
 *
 * <p>Explicit names ({@code @ForeignKey}, named unique constraints) are used as given.
 */
public class PortalImplicitNamingStrategy extends ImplicitNamingStrategyLegacyJpaImpl {
    private static final long serialVersionUID = 1L;

    @Override
    public Identifier determineForeignKeyName(ImplicitForeignKeyNameSource source) {
        final Identifier userProvidedIdentifier = source.getUserProvidedIdentifier();
        if (userProvidedIdentifier != null) {
            return userProvidedIdentifier;
        }
        return toIdentifier(
                constraintName("FK_", source.getTableName(), source.getColumnNames()),
                source.getBuildingContext());
    }

    @Override
    public Identifier determineUniqueKeyName(ImplicitUniqueKeyNameSource source) {
        final Identifier userProvidedIdentifier = source.getUserProvidedIdentifier();
        if (userProvidedIdentifier != null) {
            return userProvidedIdentifier;
        }
        return toIdentifier(
                constraintName("UK_", source.getTableName(), source.getColumnNames()),
                source.getBuildingContext());
    }

    /** {@code org.hibernate.mapping.Constraint#generateName} as of Hibernate 4.3. */
    static String constraintName(String prefix, Identifier table, List<Identifier> columns) {
        final List<String> columnNames = new ArrayList<>(columns.size());
        for (final Identifier column : columns) {
            columnNames.add(column == null ? "" : column.getText());
        }
        Collections.sort(columnNames);

        final StringBuilder sb = new StringBuilder("table`").append(table.getText()).append('`');
        for (final String columnName : columnNames) {
            sb.append("column`").append(columnName).append('`');
        }
        return prefix + hashedName(sb.toString());
    }

    @SuppressFBWarnings(
            value = "WEAK_MESSAGE_DIGEST_MD5",
            justification =
                    "Not security: reproduces the FK_/UK_ names Hibernate 4.3 derived with MD5,"
                            + " which existing databases carry")
    private static String hashedName(String s) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, digest).toString(35);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}
