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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apereo.portal.spring.context.SpringXmlContextValidator.DefinitionsOnlyContext;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.webflow.engine.model.builder.xml.XmlFlowModelBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Safety net for Spring upgrades (WS6, P4-1): loads every Spring XML application context of the
 * portal, the way the web application groups them, and checks every bean definition with {@link
 * SpringXmlContextValidator}. Also validates the Web Flow definitions against their schema.
 *
 * <ul>
 *   <li>The root context: {@code properties/contexts/*.xml}, as {@code PortalWebAppInitializer}
 *       loads it.
 *   <li>One child context per {@code DispatcherServlet} in {@code web.xml}, plus the view
 *       definitions that {@code XmlViewResolver} loads.
 *   <li>One child context per portlet in {@code portlet.xml}.
 *   <li>Every Spring XML file under {@code src/test/resources}, on its own.
 *   <li>Every flow definition under {@code WEB-INF/flows}.
 * </ul>
 *
 * A context file that none of these groups loads fails the test, so new contexts are covered.
 */
public class SpringXmlContextsTest {

    private static final String WEBAPP = "src/main/webapp/WEB-INF/";
    private static final String CONTEXTS = "src/main/resources/properties/contexts";

    private static DefinitionsOnlyContext root;
    private static final List<String> loaded = new ArrayList<>();

    @BeforeClass
    public static void loadRootContext() throws IOException {
        final Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:properties/contexts/*.xml");
        root = new DefinitionsOnlyContext(null, resources);
        root.setDisplayName("root");
        root.loadDefinitions();
        for (final Resource resource : resources) {
            loaded.add(resource.getURL().toString());
        }
    }

    @Test
    public void rootContext() {
        assertNoProblems(SpringXmlContextValidator.validate("root", root.getBeanFactory()));
    }

    @Test
    public void servletContexts() throws Exception {
        final Map<String, List<String>> groups = configLocations(WEBAPP + "web.xml", "servlet");
        // XmlViewResolver in mvcContext.xml loads these as a child of the root context
        groups.put("mvcViews", List.of("classpath:properties/contexts/views/mvcViews.xml"));
        assertTrue("web.xml declares DispatcherServlet contexts", groups.size() > 5);
        assertNoProblems(loadChildren(groups));
    }

    @Test
    public void portletContexts() throws Exception {
        final Map<String, List<String>> groups = configLocations(WEBAPP + "portlet.xml", "portlet");
        assertTrue("portlet.xml declares portlet contexts", groups.size() > 30);
        assertNoProblems(loadChildren(groups));
    }

    @Test
    public void testContexts() throws Exception {
        final List<String> problems = new ArrayList<>();
        for (final Path file : springXmlFiles(Path.of("src/test/resources"))) {
            final String name = file.toString();
            try {
                final DefinitionsOnlyContext context =
                        new DefinitionsOnlyContext(null, new FileSystemResource(file.toFile()));
                context.loadDefinitions();
                problems.addAll(SpringXmlContextValidator.validate(name, context.getBeanFactory()));
            } catch (RuntimeException e) {
                problems.add(name + ": " + describe(e));
            }
        }
        assertNoProblems(problems);
    }

    /** Every Spring XML context in src/main is loaded by one of the groups above. */
    @Test
    public void everyContextIsCovered() throws Exception {
        final Map<String, List<String>> groups = configLocations(WEBAPP + "web.xml", "servlet");
        groups.putAll(configLocations(WEBAPP + "portlet.xml", "portlet"));
        final TreeSet<String> covered = new TreeSet<>();
        for (final List<String> locations : groups.values()) {
            for (final String location : locations) {
                covered.add(toResource(location).getFile().getName());
            }
        }
        covered.add("mvcViews.xml");
        for (final String url : loaded) {
            covered.add(url.substring(url.lastIndexOf('/') + 1));
        }
        final TreeSet<String> missing = new TreeSet<>();
        for (final Path file : springXmlFiles(Path.of(CONTEXTS))) {
            if (!covered.contains(file.getFileName().toString())) {
                missing.add(file.toString());
            }
        }
        assertEquals("Spring contexts not loaded by any group", new TreeSet<>(), missing);
        assertEquals(
                "root contexts",
                springXmlFiles(Path.of(CONTEXTS)).stream()
                        .filter(p -> p.getParent().equals(Path.of(CONTEXTS)))
                        .count(),
                loaded.size());
    }

    /** Every servlet, filter and listener class that web.xml names can be loaded. */
    @Test
    public void webXmlClasses() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Document document = factory.newDocumentBuilder().parse(new File(WEBAPP + "web.xml"));
        final List<String> missing = new ArrayList<>();
        for (final String element : List.of("servlet-class", "filter-class", "listener-class")) {
            final NodeList nodes = document.getElementsByTagNameNS("*", element);
            for (int i = 0; i < nodes.getLength(); i++) {
                final String name = nodes.item(i).getTextContent().trim();
                if (!ClassUtils.isPresent(name, SpringXmlContextsTest.class.getClassLoader())) {
                    missing.add(element + " " + name);
                }
            }
        }
        assertNoProblems(missing);
    }

    @Test
    public void webFlowDefinitions() throws Exception {
        final List<String> problems = new ArrayList<>();
        final List<Path> flows;
        try (Stream<Path> walk = Files.walk(Path.of(WEBAPP + "flows"))) {
            flows =
                    walk.filter(p -> p.toString().endsWith(".xml"))
                            .sorted()
                            .collect(Collectors.toList());
        }
        assertTrue("flow definitions found", flows.size() > 20);
        for (final Path flow : flows) {
            problems.addAll(validateFlow(flow));
        }
        assertNoProblems(problems);
    }

    /**
     * Build a flow's model the way Web Flow does (which validates it against the flow schema), then
     * check the classes it names.
     */
    static List<String> validateFlow(Path flow) throws Exception {
        final List<String> problems = new ArrayList<>();
        final XmlFlowModelBuilder builder =
                new XmlFlowModelBuilder(new FileSystemResource(flow.toFile()));
        try {
            builder.init();
            builder.build();
        } catch (RuntimeException e) {
            problems.add(flow + ": " + describe(e));
            return problems;
        } finally {
            builder.dispose();
        }
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Document document = factory.newDocumentBuilder().parse(flow.toFile());
        checkClassAttribute(flow, document, "var", "class", problems);
        checkClassAttribute(flow, document, "input", "type", problems);
        checkClassAttribute(flow, document, "output", "type", problems);
        return problems;
    }

    private static void checkClassAttribute(
            Path flow, Document document, String element, String attribute, List<String> out) {
        final NodeList nodes =
                document.getElementsByTagNameNS(
                        "http://www.springframework.org/schema/webflow", element);
        for (int i = 0; i < nodes.getLength(); i++) {
            final String value = ((Element) nodes.item(i)).getAttribute(attribute);
            // Short names such as "string" or "boolean" are conversion-service aliases
            if (value.contains(".")
                    && !ClassUtils.isPresent(value, SpringXmlContextsTest.class.getClassLoader())) {
                out.add(flow + ": <" + element + " " + attribute + "=\"" + value + "\"> not found");
            }
        }
    }

    /**
     * Load each group of config locations as a child of the root context.
     *
     * @return the problems found in all of them
     */
    private static List<String> loadChildren(Map<String, List<String>> groups) {
        final List<String> problems = new ArrayList<>();
        for (final Map.Entry<String, List<String>> group : groups.entrySet()) {
            final String name = group.getKey() + " " + group.getValue();
            try {
                final Resource[] resources =
                        group.getValue().stream()
                                .map(SpringXmlContextsTest::toResource)
                                .toArray(Resource[]::new);
                final DefinitionsOnlyContext context = new DefinitionsOnlyContext(root, resources);
                context.loadDefinitions();
                problems.addAll(SpringXmlContextValidator.validate(name, context.getBeanFactory()));
            } catch (RuntimeException e) {
                problems.add(name + ": " + describe(e));
            }
        }
        return problems;
    }

    /** The contextConfigLocation init-params of each servlet or portlet in a deployment file. */
    static Map<String, List<String>> configLocations(String file, String kind) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Document document = factory.newDocumentBuilder().parse(new File(file));
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        final NodeList components = document.getElementsByTagNameNS("*", kind);
        for (int i = 0; i < components.getLength(); i++) {
            final Element component = (Element) components.item(i);
            final String name = text(component, kind + "-name");
            final NodeList params = component.getElementsByTagNameNS("*", "init-param");
            for (int j = 0; j < params.getLength(); j++) {
                final Element param = (Element) params.item(j);
                if ("contextConfigLocation".equals(text(param, "name"))
                        || "contextConfigLocation".equals(text(param, "param-name"))) {
                    String value = text(param, "value");
                    if (value == null) {
                        value = text(param, "param-value");
                    }
                    groups.put(name, List.of(StringUtils.tokenizeToStringArray(value, ",; \t\n")));
                }
            }
        }
        return groups;
    }

    private static String text(Element parent, String child) {
        final NodeList nodes = parent.getElementsByTagNameNS("*", child);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    /** Map a deployment-descriptor location to the file in this project. */
    private static Resource toResource(String location) {
        if (location.startsWith("/WEB-INF/classes/")) {
            return new ClassPathResource(location.substring("/WEB-INF/classes/".length()));
        }
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length()));
        }
        return new FileSystemResource("src/main/webapp" + location);
    }

    /** The Spring bean definition files (root element beans:beans) under a directory. */
    static List<Path> springXmlFiles(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".xml"))
                    .filter(SpringXmlContextsTest::isBeansFile)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static boolean isBeansFile(Path file) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            final Element element =
                    factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
            return "beans".equals(element.getLocalName())
                    && "http://www.springframework.org/schema/beans"
                            .equals(element.getNamespaceURI());
        } catch (Exception e) {
            return false;
        }
    }

    private static String describe(Throwable e) {
        final StringBuilder message = new StringBuilder(e.toString());
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            message.append("\n    caused by: ").append(cause);
        }
        return message.toString();
    }

    private static void assertNoProblems(List<String> problems) {
        if (!problems.isEmpty()) {
            fail(problems.size() + " problem(s):\n" + String.join("\n", problems));
        }
    }
}
