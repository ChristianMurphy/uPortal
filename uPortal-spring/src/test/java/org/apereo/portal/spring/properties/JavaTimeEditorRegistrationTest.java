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
package org.apereo.portal.spring.properties;

import static org.junit.Assert.assertEquals;

import java.beans.PropertyEditor;
import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.springframework.beans.BeanWrapperImpl;

/**
 * Property text reaches java.time properties through the registrar, as applicationContext.xml wires
 * it.
 */
public class JavaTimeEditorRegistrationTest {

    public static class Settings {
        private Duration timeout;
        private TemporalAmount delay;

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public TemporalAmount getDelay() {
            return delay;
        }

        public void setDelay(TemporalAmount delay) {
            this.delay = delay;
        }
    }

    @Test
    public void convertsPropertyText() {
        final Map<Class<?>, PropertyEditor> editors = new LinkedHashMap<>();
        editors.put(Duration.class, new DurationEditor());
        editors.put(TemporalAmount.class, new TemporalAmountEditor());
        final PortalPropertyEditorRegistrar registrar = new PortalPropertyEditorRegistrar();
        registrar.setPropertyEditors(editors);

        final Settings settings = new Settings();
        final BeanWrapperImpl wrapper = new BeanWrapperImpl(settings);
        registrar.registerCustomEditors(wrapper);
        wrapper.setPropertyValue("timeout", "PT0.100S");
        wrapper.setPropertyValue("delay", "P30D");

        assertEquals(Duration.ofMillis(100), settings.getTimeout());
        assertEquals(Period.ofDays(30), settings.getDelay());
    }
}
