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
package org.apereo.portal.portlets.groupadmin;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.springframework.binding.message.DefaultMessageContext;
import org.springframework.binding.message.Message;
import org.springframework.context.support.StaticMessageSource;

/**
 * A local group's name and description fit their 255 columns, counted in characters (code points),
 * in any script (MOD-I272).
 */
public class GroupFormValidatorTest {

    private static String repeat(String s, int times) {
        return new String(new char[times]).replace("\0", s);
    }

    private static List<String> errors(String name, String description) {
        final GroupForm group = new GroupForm();
        group.setName(name);
        group.setDescription(description);
        final StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.setUseCodeAsDefaultMessage(true);
        final DefaultMessageContext context = new DefaultMessageContext(messageSource);
        new GroupFormValidator().validateEditDetails(group, context);
        final List<String> sources = new ArrayList<>();
        for (final Message message : context.getAllMessages()) {
            sources.add(String.valueOf(message.getSource()));
        }
        return sources;
    }

    @Test
    public void acceptsTextUpToTheColumnSizes() {
        // 255 emoji are 510 UTF-16 chars, but 255 characters
        assertEquals(new ArrayList<String>(), errors(repeat("😀", 255), repeat("é", 255)));
    }

    @Test
    public void refusesTextLongerThanTheColumns() {
        assertEquals("[name, description]", errors(repeat("n", 256), repeat("d", 256)).toString());
    }
}
