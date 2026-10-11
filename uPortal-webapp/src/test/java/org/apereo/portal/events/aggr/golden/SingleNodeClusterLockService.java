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
package org.apereo.portal.events.aggr.golden;

import com.google.common.base.Function;
import java.util.HashSet;
import java.util.Set;
import org.apereo.portal.concurrency.locking.ClusterMutex;
import org.apereo.portal.concurrency.locking.IClusterLockService;
import org.apereo.portal.concurrency.locking.LockOptions;

/**
 * A cluster lock for a single node: every lock is free, and is owned by the calling thread while
 * its function runs. The aggregation goldens test the aggregation, not the locking (which has its
 * own database-backed tests); this keeps the run independent of lock timing.
 */
public class SingleNodeClusterLockService implements IClusterLockService {
    private final ThreadLocal<Set<String>> ownedLocks = ThreadLocal.withInitial(HashSet::new);

    @Override
    public ClusterMutex getClusterMutex(String mutexName) {
        return null;
    }

    @Override
    public <T> TryLockFunctionResult<T> doInTryLock(
            String mutexName, Function<ClusterMutex, T> lockFunction) {
        return doInTryLock(mutexName, LockOptions.builder(), lockFunction);
    }

    @Override
    public <T> TryLockFunctionResult<T> doInTryLock(
            String mutexName, LockOptions lockOptions, Function<ClusterMutex, T> lockFunction) {
        final boolean acquired = ownedLocks.get().add(mutexName);
        final T result;
        try {
            result = lockFunction.apply(null);
        } finally {
            if (acquired) {
                ownedLocks.get().remove(mutexName);
            }
        }
        return new TryLockFunctionResult<T>() {
            @Override
            @Deprecated
            public boolean isExecuted() {
                return true;
            }

            @Override
            public LockStatus getLockStatus() {
                return LockStatus.EXECUTED;
            }

            @Override
            public T getResult() {
                return result;
            }
        };
    }

    @Override
    public boolean isLockOwner(String mutexName) {
        return ownedLocks.get().contains(mutexName);
    }

    @Override
    public boolean isLocked(String mutexName) {
        return isLockOwner(mutexName);
    }
}
