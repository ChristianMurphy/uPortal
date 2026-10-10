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
package org.apereo.portal.portlets.iframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public class IFramePortletControllerTest {

    @Test
    public void iframeAttributesKeepTheirOrder() {
        assertEquals(
                Arrays.asList(
                        "id",
                        "cssClass",
                        "style",
                        "title",
                        "longDescription",
                        "name",
                        "src",
                        "frameBorder",
                        "marginWidth",
                        "marginHeight",
                        "scrolling",
                        "align",
                        "width",
                        "height"),
                new ArrayList<>(IFramePortletController.IFRAME_ATTRS.keySet()));
    }

    @Test
    public void iframeAttributesHaveTheirDefaults() {
        assertEquals("0", IFramePortletController.IFRAME_ATTRS.get("frameBorder"));
        assertEquals("100%", IFramePortletController.IFRAME_ATTRS.get("width"));
        assertNull(IFramePortletController.IFRAME_ATTRS.get("height"));
        assertNull(IFramePortletController.IFRAME_ATTRS.get("src"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void iframeAttributesCannotBeChanged() {
        IFramePortletController.IFRAME_ATTRS.put("src", "https://example.org/");
    }
}
