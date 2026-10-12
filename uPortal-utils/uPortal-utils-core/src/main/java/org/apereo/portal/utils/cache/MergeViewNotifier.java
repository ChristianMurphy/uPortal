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
package org.apereo.portal.utils.cache;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jgroups.Event;
import org.jgroups.MergeView;
import org.jgroups.stack.Protocol;

/**
 * A JGroups protocol that reports each {@link MergeView} to the registered listeners, such as
 * {@link MergeViewCacheClearer}.
 *
 * <p>JGroups installs a MergeView when the two sides of a split cluster join again. GMS passes
 * every view up the stack, so this protocol goes at the top of the stack in {@code jgroups.xml}:
 *
 * <pre>{@code <org.apereo.portal.utils.cache.MergeViewNotifier />}</pre>
 *
 * <p>JGroups creates the protocol itself, so the listeners register with this class rather than
 * with an instance. Every message and event passes on unchanged.
 */
public class MergeViewNotifier extends Protocol {

    private static final List<Consumer<MergeView>> listeners = new CopyOnWriteArrayList<>();
    private static final AtomicInteger stacks = new AtomicInteger();

    /** Calls the listener with each MergeView, on the JGroups thread that installs it. */
    public static void addListener(Consumer<MergeView> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<MergeView> listener) {
        listeners.remove(listener);
    }

    /** Whether a JGroups stack with this protocol has started in this class loader. */
    public static boolean isInAStack() {
        return stacks.get() > 0;
    }

    @Override
    public void init() throws Exception {
        super.init();
        stacks.incrementAndGet();
    }

    @Override
    public void destroy() {
        stacks.decrementAndGet();
        super.destroy();
    }

    @Override
    public Object up(Event evt) {
        if (evt.getType() == Event.VIEW_CHANGE && evt.getArg() instanceof MergeView) {
            final MergeView view = (MergeView) evt.getArg();
            for (Consumer<MergeView> listener : listeners) {
                listener.accept(view);
            }
        }
        return up_prot != null ? up_prot.up(evt) : null;
    }
}
