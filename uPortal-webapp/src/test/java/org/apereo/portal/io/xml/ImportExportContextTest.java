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
package org.apereo.portal.io.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import org.apereo.portal.EntityIdentifier;
import org.apereo.portal.groups.IEntityGroup;
import org.apereo.portal.groups.IGroupConstants.SearchMethod;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupDefinition;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupDefinitionDao;
import org.apereo.portal.io.xml.IPortalDataHandlerService.BatchExportOptions;
import org.apereo.portal.io.xml.IPortalDataHandlerService.BatchImportOptions;
import org.apereo.portal.io.xml.crn.CernunnosDataExporter;
import org.apereo.portal.io.xml.dlm.FragmentDefinitionExporter;
import org.apereo.portal.io.xml.dlm.FragmentDefinitionPortalDataType;
import org.apereo.portal.io.xml.entitytype.EntityTypePortalDataType;
import org.apereo.portal.io.xml.eventaggr.EventAggregationConfigurationPortalDataType;
import org.apereo.portal.io.xml.group.GroupMembershipPortalDataType;
import org.apereo.portal.io.xml.layout.FragmentLayoutPortalDataType;
import org.apereo.portal.io.xml.layout.LayoutPortalDataType;
import org.apereo.portal.io.xml.layout.ProfilePortalDataType;
import org.apereo.portal.io.xml.pags.PersonAttributesGroupStoreExporter;
import org.apereo.portal.io.xml.pags.PersonAttributesGroupStorePortalDataType;
import org.apereo.portal.io.xml.permission.PermissionOwnerPortalDataType;
import org.apereo.portal.io.xml.permission.PermissionSetPortalDataType;
import org.apereo.portal.io.xml.portlet.PortletPortalDataType;
import org.apereo.portal.io.xml.portlettype.PortletTypePortalDataType;
import org.apereo.portal.io.xml.ssd.StylesheetDescriptorPortalDataType;
import org.apereo.portal.io.xml.user.UserPortalDataType;
import org.apereo.portal.layout.dlm.FragmentDefinition;
import org.apereo.portal.layout.dlm.IFragmentDefinitionDao;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.services.GroupService;
import org.apereo.portal.utils.Tuple;
import org.apereo.portal.xml.XmlUtilitiesImpl;
import org.dom4j.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Checks that the portal can import what it exports, with the importers, upgraders and exporters
 * that {@code importExportContext.xml} defines.
 */
public class ImportExportContextTest {
    private static final String IMPORT_EXPORT_CONTEXT =
            "classpath:/properties/contexts/importExportContext.xml";

    /**
     * The data key that each exporter in importExportContext.xml writes. The Cernunnos exporters
     * take it from the {@code script} attribute of their export script, the PAGS and fragment
     * definition exporters from their code, the layout exporters from {@code
     * RDBMDistributedLayoutStore}, and the JAXB exporters from the fixed {@code version} of their
     * schema.
     */
    private static final Map<String, PortalDataKey> EXPORTED_DATA_KEYS = new LinkedHashMap<>();

    static {
        EXPORTED_DATA_KEYS.put(
                "portletDefinitionImporterExporter", PortletPortalDataType.IMPORT_50_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "portletTypeImporterExporter", PortletTypePortalDataType.IMPORT_40_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "stylesheetDescriptorImporterExporter",
                StylesheetDescriptorPortalDataType.IMPORT_40_DATA_KEY);
        EXPORTED_DATA_KEYS.put("userImporterExporter", UserPortalDataType.IMPORT_40_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "permissionOwnerImporterExporter",
                PermissionOwnerPortalDataType.IMPORT_40_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "eventAggregationConfigurationImporterExporter",
                EventAggregationConfigurationPortalDataType.IMPORT_40_DATA_KEY);
        EXPORTED_DATA_KEYS.put("entityTypeExporter", EntityTypePortalDataType.IMPORT_32_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "groupMembershipExporter", GroupMembershipPortalDataType.IMPORT_50_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "personAttributesGroupStoreExporter",
                PersonAttributesGroupStorePortalDataType.IMPORT_PAGS_41_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "permissionSetExporter", PermissionSetPortalDataType.IMPORT_50_DATA_KEY);
        EXPORTED_DATA_KEYS.put("profileExporter", ProfilePortalDataType.IMPORT_32_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "fragmentLayoutExporter", FragmentLayoutPortalDataType.IMPORT_32_DATA_KEY);
        EXPORTED_DATA_KEYS.put("layoutExporter", LayoutPortalDataType.IMPORT_32_DATA_KEY);
        EXPORTED_DATA_KEYS.put(
                "fragmentDefinitionExporter", FragmentDefinitionPortalDataType.IMPORT_31_DATA_KEY);
    }

