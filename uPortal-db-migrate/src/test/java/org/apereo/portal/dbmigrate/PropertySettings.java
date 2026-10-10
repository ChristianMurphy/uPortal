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
package org.apereo.portal.dbmigrate;

import net.jqwik.api.lifecycle.AroundPropertyHook;
import net.jqwik.api.lifecycle.PropagationMode;
import net.jqwik.api.lifecycle.PropertyAttributes;
import net.jqwik.api.lifecycle.PropertyExecutionResult;
import net.jqwik.api.lifecycle.PropertyExecutor;
import net.jqwik.api.lifecycle.PropertyLifecycleContext;

/**
 * How deep the copy tool's property tests go, from the build (MOD-D29):
 *
 * <ul>
 *   <li>{@code dbmigrate.pbt.seed}: the random seed of every property. The build passes a fixed one
 *       by default, so a push run is repeatable; {@code random} (the nightly run) lets jqwik pick
 *       one, which it prints when a property fails ({@code -PdbMigrateSeed=<seed>} repeats it).
 *   <li>{@code dbmigrate.pbt.factor}: multiplies each property's tries (1 on push, 10 nightly).
 * </ul>
 *
 * A seed in a property's own annotation wins.
 */
public class PropertySettings implements AroundPropertyHook {

    @Override
    public PropertyExecutionResult aroundProperty(
            PropertyLifecycleContext context, PropertyExecutor property) {
        PropertyAttributes attributes = context.attributes();
        String seed = System.getProperty("dbmigrate.pbt.seed", "");
        // An annotation without a seed reports it as empty, or not at all
        boolean annotated = attributes.seed().filter(s -> !s.isEmpty()).isPresent();
        if (!seed.isEmpty() && !"random".equals(seed) && !annotated) {
            attributes.setSeed(seed);
        }
        int factor = Integer.getInteger("dbmigrate.pbt.factor", 1);
        attributes.tries().ifPresent(tries -> attributes.setTries(tries * Math.max(1, factor)));
        return property.execute();
    }

    /** Registered on a test class, the hook applies to the class's properties. */
    @Override
    public PropagationMode propagateTo() {
        return PropagationMode.ALL_DESCENDANTS;
    }

    @Override
    public int aroundPropertyProximity() {
        // Outside jqwik's own hooks, so the attributes are set before it reads them
        return -100;
    }
}
