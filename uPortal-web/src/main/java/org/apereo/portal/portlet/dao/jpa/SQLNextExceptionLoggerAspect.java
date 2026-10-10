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

import java.sql.SQLException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;

/**
 * Provides additional logging for SQL based exceptions that provide chained exceptions via {@link
 * SQLException#getNextException()}
 */
public class SQLNextExceptionLoggerAspect implements Ordered {
    protected final Log logger = LogFactory.getLog(this.getClass());

    private int order = 0;

    /* (non-Javadoc)
     * @see org.springframework.core.Ordered#getOrder()
     */
    @Override
    public int getOrder() {
        return this.order;
    }

    /**
     * @param order the order to set
     */
    public void setOrder(int order) {
        this.order = order;
    }

    public void logBatchUpdateExceptions(Throwable t) {
        while (!(t instanceof SQLException)) {
            t = t.getCause();
            if (t == null) {
                return;
            }
        }

        SQLException sqle = (SQLException) t;

        // If the SQLException is the root chain the results of getNextException as initCauses
        if (sqle.getCause() == null) {
            SQLException nextException;
            while ((nextException = sqle.getNextException()) != null) {
                try {
                    sqle.initCause(nextException);
                } catch (IllegalStateException e) {
                    // The cause was set to null in the constructor, as Oracle's driver does for a
                    // BatchUpdateException. Log the rest; this aspect must not replace t.
                    logNextExceptions(t, sqle);
                    return;
                }
                sqle = nextException;
            }
        }
        // The SQLException already has a cause so log the results of all getNextException calls
        else {
            logNextExceptions(t, sqle);
        }
    }

    private void logNextExceptions(Throwable t, SQLException sqle) {
        while ((sqle = sqle.getNextException()) != null) {
            this.logger.error("Logging getNextException for root SQLException: " + t, sqle);
        }
    }
}
