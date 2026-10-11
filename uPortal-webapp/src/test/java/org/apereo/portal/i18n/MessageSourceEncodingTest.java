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
package org.apereo.portal.i18n;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.apereo.portal.spring.context.SpringXmlContextValidator.DefinitionsOnlyContext;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;

/**
 * MOD-I232: the message source in mvcContext.xml reads the Messages bundles as UTF-8, so a
 * translation can be written with plain accented letters as well as with Unicode escapes.
 */
public class MessageSourceEncodingTest {

    private static MessageSource messageSource;

    @BeforeClass
    public static void loadMessageSource() {
        final DefinitionsOnlyContext context =
                new DefinitionsOnlyContext(
                        null, new ClassPathResource("properties/contexts/mvcContext.xml"));
        context.loadDefinitions();
        messageSource = context.getBeanFactory().getBean("messageSource", MessageSource.class);
    }

    @Test
    public void readsAccentedLettersWrittenAsUtf8() {
        assertEquals(
                "Ce portlet ne peut être déplacé",
                messageSource.getMessage("this.portlet.locked", null, Locale.FRENCH));
    }

    @Test
    public void readsAccentedLettersWrittenAsEscapes() {
        assertEquals(
                "Catégories",
                messageSource.getMessage("marketplace.categories", null, Locale.FRENCH));
    }

    @Test
    public void keepsTheEnglishText() {
        assertEquals(
                "This portlet may not be moved",
                messageSource.getMessage("this.portlet.locked", null, Locale.ENGLISH));
    }
}
