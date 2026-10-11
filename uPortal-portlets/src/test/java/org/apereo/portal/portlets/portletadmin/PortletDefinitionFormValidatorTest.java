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
package org.apereo.portal.portlets.portletadmin;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import org.apereo.portal.portlet.registry.IPortletDefinitionRegistry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.binding.message.DefaultMessageContext;
import org.springframework.binding.message.Message;
import org.springframework.context.support.StaticMessageSource;

/**
 * A portlet's title, name, description and fname fit their columns, counted in characters (code
 * points), in any script (MOD-I272).
 */
public class PortletDefinitionFormValidatorTest {
    private PortletDefinitionFormValidator validator;

    @Before
    public void setUp() {
        validator = new PortletDefinitionFormValidator();
        validator.setPortletDefinitionRegistry(mock(IPortletDefinitionRegistry.class));
    }

    private static String repeat(String s, int times) {
        return new String(new char[times]).replace("\0", s);
    }

    private List<String> errors(String fname, String title, String name, String description) {
        final PortletDefinitionForm form = new PortletDefinitionForm();
        form.setFname(fname);
        form.setTitle(title);
        form.setName(name);
        form.setDescription(description);
        final StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        final DefaultMessageContext context = new DefaultMessageContext(messageSource);
        validator.doBasicInfo(form, context);
        final List<String> sources = new ArrayList<>();
        for (final Message message : context.getAllMessages()) {
            sources.add(String.valueOf(message.getSource()));
        }
        return sources;
    }

    @Test
    public void acceptsTextUpToTheColumnSizes() {
        // 128 emoji are 256 UTF-16 chars, but 128 characters
        assertEquals(
                new ArrayList<String>(),
                errors(repeat("f", 255), repeat("😀", 128), repeat("李", 128), repeat("é", 255)));
    }

    @Test
    public void refusesTextLongerThanTheColumns() {
        final List<String> errors =
                errors(repeat("f", 256), repeat("t", 129), repeat("n", 129), repeat("d", 256));
        assertEquals("[fName, title, name, description]", errors.toString());
    }
}
