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
package org.apereo.portal.utils;

import org.apereo.portal.utils.hibernate4.dialects.MySQL5InnoDBCompressedDialect;
import org.apereo.portal.utils.hibernate4.dialects.Oracle12ForceClobDialect;
import org.apereo.portal.utils.hibernate4.dialects.PostgreSQL81Dialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQL55Dialect;
import org.hibernate.dialect.PostgreSQL9Dialect;
import org.hibernate.dialect.SQLServer2005Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;

/**
 * Picks uPortal's dialect for a database before Hibernate's standard resolver does.
 *
 * <p>Besides uPortal's own dialects, this keeps every database on the dialect Hibernate 4.3's
 * resolver chose for it. The dialect decides column types and whether generated ids are backed by
 * sequences or by one-row tables, so existing databases only keep working unchanged if it does not
 * move. Hibernate 5.2 would otherwise pick, for the same servers:
 *
 * <ul>
 *   <li>MySQL 5.7+ and MariaDB (MySQL metadata, version 6+): {@code MySQL57Dialect}, or a {@code
 *       MariaDB*Dialect} for MariaDB Connector/J, which create {@code datetime(6)} columns and,
 *       from MariaDB 10.3, real sequences instead of the {@code UP_*_SEQ} tables. Hibernate 4.3
 *       chose {@code MySQL5Dialect}; Hibernate 5.2's {@code MySQL5Dialect} declares {@code
 *       engine=MyISAM}, so {@code MySQL55Dialect}, the same types with the InnoDB engine that 4.3
 *       got from the server default, takes its place.
 *   <li>PostgreSQL 9.x and 10+: {@code PostgreSQL9xDialect} or {@code PostgreSQL95Dialect} ({@code
 *       uuid} columns for {@code java.util.UUID}, other lock syntax); Hibernate 4.3 chose {@code
 *       PostgreSQL9Dialect} and {@code PostgreSQL81Dialect}.
 *   <li>SQL Server 2008+: {@code SQLServer2008Dialect} or {@code SQLServer2012Dialect}, which have
 *       sequences; uPortal already forced {@code SQLServer2005Dialect}.
 * </ul>
 *
 * @see <a
 *     href="https://github.com/uPortal-Project/uPortal-start/blob/master/docs/db-upgrade-harness.md">DB
 *     upgrade harness</a>
 */
public class PortalDialectResolver implements DialectResolver {
    private static final long serialVersionUID = 1L;

    @Override
    public Dialect resolveDialect(DialectResolutionInfo info) {
        final String databaseName = info.getDatabaseName();
        final int databaseMajorVersion = info.getDatabaseMajorVersion();
        final int databaseMinorVersion = info.getDatabaseMinorVersion();

        if ("MySQL".equals(databaseName) && 5 == databaseMajorVersion) {
            return new MySQL5InnoDBCompressedDialect();
        }

        // MySQL 8+, and MariaDB 10+ reporting MySQL metadata: Hibernate 4.3's MySQL5Dialect types
        if ("MySQL".equals(databaseName) && databaseMajorVersion > 5) {
            return new MySQL55Dialect();
        }

        if ("PostgreSQL".equals(databaseName)
                && 8 == databaseMajorVersion
                && databaseMinorVersion <= 1) {
            return new PostgreSQL81Dialect();
        }

        // Hibernate 4.3 knew PostgreSQL 8 and 9 only; it used PostgreSQL81Dialect for 10+
        if ("PostgreSQL".equals(databaseName) && 9 == databaseMajorVersion) {
            return new PostgreSQL9Dialect();
        }
        if ("PostgreSQL".equals(databaseName) && databaseMajorVersion >= 10) {
            return new org.hibernate.dialect.PostgreSQL81Dialect();
        }

        // Ensure long varchar columns are forced to clob instead of long.
        if ("Oracle".equals(databaseName) && databaseMajorVersion >= 12) {
            return new Oracle12ForceClobDialect();
        }

        // This is due to a jTDS not supporting SQL Server 2008+, hence does not support some new
        // types like TIME.
        if ("Microsoft SQL Server".equals(databaseName) && databaseMajorVersion > 9) {
            return new SQLServer2005Dialect();
        }

        return null;
    }
}
