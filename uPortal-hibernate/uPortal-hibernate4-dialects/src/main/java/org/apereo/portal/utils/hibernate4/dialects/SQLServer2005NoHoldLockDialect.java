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
package org.apereo.portal.utils.hibernate4.dialects;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.dialect.SQLServer2005Dialect;

/**
 * {@link SQLServer2005Dialect} with the write lock hint of Hibernate 4.3 (uPortal 5.17): {@code
 * with (updlock, rowlock)}, without {@code holdlock}.
 *
 * <p>The dialect has no sequences, so Hibernate keeps each id generator in a one-row table without
 * an index. It reads the row with the write lock hint and then updates it, in a transaction of its
 * own. Since Hibernate 5 the hint is {@code updlock, holdlock, rowlock}, and transactions that read
 * the row at the same moment deadlock: SQL Server ended 25 of 60 parallel inserts as deadlock
 * victims in {@code ParallelIdAllocationTest}. With {@code updlock} alone the second reader waits
 * until the first transaction commits and then reads the value it wrote, so no insert fails and ids
 * stay unique.
 */
public class SQLServer2005NoHoldLockDialect extends SQLServer2005Dialect {

    @Override
    public String appendLockHint(LockOptions lockOptions, String tableName) {
        LockMode lockMode = lockOptions.getAliasSpecificLockMode(tableName);
        if (lockMode == null) {
            lockMode = lockOptions.getLockMode();
        }

        final boolean writeLock =
                lockMode == LockMode.UPGRADE
                        || lockMode == LockMode.PESSIMISTIC_WRITE
                        || lockMode == LockMode.WRITE;
        if (!writeLock || lockOptions.getTimeOut() == LockOptions.SKIP_LOCKED) {
            // Read locks keep holdlock, as in Hibernate 4.3, and skip locked never had it
            return super.appendLockHint(lockOptions, tableName);
        }

        final String noWait = lockOptions.getTimeOut() == LockOptions.NO_WAIT ? ", nowait" : "";
        return tableName + " with (updlock, rowlock" + noWait + ")";
    }
}