    /** Beans that importExportContext.xml passes to Cernunnos scripts this test doesn't run. */
    private static final List<String> UNUSED_SCRIPT_ATTRIBUTES =
            Arrays.asList(
                    "entityTypes",
                    "fragmentDefinitionDao",
                    "personAttributesGroupDefinitionDao",
                    "personAttributesGroupTestDefinitionDao",
                    "personAttributesGroupTestGroupDefinitionDao",
                    "stylesheetDescriptorDao",
                    "stylesheetUserPreferencesDao");

    private static final Pattern SCRIPT_ATTRIBUTE = Pattern.compile("script=([^)\\s]+)\\)");

    private static final String GROUP_ENTITY_TYPE = "org.apereo.portal.groups.IEntityGroup";

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private DefaultListableBeanFactory beanFactory;
    private List<IPortalDataType> portalDataTypes;

    @Before
    public void setUp() {
        dataSource =
                new EmbeddedDatabaseBuilder()
                        .generateUniqueName(true)
                        .setType(EmbeddedDatabaseType.HSQL)
                        .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                "CREATE TABLE UP_ENTITY_TYPE (ENTITY_TYPE_ID INTEGER NOT NULL PRIMARY KEY,"
                        + " ENTITY_TYPE_NAME VARCHAR(255), DESCRIPTIVE_NAME VARCHAR(255))");
        jdbcTemplate.execute(
                "CREATE TABLE UP_PERMISSION (OWNER VARCHAR(255), PRINCIPAL_TYPE INTEGER,"
                        + " PRINCIPAL_KEY VARCHAR(255), ACTIVITY VARCHAR(255), TARGET VARCHAR(255),"
                        + " PERMISSION_TYPE VARCHAR(255), EFFECTIVE TIMESTAMP, EXPIRES TIMESTAMP)");

        beanFactory = new DefaultListableBeanFactory();
        beanFactory.addPropertyEditorRegistrar(
                new ResourceEditorRegistrar(
                        new DefaultResourceLoader(), new StandardEnvironment()));
        // UserImporterExporter gets PortalDb through @Resource
        final CommonAnnotationBeanPostProcessor resourceInjection =
                new CommonAnnotationBeanPostProcessor();
        resourceInjection.setBeanFactory(beanFactory);
        beanFactory.addBeanPostProcessor(resourceInjection);

        beanFactory.registerSingleton("PortalDb", dataSource);
        beanFactory.registerSingleton(
                "transactionManager", new DataSourceTransactionManager(dataSource));
        for (final String name : UNUSED_SCRIPT_ATTRIBUTES) {
            beanFactory.registerSingleton(name, new Object());
        }

        // The data types, found the way the portal's component scan finds them
        final ClassPathBeanDefinitionScanner scanner =
                new ClassPathBeanDefinitionScanner(beanFactory, false);
        scanner.setIncludeAnnotationConfig(false);
        scanner.addIncludeFilter(
                (reader, factory) ->
                        new AnnotationTypeFilter(Component.class).match(reader, factory)
                                && new AssignableTypeFilter(IPortalDataType.class)
                                        .match(reader, factory));
        scanner.scan("org.apereo.portal");

        new XmlBeanDefinitionReader(beanFactory).loadBeanDefinitions(IMPORT_EXPORT_CONTEXT);

