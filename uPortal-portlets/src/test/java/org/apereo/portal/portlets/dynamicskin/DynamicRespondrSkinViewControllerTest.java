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
package org.apereo.portal.portlets.dynamicskin;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import javax.portlet.PortletContext;
import org.junit.Before;
import org.junit.Test;

public class DynamicRespondrSkinViewControllerTest {

    private final PortletContext portletContext = mock(PortletContext.class);
    private final DynamicRespondrSkinViewController controller =
            new DynamicRespondrSkinViewController();

    @Before
    public void setUp() throws Exception {
        when(portletContext.getResource("/media/skins/respondr/defaultSkin-rtl.css"))
                .thenReturn(new URL("file:/webapp/media/skins/respondr/defaultSkin-rtl.css"));
        controller.setPortletContext(portletContext);
    }

    @Test
    public void leftToRightGetsTheSkin() {
        assertEquals(
                "/media/skins/respondr/defaultSkin.css",
                controller.calculateDefaultSkinCssLocationInWebapp("defaultSkin", false));
    }

    @Test
    public void rightToLeftGetsTheMirroredCopy() {
        assertEquals(
                "/media/skins/respondr/defaultSkin-rtl.css",
                controller.calculateDefaultSkinCssLocationInWebapp("defaultSkin", true));
    }

    @Test
    public void rightToLeftWithoutAMirroredCopyGetsTheSkin() {
        // A skin an adopter built has no -rtl copy unless the adopter makes one
        assertEquals(
                "/media/skins/respondr/mySkin.css",
                controller.calculateDefaultSkinCssLocationInWebapp("mySkin", true));
    }
}
