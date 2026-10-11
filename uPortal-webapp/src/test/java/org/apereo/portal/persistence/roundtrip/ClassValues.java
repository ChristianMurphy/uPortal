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
package org.apereo.portal.persistence.roundtrip;

import java.util.Arrays;
import java.util.List;
import org.apereo.portal.events.LoginEvent;
import org.apereo.portal.events.LogoutEvent;
import org.apereo.portal.events.PortalEvent;
import org.apereo.portal.events.PortalRenderEvent;
import org.apereo.portal.events.PortletRenderExecutionEvent;
import org.apereo.portal.events.PortletResourceExecutionEvent;
import org.apereo.portal.events.aggr.IPortalEventAggregator;
import org.apereo.portal.events.aggr.action.SearchRequestAggregator;
import org.apereo.portal.events.aggr.concuser.ConcurrentUserAggregator;
import org.apereo.portal.events.aggr.login.LoginPortalEventAggregator;
import org.apereo.portal.events.aggr.portletexec.PortletExecutionAggregator;
import org.apereo.portal.events.aggr.portletlayout.PortletLayoutAggregator;
import org.apereo.portal.events.aggr.tabrender.TabRenderAggregator;

/** Values for {@code Class} columns (Hibernate's "class" type), by the field's type bound. */
final class ClassValues {
    private static final List<Class<?>> EVENTS =
            Arrays.asList(
                    LoginEvent.class,
                    PortletResourceExecutionEvent.class,
                    PortalRenderEvent.class,
                    LogoutEvent.class,
                    PortletRenderExecutionEvent.class);

    /** Distinct per variant: the aggregator type is a natural id */
    private static final List<Class<?>> AGGREGATORS =
            Arrays.asList(
                    LoginPortalEventAggregator.class,
                    TabRenderAggregator.class,
                    PortletExecutionAggregator.class,
                    ConcurrentUserAggregator.class,
                    PortletLayoutAggregator.class,
                    SearchRequestAggregator.class);

    private ClassValues() {}

    static Class<?> forBound(Class<?> bound, Variant variant, int seed) {
        final List<Class<?>> candidates;
        if (PortalEvent.class.isAssignableFrom(bound)) {
            candidates = EVENTS;
        } else if (IPortalEventAggregator.class.isAssignableFrom(bound)) {
            candidates = AGGREGATORS;
        } else {
            candidates = Arrays.asList(String.class, Integer.class, Object.class, Class.class);
        }
        if (variant == Variant.TYPICAL) {
            return candidates.get(seed % candidates.size());
        }
        return candidates.get(variant.ordinal() % candidates.size());
    }
}
