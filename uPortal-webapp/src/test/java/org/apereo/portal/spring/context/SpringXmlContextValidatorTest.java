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
package org.apereo.portal.spring.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apereo.portal.spring.context.SpringXmlContextValidator.DefinitionsOnlyContext;
import org.junit.Test;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.io.ByteArrayResource;

/**
 * Seeded faults: each kind of breakage a Spring upgrade can cause in an XML context is caught by
 * {@link SpringXmlContextValidator}.
 */
public class SpringXmlContextValidatorTest {

    private static final String HEADER =
            "<beans xmlns=\"http://www.springframework.org/schema/beans\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " xmlns:util=\"http://www.springframework.org/schema/util\""
                    + " xsi:schemaLocation=\""
                    + "http://www.springframework.org/schema/beans"
                    + " http://www.springframework.org/schema/beans/spring-beans.xsd"
                    + " http://www.springframework.org/schema/util"
                    + " http://www.springframework.org/schema/util/spring-util.xsd\">";

    /** A bean class for the fixtures. */
    public static class Sample {
        public Sample() {}

        public Sample(String name) {}

        public void setName(String name) {}

        public void setOther(Sample other) {}

        public void start() {}

        public static Sample create() {
            return new Sample();
        }
    }

    private static List<String> problems(String beans) {
        final DefinitionsOnlyContext context =
                new DefinitionsOnlyContext(
                        null,
                        new ByteArrayResource(
                                (HEADER + beans + "</beans>").getBytes(StandardCharsets.UTF_8),
                                "fixture"));
        context.loadDefinitions();
        return SpringXmlContextValidator.validate("fixture", context.getBeanFactory());
    }

    private static void assertProblem(String expected, String beans) {
        final List<String> problems = problems(beans);
        assertEquals(problems.toString(), 1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains(expected));
    }

    private static final String SAMPLE_BINARY = Sample.class.getName();

    @Test
    public void validDefinitionsHaveNoProblems() {
        assertEquals(
                List.of(),
                problems(
                        "<bean id='a' class='"
                                + SAMPLE_BINARY
                                + "' init-method='start'>"
                                + "<property name='name' value='x'/>"
                                + "<property name='other'><bean class='"
                                + SAMPLE_BINARY
                                + "'/></property></bean>"
                                + "<bean id='b' class='"
                                + SAMPLE_BINARY
                                + "'><constructor-arg value='y'/></bean>"
                                + "<bean id='c' class='"
                                + SAMPLE_BINARY
                                + "' factory-method='create'/>"
                                + "<bean id='d' parent='a'><property name='other' ref='b'/></bean>"
                                + "<util:list id='e'><ref bean='a'/></util:list>"));
    }

    @Test
    public void misspelledClass() {
        assertProblem("cannot be loaded", "<bean id='a' class='" + SAMPLE_BINARY + "Misspelled'/>");
    }

    @Test
    public void misspelledInnerBeanClass() {
        assertProblem(
                "cannot be loaded",
                "<bean id='a' class='"
                        + SAMPLE_BINARY
                        + "'><property name='other'><bean class='"
                        + SAMPLE_BINARY
                        + "Typo'/></property></bean>");
    }

    @Test
    public void propertyWithoutSetter() {
        assertProblem(
                "property 'removed' is not writable",
                "<bean id='a' class='"
                        + SAMPLE_BINARY
                        + "' p:removed='x'"
                        + " xmlns:p='http://www.springframework.org/schema/p'/>");
    }

    @Test
    public void constructorArgumentsWithoutConstructor() {
        assertProblem(
                "no constructor",
                "<bean id='a' class='"
                        + SAMPLE_BINARY
                        + "'><constructor-arg value='1'/><constructor-arg value='2'/></bean>");
    }

    @Test
    public void missingFactoryMethod() {
        assertProblem(
                "factory method build",
                "<bean id='a' class='" + SAMPLE_BINARY + "' factory-method='build'/>");
    }

    @Test
    public void missingInitMethod() {
        assertProblem(
                "init-method begin()",
                "<bean id='a' class='" + SAMPLE_BINARY + "' init-method='begin'/>");
    }

    @Test
    public void referenceToUndefinedBean() {
        assertProblem(
                "references bean 'nothing'",
                "<bean id='a' class='"
                        + SAMPLE_BINARY
                        + "'><property name='other' ref='nothing'/></bean>");
    }

    @Test
    public void attributeTheSchemaDoesNotDeclare() {
        try {
            problems("<bean id='a' class='" + SAMPLE_BINARY + "' singleton='true'/>");
            fail("expected a schema validation error");
        } catch (BeanDefinitionStoreException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("singleton"));
        }
    }

    @Test
    public void unresolvablePlaceholder() {
        try {
            problems(
                    "<bean class='org.springframework.context.support"
                            + ".PropertySourcesPlaceholderConfigurer'/>"
                            + "<bean id='a' class='"
                            + SAMPLE_BINARY
                            + "' p:name='${no.such.property}'"
                            + " xmlns:p='http://www.springframework.org/schema/p'/>");
            fail("expected an unresolvable placeholder");
        } catch (BeanDefinitionStoreException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no.such.property"));
        }
    }
}
