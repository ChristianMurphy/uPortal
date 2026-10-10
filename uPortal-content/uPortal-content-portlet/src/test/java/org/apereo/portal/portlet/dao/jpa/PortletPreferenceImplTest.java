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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.apache.pluto.container.om.portlet.Preference;
import org.junit.Test;

/**
 * Preferences read from a portlet descriptor. A {@code <preference>} without {@code <value>} has no
 * value, so its values are null and getValues(key, def) answers the default (found by Pluto's test
 * suite, PreferenceCommonTest.checkGetNoValuePreference).
 */
public class PortletPreferenceImplTest {

    private static Preference descriptorPreference(boolean readOnly, List<String> values) {
        final Preference preference = mock(Preference.class);
        when(preference.getName()).thenReturn("name");
        when(preference.isReadOnly()).thenReturn(readOnly);
        when(preference.getValues()).thenReturn(values);
        return preference;
    }

    @Test
    public void preferenceWithoutValueHasNullValues() {
        final PortletPreferenceImpl preference =
                new PortletPreferenceImpl(descriptorPreference(false, Collections.emptyList()));
        assertEquals("name", preference.getName());
        assertNull(preference.getValues());
    }

    @Test
    public void preferenceWithValuesKeepsThemInOrder() {
        final PortletPreferenceImpl preference =
                new PortletPreferenceImpl(descriptorPreference(true, List.of("b", "a", "b")));
        assertTrue(preference.isReadOnly());
        assertArrayEquals(new String[] {"b", "a", "b"}, preference.getValues());
    }

    @Test
    public void preferenceWithAnEmptyValueKeepsIt() {
        final PortletPreferenceImpl preference =
                new PortletPreferenceImpl(descriptorPreference(false, List.of("")));
        assertArrayEquals(new String[] {""}, preference.getValues());
    }
}