        portalDataTypes = beansOfType(IPortalDataType.class);
        AnnotationAwareOrderComparator.sort(portalDataTypes);
    }

    @After
    public void tearDown() {
        beanFactory.destroySingletons();
        dataSource.shutdown();
    }

    /** dataExport of a permission set, then dataImport of the exported file. */
    @Test
    public void testPermissionSetExportImportsAgain() throws Exception {
        jdbcTemplate.update("INSERT INTO UP_ENTITY_TYPE VALUES (2, ?, 'Group')", GROUP_ENTITY_TYPE);
        jdbcTemplate.update(
                "INSERT INTO UP_PERMISSION (OWNER, PRINCIPAL_TYPE, PRINCIPAL_KEY, ACTIVITY, TARGET,"
                        + " PERMISSION_TYPE) VALUES ('UP_ERROR_CHAN', 2, 'local.3', 'VIEW',"
                        + " 'DETAILS', 'GRANT')");
        jdbcTemplate.update(
                "INSERT INTO UP_PERMISSION (OWNER, PRINCIPAL_TYPE, PRINCIPAL_KEY, ACTIVITY, TARGET,"
                        + " PERMISSION_TYPE) VALUES ('UP_GROUPSMANAGER', 2, 'local.2',"
                        + " 'VIEW_GROUP', 'local.0', 'GRANT')");
        final List<Map<String, Object>> exportedPermissions = readPermissions();

        final JaxbPortalDataHandlerService dataHandlerService = newDataHandlerService();
        final File exportDirectory = tempFolder.newFolder("export");
        final File logDirectory = tempFolder.newFolder("logs");

        try (MockedStatic<GroupService> groupService = mockStatic(GroupService.class)) {
            groupService
                    .when(
                            () ->
                                    GroupService.searchForGroups(
                                            anyString(), any(SearchMethod.class), any()))
                    .thenReturn(new EntityIdentifier[0]);
            mockGroup(groupService, "local.0", "Everyone");
            mockGroup(groupService, "local.2", "Portal Administrators");
            mockGroup(groupService, "local.3", "Portal Developers");

            dataHandlerService.exportAllDataOfType(
                    Collections.singleton("permission-set"),
                    exportDirectory,
                    new BatchExportOptions().setLogDirectoryParent(logDirectory));
            assertEquals(
                    "exported files: " + exportedFiles(exportDirectory),
                    2,
                    exportedFiles(exportDirectory).size());

            jdbcTemplate.update("DELETE FROM UP_PERMISSION");

            try {
                dataHandlerService.importDataDirectory(
                        exportDirectory,
                        null,
                        new BatchImportOptions().setLogDirectoryParent(logDirectory));
            } catch (RuntimeException e) {
                throw new AssertionError(
                        e.getMessage()
                                + importFailures(new File(logDirectory, "data-import-reports")),
                        e);
            }
        }

        assertEquals(exportedPermissions, readPermissions());
    }

    /**
     * For each exporter in importExportContext.xml: the data key it writes, and an importer or an
     * upgrader for that key.
     */
    @Test
    public void testEveryExportedDataKeyHasAnImporterOrUpgrader() throws Exception {
        assertEquals(
                "EXPORTED_DATA_KEYS lists every exporter in importExportContext.xml",
                new TreeSet<>(EXPORTED_DATA_KEYS.keySet()),
                new TreeSet<>(
                        Arrays.asList(
                                beanFactory.getBeanNamesForType(
                                        IDataExporter.class, false, false))));

        final Set<PortalDataKey> handledKeys = importerAndUpgraderKeys();
        final Map<PortalDataKey, IPortalDataType> dataKeyTypes = new LinkedHashMap<>();
        for (final IPortalDataType portalDataType : portalDataTypes) {
            for (final PortalDataKey key : portalDataType.getDataKeyImportOrder()) {
                dataKeyTypes.put(key, portalDataType);
            }
        }

        final List<String> problems = new ArrayList<>();
        for (final Map.Entry<String, PortalDataKey> exported : EXPORTED_DATA_KEYS.entrySet()) {
            final String exporter = exported.getKey();
            final PortalDataKey key = exported.getValue();

            final PortalDataKey written = writtenDataKey(exporter);
            if (written != null && !written.equals(key)) {
                problems.add(exporter + " writes " + written + ", the table says " + key);
            }

            final IPortalDataType portalDataType = dataKeyTypes.get(key);
            if (portalDataType == null) {
                problems.add(exporter + " writes " + key + ", which no IPortalDataType lists");
                continue;
            }
            for (final PortalDataKey step :
                    portalDataType.postProcessPortalDataKey(exporter, key, null)) {
                if (!handledKeys.contains(step)) {
                    problems.add(
                            exporter
                                    + " writes "
                                    + key
                                    + ", and importExportContext.xml has no importer or upgrader for "
                                    + step);
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    /**
     * For each data key that a data type lists: an importer or an upgrader. The import reads a file
     * whose key a data type lists and then fails when nothing takes that key.
     */
    @Test
    public void testEveryListedDataKeyHasAnImporterOrUpgrader() {
        final Set<PortalDataKey> handledKeys = importerAndUpgraderKeys();

        final List<String> problems = new ArrayList<>();
        for (final IPortalDataType portalDataType : portalDataTypes) {
            final String dataType = portalDataType.getClass().getSimpleName();
            for (final PortalDataKey key : portalDataType.getDataKeyImportOrder()) {
                for (final PortalDataKey step :
                        portalDataType.postProcessPortalDataKey(dataType, key, null)) {
                    if (!handledKeys.contains(step)) {
                        problems.add(
                                dataType
                                        + " lists "
                                        + key
                                        + ", and importExportContext.xml has no importer or"
                                        + " upgrader for "
                                        + step);
                    }
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    /** The data keys that the importers and upgraders in importExportContext.xml take. */
    private Set<PortalDataKey> importerAndUpgraderKeys() {
        final Set<PortalDataKey> keys = new LinkedHashSet<>();
        for (final IDataImporter<?> importer : beansOfType(IDataImporter.class)) {
            keys.addAll(importer.getImportDataKeys());
        }
        for (final IDataUpgrader upgrader : beansOfType(IDataUpgrader.class)) {
            keys.addAll(upgrader.getSourceDataTypes());
        }
        return keys;
    }

    /**
     * The data key an exporter writes, read from its export script or from the element it builds.
     * Null for the exporters whose key this test can't read without a portal database.
     */
    private PortalDataKey writtenDataKey(String exporterName) throws IOException {
        final Object exporter = beanFactory.getBean(exporterName);
        if (exporter instanceof CernunnosDataExporter) {
            final RuntimeBeanReference task =
                    (RuntimeBeanReference)
                            beanFactory
                                    .getBeanDefinition(exporterName)
                                    .getPropertyValues()
                                    .get("task");
            final TypedStringValue location =
                    (TypedStringValue)
                            beanFactory
                                    .getBeanDefinition(task.getBeanName())
                                    .getPropertyValues()
                                    .get("location");
            final String script =
                    StreamUtils.copyToString(
                            new DefaultResourceLoader()
                                    .getResource(location.getValue())
                                    .getInputStream(),
                            StandardCharsets.UTF_8);
            final Matcher matcher = SCRIPT_ATTRIBUTE.matcher(script);
            assertTrue(location.getValue() + " sets a script attribute", matcher.find());
            final IPortalDataType portalDataType =
                    ((CernunnosDataExporter) exporter).getPortalDataType();
            return new PortalDataKey(
                    portalDataType.getDataKeyImportOrder().get(0).getName(),
                    matcher.group(1),
                    null);
        }
        if (exporter instanceof PersonAttributesGroupStoreExporter) {
            final IPersonAttributesGroupDefinitionDao dao =
                    mock(IPersonAttributesGroupDefinitionDao.class);
            when(dao.getPersonAttributesGroupDefinitionByName("group"))
                    .thenReturn(
                            Collections.singleton(mock(IPersonAttributesGroupDefinition.class)));
            ((PersonAttributesGroupStoreExporter) exporter)
                    .setpersonAttributesGroupDefinitionDao(dao);
            return dataKey(((PersonAttributesGroupStoreExporter) exporter).exportData("group"));
        }
        if (exporter instanceof FragmentDefinitionExporter) {
            final IFragmentDefinitionDao dao = mock(IFragmentDefinitionDao.class);
            when(dao.getFragmentDefinition("fragment")).thenReturn(mock(FragmentDefinition.class));
            ((FragmentDefinitionExporter) exporter).setFragmentDefinitionDao(dao);
            return dataKey(((FragmentDefinitionExporter) exporter).exportData("fragment"));
        }
        return null;
    }

    private static PortalDataKey dataKey(Tuple<String, Element> exported) {
        final Element root = exported.second;
        return new PortalDataKey(
                new QName(root.getNamespaceURI(), root.getName()),
                root.attributeValue("script"),
                root.attributeValue("version"));
    }

    private JaxbPortalDataHandlerService newDataHandlerService() {
        final XmlUtilitiesImpl xmlUtilities = new XmlUtilitiesImpl();
        final CernunnosDataExporter permissionSetExporter =
                beanFactory.getBean("permissionSetExporter", CernunnosDataExporter.class);
        permissionSetExporter.setXmlUtilities(xmlUtilities);

        final JaxbPortalDataHandlerService dataHandlerService = new JaxbPortalDataHandlerService();
        dataHandlerService.setXmlUtilities(xmlUtilities);
        // One thread, so the GroupService mock applies to every import and export task
        dataHandlerService.setImportExportThreadPool(new CallerRunsExecutorService());
        dataHandlerService.setDataTypeImportOrder(portalDataTypes);
        @SuppressWarnings("unchecked")
        final Set<String> dataFileIncludes = (Set<String>) beanFactory.getBean("dataFileIncludes");
        dataHandlerService.setDataFileIncludes(dataFileIncludes);
        final List<IDataImporter<?>> importers = new ArrayList<>();
        for (final IDataImporter<?> importer : beansOfType(IDataImporter.class)) {
            importers.add(importer);
        }
        dataHandlerService.setDataImporters(importers);
        dataHandlerService.setDataUpgraders(beansOfType(IDataUpgrader.class));
        dataHandlerService.setDataExporters(
                Collections.<IDataExporter<?>>singletonList(permissionSetExporter));
        dataHandlerService.init();
        return dataHandlerService;
    }

    /** The beans of a type that importExportContext.xml defines, without its factory beans. */
    private <T> List<T> beansOfType(Class<T> type) {
        final List<T> beans = new ArrayList<>();
        for (final String name : beanFactory.getBeanNamesForType(type, false, false)) {
            beans.add(beanFactory.getBean(name, type));
        }
        return beans;
    }

    private static void mockGroup(
            MockedStatic<GroupService> groupService, String key, String name) {
        final IEntityGroup group = mock(IEntityGroup.class);
        when(group.getKey()).thenReturn(key);
        when(group.getName()).thenReturn(name);
        groupService.when(() -> GroupService.findGroup(key)).thenReturn(group);
        groupService
                .when(
                        () ->
                                GroupService.searchForGroups(
                                        eq(name), eq(SearchMethod.DISCRETE), eq(IPerson.class)))
                .thenReturn(new EntityIdentifier[] {new EntityIdentifier(key, IEntityGroup.class)});
    }

    private List<Map<String, Object>> readPermissions() {
        return jdbcTemplate.queryForList(
                "SELECT OWNER, PRINCIPAL_TYPE, PRINCIPAL_KEY, ACTIVITY, TARGET, PERMISSION_TYPE"
                        + " FROM UP_PERMISSION ORDER BY OWNER, PRINCIPAL_KEY, ACTIVITY, TARGET");
    }

    private static List<String> exportedFiles(File directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory.toPath())) {
            return files.filter(Files::isRegularFile)
                    .map(path -> directory.toPath().relativize(path).toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** The first lines of each per-file report that a failed bulk import wrote. */
    private static String importFailures(File reportDirectory) throws IOException {
        final StringBuilder failures = new StringBuilder();
        final File[] reports = reportDirectory.listFiles((dir, name) -> name.endsWith(".txt"));
        if (reports == null) {
            return "";
        }
        Arrays.sort(reports);
        for (final File report : reports) {
            if (report.getName().equals("data-import.txt")) {
                continue;
            }
            final List<String> lines = Files.readAllLines(report.toPath(), StandardCharsets.UTF_8);
            failures.append('\n')
                    .append(String.join("\n", lines.subList(0, Math.min(3, lines.size()))));
        }
        return failures.toString();
    }

    /** Runs each task in the thread that submits it. */
    private static final class CallerRunsExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
