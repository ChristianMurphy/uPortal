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
package org.apereo.portal.portlet.dao.jpa;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.Test;

public class SQLNextExceptionLoggerAspectTest {
    private final SQLNextExceptionLoggerAspect aspect = new SQLNextExceptionLoggerAspect();

    @Test
    public void testChainsNextExceptionsAsCauses() {
        final SQLException root = new SQLException("root");
        final SQLException next = new SQLException("next");
        root.setNextException(next);

        aspect.logBatchUpdateExceptions(new RuntimeException(root));

        assertSame(next, root.getCause());
    }

    /** Oracle's driver creates its BatchUpdateException with a cause of null. */
    @Test
    public void testLeavesACauseThatWasSetToNull() {
        final BatchUpdateException root =
                new BatchUpdateException("ORA-00001", "23000", 1, new int[0], null);
        root.setNextException(new SQLIntegrityConstraintViolationException("ORA-00001"));

        aspect.logBatchUpdateExceptions(new RuntimeException(root));

        assertNull(root.getCause());
    }
}
