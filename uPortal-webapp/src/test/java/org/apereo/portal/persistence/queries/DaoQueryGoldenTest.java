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
package org.apereo.portal.persistence.queries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.Cookie;
import org.apereo.portal.dao.portletlist.IPortletListDao;
import org.apereo.portal.dao.portletlist.jpa.PortletList;
import org.apereo.portal.dao.portletlist.jpa.PortletListItem;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupDefinition;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupDefinitionDao;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupTestDefinitionDao;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupTestGroupDefinition;
import org.apereo.portal.groups.pags.dao.IPersonAttributesGroupTestGroupDefinitionDao;
import org.apereo.portal.i18n.dao.IMessageDao;
import org.apereo.portal.jgroups.auth.AuthDao;
import org.apereo.portal.layout.dao.IStylesheetDescriptorDao;
import org.apereo.portal.layout.dao.IStylesheetUserPreferencesDao;
import org.apereo.portal.layout.dlm.FragmentDefinition;
import org.apereo.portal.layout.dlm.IFragmentDefinitionDao;
import org.apereo.portal.layout.om.IStylesheetDescriptor;
import org.apereo.portal.layout.om.IStylesheetUserPreferences;
import org.apereo.portal.layout.profile.dao.IProfileSelectionDao;
import org.apereo.portal.permission.IPermissionOwner;
import org.apereo.portal.permission.dao.IPermissionOwnerDao;
import org.apereo.portal.persondir.ILocalAccountDao;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.apereo.portal.persondir.LocalAccountQuery;
import org.apereo.portal.portlet.dao.IMarketplaceRatingDao;
import org.apereo.portal.portlet.dao.IPortletCookieDao;
import org.apereo.portal.portlet.dao.IPortletDefinitionDao;
import org.apereo.portal.portlet.dao.IPortletEntityDao;
import org.apereo.portal.portlet.dao.IPortletTypeDao;
import org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl;
import org.apereo.portal.portlet.om.IPortalCookie;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletType;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.provider.PersonImpl;
import org.apereo.portal.tenants.ITenant;
import org.apereo.portal.test.golden.DatabaseVendor;
import org.apereo.portal.test.golden.GoldenFiles;
import org.apereo.portal.test.golden.GoldenRenderer;
import org.apereo.portal.test.golden.JvmTimeZone;
import org.apereo.portal.test.golden.PersistenceUnits;
import org.apereo.portal.version.dao.VersionDao;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Query characterisation (MOD-D29 item 7): a fixed data set is written through the PortalDb DAOs,
 * then every query method of those DAOs is called and its result rendered (values, order where the
 * query defines one, collection and number classes, nulls and exceptions) into {@code
 * queries/portal-dao.txt}. Hibernate and Spring upgrades change HQL/JPQL translation, result types
 * (Long vs Integer, Double), null handling and ordering; this pins what 5.17 returns. Each query
 * runs in its own transaction, as the DAOs' callers do.
 *
 * <p>The raw-event and aggregation DAOs are characterised by {@code AggregationGoldenTest}, on the
 * rows the aggregation writes. {@code JpaClusterLockDao} has its own database tests (MOD-I82).
 */
public class DaoQueryGoldenTest {
    /**
     * Fails a stuck or very slow case with the stack of the test thread, before the time limit of
     * the CI job cancels the whole run without a report.
     */
    @Rule
    public final Timeout timeout =
            Timeout.builder()
                    .withTimeout(5, TimeUnit.MINUTES)
                    .withLookingForStuckThread(true)
                    .build();

    /** Values set to "now" or to random tokens by the code under test */
    private static final Set<String> MASKED =
            new HashSet<>(
                    Arrays.asList(
                            "PortletList.createdOn",
                            "PortletList.updatedOn",
                            "PortletList.id",
                            "PortletListItem.id",
                            "PortalCookieImpl.created",
                            "PortalCookieImpl.expires",
                            "PortalCookieImpl.value",
                            "PortletCookieImpl.expires",
                            "LocalAccountPersonImpl.lastPasswordChange"));

    private final List<String> lines = new ArrayList<>();
    private GoldenRenderer renderer;
    private TransactionTemplate transactions;

    @Test
    public void queriesReturnWhatTheyReturnedIn517() throws Exception {
        try (JvmTimeZone timeZone = JvmTimeZone.set("UTC");
                GenericXmlApplicationContext ctx =
                        new GenericXmlApplicationContext("classpath:goldenDaoTestContext.xml")) {
            final EntityManagerFactory emf =
                    ctx.getBean("PortalDbEntityManagerFactory", EntityManagerFactory.class);
            final DatabaseVendor vendor =
                    DatabaseVendor.of(ctx.getBean("PortalDb", javax.sql.DataSource.class));
            renderer = new GoldenRenderer(emf.getMetamodel(), MASKED);
            transactions =
                    new TransactionTemplate(
                            ctx.getBean("transactionManager", PlatformTransactionManager.class));
            PersistenceUnits.deleteAll(emf);
            try {
                portlets(ctx);
                messages(ctx);
                layouts(ctx);
                people(ctx);
                permissionsAndTenants(ctx);
                pags(ctx);
                portletLists(ctx);
                misc(ctx);
                final String golden = "queries/portal-dao.txt";
                GoldenFiles.verify(
                        golden,
                        lines,
                        vendor,
                        "JVM zone UTC",
                        QueryVendorDifferences.goldenLines(
                                vendor,
                                vendor == DatabaseVendor.HSQLDB
                                        ? Collections.<String>emptyList()
                                        : GoldenFiles.expectedLines(golden)));
            } finally {
                PersistenceUnits.deleteAll(emf);
            }
        }
    }

    private void portlets(GenericXmlApplicationContext ctx) {
        final IPortletTypeDao typeDao = ctx.getBean(IPortletTypeDao.class);
        final IPortletDefinitionDao definitionDao = ctx.getBean(IPortletDefinitionDao.class);
        final IPortletEntityDao entityDao = ctx.getBean(IPortletEntityDao.class);
        final IMarketplaceRatingDao ratingDao = ctx.getBean(IMarketplaceRatingDao.class);

        final IPortletType portlet =
                typeDao.createPortletType(
                        "Portlet", "/org/apereo/portal/portlets/CustomPortletType.cpd.xml");
        final IPortletType proxy =
                typeDao.createPortletType(
                        "Web Proxy", "/org/jasig/portlet/proxy/web-proxy.cpd.xml");
        typeDao.createPortletType("zz Last", "/last.cpd.xml");
        final String[][] definitions = {
            // fname, name, title, type
            {"weather", "Weather", "Campus Weather", "p"},
            {"calendar", "Campus Calendar", "Calendar", "p"},
            {"email-preview", "email preview", "Email Preview", "x"},
            {"unicode", "Zoë's 日本語 Portlet", "Übersicht", "p"},
            {"percent", "100%_Done", "Wildcards 100%_Done", "x"},
            {"trailing", "Trailing Space ", "Trailing Space ", "p"},
        };
        final List<IPortletDefinition> saved = new ArrayList<>();
        for (final String[] d : definitions) {
            final PortletDefinitionImpl definition =
                    new PortletDefinitionImpl(
                            "p".equals(d[3]) ? portlet : proxy,
                            d[0],
                            d[1],
                            d[2],
                            "/" + d[0],
                            d[0] + "-portlet",
                            false);
            definition.setDescription(d[0].startsWith("e") ? null : "About " + d[1]);
            saved.add(definitionDao.savePortletDefinition(definition));
        }
        final IPortletDefinition weather = saved.get(0);
        final IPortletDefinition calendar = saved.get(1);
        entityDao.createPortletEntity(weather.getPortletDefinitionId(), "n10", 1);
        entityDao.createPortletEntity(weather.getPortletDefinitionId(), "n11", 2);
        entityDao.createPortletEntity(calendar.getPortletDefinitionId(), "n10", 2);
        ratingDao.createOrUpdateRating(4, "alice", "Good", weather);
        ratingDao.createOrUpdateRating(5, "bob", null, weather);
        ratingDao.createOrUpdateRating(2, "carol", "", weather);
        ratingDao.createOrUpdateRating(3, "alice", "Fine", calendar);
        ratingDao.createOrUpdateRating(1, "alice", "Changed my mind", calendar);

        check("portletTypeDao.getPortletTypes()", typeDao::getPortletTypes);
        check(
                "portletTypeDao.getPortletType(\"Web Proxy\")",
                () -> typeDao.getPortletType("Web Proxy"));
        check(
                "portletTypeDao.getPortletType(\"web proxy\")",
                () -> typeDao.getPortletType("web proxy"));
        check("portletTypeDao.getPortletType(-1)", () -> typeDao.getPortletType(-1));

        check("portletDefinitionDao.getPortletDefinitions()", definitionDao::getPortletDefinitions);
        check(
                "portletDefinitionDao.getPortletDefinitionByFname(\"weather\")",
                () -> definitionDao.getPortletDefinitionByFname("weather"));
        check(
                "portletDefinitionDao.getPortletDefinitionByFname(\"nope\")",
                () -> definitionDao.getPortletDefinitionByFname("nope"));
        check(
                "portletDefinitionDao.getPortletDefinitionByName(\"Weather\")",
                () -> definitionDao.getPortletDefinitionByName("Weather"));
        check(
                "portletDefinitionDao.getPortletDefinitionByName(\"weather\")",
                () -> definitionDao.getPortletDefinitionByName("weather"));
        check(
                "portletDefinitionDao.getPortletDefinitionByName(\"Trailing Space\")",
                () -> definitionDao.getPortletDefinitionByName("Trailing Space"));
        check(
                "portletDefinitionDao.getPortletDefinitionByName(unicode)",
                () -> definitionDao.getPortletDefinitionByName("Zoë's 日本語 Portlet"));
        check(
                "portletDefinitionDao.getPortletDefinition(\"not-a-number\")",
                () -> definitionDao.getPortletDefinition("not-a-number"));
        check(
                "portletDefinitionDao.getPortletDefinition(weather id)",
                () ->
                        definitionDao.getPortletDefinition(
                                weather.getPortletDefinitionId().getStringId()));
        for (final String term :
                Arrays.asList("cal", "CAL", "Calendar", "%", "100%_", "über", "")) {
            check(
                    "portletDefinitionDao.searchForPortlets(\""
                            + GoldenRenderer.escape(term)
                            + "\", true)",
                    () -> definitionDao.searchForPortlets(term, true));
        }
        for (final String term : Arrays.asList("Calendar", "calendar", "Campus Weather")) {
            check(
                    "portletDefinitionDao.searchForPortlets(\"" + term + "\", false)",
                    () -> definitionDao.searchForPortlets(term, false));
        }

        check(
                "portletEntityDao.getPortletEntity(\"n10\", 2)",
                () -> entityDao.getPortletEntity("n10", 2));
        check(
                "portletEntityDao.getPortletEntity(\"n10\", 3)",
                () -> entityDao.getPortletEntity("n10", 3));
        check(
                "portletEntityDao.getPortletEntities(weather)",
                () -> entityDao.getPortletEntities(weather.getPortletDefinitionId()));
        check(
                "portletEntityDao.getPortletEntitiesForUser(2)",
                () -> entityDao.getPortletEntitiesForUser(2));
        check(
                "portletEntityDao.getPortletEntitiesForUser(9)",
                () -> entityDao.getPortletEntitiesForUser(9));

        check("marketplaceRatingDao.getAllRatings()", ratingDao::getAllRatings);
        check(
                "marketplaceRatingDao.getRatingsByFname(\"weather\")",
                () -> ratingDao.getRatingsByFname("weather"));
        check(
                "marketplaceRatingDao.getRating(\"alice\", calendar)",
                () -> ratingDao.getRating("alice", calendar));
        check(
                "marketplaceRatingDao.getRating(\"zed\", calendar)",
                () -> ratingDao.getRating("zed", calendar));
        // AVG(rating) and COUNT(...) into PortletDefinition.rating and usersRated
        run(ratingDao::aggregateMarketplaceRating);
        check(
                "after aggregateMarketplaceRating: weather",
                () -> rating(definitionDao.getPortletDefinitionByFname("weather")));
        check(
                "after aggregateMarketplaceRating: calendar",
                () -> rating(definitionDao.getPortletDefinitionByFname("calendar")));
        check(
                "after aggregateMarketplaceRating: unicode (no ratings)",
                () -> rating(definitionDao.getPortletDefinitionByFname("unicode")));
        run(
                () ->
                        ratingDao.clearRatingsForPortlet(
                                definitionDao.getPortletDefinitionByFname("weather")));
        check("after clearRatingsForPortlet(weather): getAllRatings()", ratingDao::getAllRatings);
    }

    private static String rating(IPortletDefinition definition) {
        final Double rating = definition.getRating();
        final Long usersRated = definition.getUsersRated();
        return "rating=" + value(rating) + ", usersRated=" + value(usersRated);
    }

    private void messages(GenericXmlApplicationContext ctx) {
        final IMessageDao dao = ctx.getBean(IMessageDao.class);
        dao.createMessage("greeting", Locale.ENGLISH, "Hello");
        dao.createMessage("greeting", Locale.US, "Howdy");
        dao.createMessage("greeting", Locale.FRENCH, "Bonjour");
        dao.createMessage("greeting", Locale.TRADITIONAL_CHINESE, "你好");
        dao.createMessage("farewell", Locale.ENGLISH, "Goodbye");
        // The unique key on (CODE, LOCALE) is case-sensitive on some databases only
        check(
                "messageDao.createMessage(\"Greeting\", en, ...) next to \"greeting\"",
                () -> dao.createMessage("Greeting", Locale.ENGLISH, "Upper-case code"));

        check(
                "messageDao.getMessage(\"greeting\", en_US)",
                () -> dao.getMessage("greeting", Locale.US));
        check(
                "messageDao.getMessage(\"greeting\", de)",
                () -> dao.getMessage("greeting", Locale.GERMAN));
        check(
                "messageDao.getMessage(\"GREETING\", en)",
                () -> dao.getMessage("GREETING", Locale.ENGLISH));
        // These two queries have an ORDER BY, and the DAO keeps it in a LinkedHashSet
        ordered(
                "messageDao.getMessagesByCode(\"greeting\")",
                () -> dao.getMessagesByCode("greeting"));
        ordered(
                "messageDao.getMessagesByLocale(en)",
                () -> dao.getMessagesByLocale(Locale.ENGLISH));
        // GROUP BY without ORDER BY: PostgreSQL returns the codes in another order
        check("messageDao.getCodes()", dao::getCodes);
    }

    private void layouts(GenericXmlApplicationContext ctx) throws ReflectiveOperationException {
        final IStylesheetDescriptorDao descriptors = ctx.getBean(IStylesheetDescriptorDao.class);
        final IStylesheetUserPreferencesDao preferences =
                ctx.getBean(IStylesheetUserPreferencesDao.class);
        final IProfileSelectionDao profiles = ctx.getBean(IProfileSelectionDao.class);
        final IFragmentDefinitionDao fragments = ctx.getBean(IFragmentDefinitionDao.class);

        final IStylesheetDescriptor respondr =
                descriptors.createStylesheetDescriptor(
                        "RespondrJS", "classpath:/layout/structure/columns.xsl");
        descriptors.createStylesheetDescriptor("Ziggy", "classpath:/z.xsl");
        // Created, changed and stored in one transaction, as the layout code does
        run(
                () -> {
                    final IStylesheetUserPreferences prefs =
                            preferences.createStylesheetUserPreferences(respondr, 7, 1);
                    prefs.setStylesheetParameter("skin", "dark");
                    prefs.setLayoutAttribute("u7l1s5", "width", "50%");
                    preferences.storeStylesheetUserPreferences(prefs);
                });
        preferences.createStylesheetUserPreferences(respondr, 8, 1);
        profiles.createOrUpdateProfileSelection("alice", "respondr");
        profiles.createOrUpdateProfileSelection("bob", "mobile");
        // An existing selection changes the way ProfileSelectionRegistry does it
        run(
                () -> {
                    final org.apereo.portal.layout.profile.IProfileSelection existing =
                            profiles.readProfileSelectionForUser("alice");
                    existing.setProfileFName("mobile");
                    profiles.createOrUpdateProfileSelection(existing);
                });
        for (final Object[] f :
                new Object[][] {
                    {"Welcome", "fragment-welcome", 80.0},
                    {"Academics", "fragment-academics", 20.5},
                    {"Admin", "admin-lo", 100.0}
                }) {
            final FragmentDefinition fragment =
                    fragment((String) f[0], (String) f[1], (Double) f[2]);
            run(() -> fragments.updateFragmentDefinition(fragment));
        }

        check(
                "stylesheetDescriptorDao.getStylesheetDescriptors()",
                descriptors::getStylesheetDescriptors);
        check(
                "stylesheetDescriptorDao.getStylesheetDescriptorByName(\"RespondrJS\")",
                () -> descriptors.getStylesheetDescriptorByName("RespondrJS"));
        check(
                "stylesheetDescriptorDao.getStylesheetDescriptorByName(\"respondrjs\")",
                () -> descriptors.getStylesheetDescriptorByName("respondrjs"));
        check(
                "stylesheetUserPreferencesDao.getStylesheetUserPreferences(respondr, 7, 1)",
                () -> preferences.getStylesheetUserPreferences(respondr, 7, 1));
        check(
                "stylesheetUserPreferencesDao.getStylesheetUserPreferencesForUser(8)",
                () -> preferences.getStylesheetUserPreferencesForUser(8));
        check(
                "stylesheetUserPreferencesDao.getStylesheetUserPreferences()",
                preferences::getStylesheetUserPreferences);
        check(
                "profileSelectionDao.readProfileSelectionForUser(\"alice\")",
                () -> profiles.readProfileSelectionForUser("alice"));
        check(
                "profileSelectionDao.readProfileSelectionForUser(\"nobody\")",
                () -> profiles.readProfileSelectionForUser("nobody"));
        check("fragmentDefinitionDao.getAllFragments()", fragments::getAllFragments);
        check(
                "fragmentDefinitionDao.getFragmentDefinition(\"Academics\")",
                () -> fragments.getFragmentDefinition("Academics"));
        check(
                "fragmentDefinitionDao.getFragmentDefinitionByOwner(\"admin-lo\")",
                () -> fragments.getFragmentDefinitionByOwner("admin-lo"));
    }

    /** FragmentDefinition is built from layout XML in the portal; set its columns directly */
    private static FragmentDefinition fragment(String name, String owner, double precedence)
            throws ReflectiveOperationException {
        final java.lang.reflect.Constructor<FragmentDefinition> constructor =
                FragmentDefinition.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        final FragmentDefinition fragment = constructor.newInstance(name);
        set(fragment, "ownerID", owner);
        set(fragment, "precedence", precedence);
        set(fragment, "description", "The " + name + " fragment");
        return fragment;
    }

    private static void set(Object target, String name, Object value)
            throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                final Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void people(GenericXmlApplicationContext ctx) {
        final ILocalAccountDao dao = ctx.getBean(ILocalAccountDao.class);
        run(
                () -> {
                    final ILocalAccountPerson alice = dao.createPerson("alice");
                    alice.setAttribute("givenName", "Alice");
                    alice.setAttribute("mail", "alice@example.edu", "ALICE@EXAMPLE.EDU");
                    alice.setAttribute("nickname", "");
                    dao.updateAccount(alice);
                    final ILocalAccountPerson bob = dao.createPerson("bob");
                    bob.setAttribute("givenName", "Bob");
                    bob.setAttribute("mail", "bob@example.edu");
                    dao.updateAccount(bob);
                    dao.createPerson("nobody");
                });

        check("localAccountDao.getPerson(\"alice\")", () -> dao.getPerson("alice"));
        check("localAccountDao.getPerson(\"ALICE\")", () -> dao.getPerson("ALICE"));
        check("localAccountDao.getAllAccounts()", dao::getAllAccounts);
        check("localAccountDao.getCurrentAttributeNames()", dao::getCurrentAttributeNames);
        check(
                "localAccountDao.getPeople(name=nobody)",
                () -> dao.getPeople(query("nobody", null, null)));
        check("localAccountDao.getPeople(name=bob)", () -> dao.getPeople(query("bob", null, null)));
        check(
                "localAccountDao.getPeople(mail~EXAMPLE)",
                () -> dao.getPeople(query(null, "mail", "EXAMPLE")));
        check(
                "localAccountDao.getPeople(givenName~ali)",
                () -> dao.getPeople(query(null, "givenName", "ali")));
        check("localAccountDao.getPeople(nothing)", () -> dao.getPeople(query(null, null, null)));
    }

    private static LocalAccountQuery query(String name, String attribute, String value) {
        final LocalAccountQuery query = new LocalAccountQuery();
        if (name != null) {
            query.setUserName(name);
        }
        if (attribute != null) {
            query.setAttribute(attribute, Collections.singletonList(value));
        }
        return query;
    }

    private void permissionsAndTenants(GenericXmlApplicationContext ctx) {
        final IPermissionOwnerDao owners = ctx.getBean(IPermissionOwnerDao.class);
        // ITenantDao is package-private: call it reflectively
        final Object tenants = ctx.getBean("tenantDao");
        run(
                () -> {
                    final IPermissionOwner portal =
                            owners.getOrCreatePermissionOwner("uPortal", "UP_PORTAL");
                    owners.getOrCreatePermissionOwner("Portlet Publishing", "UP_PORTLET_PUBLISH");
                    owners.getOrCreatePermissionActivity(
                            portal, "Subscribe", "SUBSCRIBE", "portletTargetProvider");
                    owners.getOrCreatePermissionActivity(
                            portal, "Manage", "MANAGE", "portletTargetProvider");
                });

        check("permissionOwnerDao.getAllPermissionOwners()", owners::getAllPermissionOwners);
        check(
                "permissionOwnerDao.getPermissionOwner(\"UP_PORTAL\")",
                () -> owners.getPermissionOwner("UP_PORTAL"));
        check(
                "permissionOwnerDao.getPermissionOwner(\"up_portal\")",
                () -> owners.getPermissionOwner("up_portal"));
        check(
                "permissionOwnerDao.getPermissionActivity(\"UP_PORTAL\", \"MANAGE\")",
                () -> owners.getPermissionActivity("UP_PORTAL", "MANAGE"));
        check(
                "permissionOwnerDao.getPermissionActivity(\"UP_PORTAL\", \"NONE\")",
                () -> owners.getPermissionActivity("UP_PORTAL", "NONE"));
        check(
                "permissionOwnerDao.getOrCreatePermissionOwner(\"uPortal\", \"UP_PORTAL\") again",
                () -> owners.getOrCreatePermissionOwner("uPortal", "UP_PORTAL"));

        for (final String[] t : new String[][] {{"Default", "default"}, {"Campus B", "campus-b"}}) {
            run(
                    () -> {
                        final ITenant tenant = (ITenant) invoke(tenants, "instantiate");
                        tenant.setName(t[0]);
                        tenant.setFname(t[1]);
                        tenant.setAttribute("adminContactEmail", t[1] + "@example.edu");
                        invoke(tenants, "createOrUpdateTenant", tenant);
                    });
        }
        check("tenantDao.getAllTenants()", () -> invoke(tenants, "getAllTenants"));
        check(
                "tenantDao.getTenantByName(\"Campus B\")",
                () -> invoke(tenants, "getTenantByName", "Campus B"));
        check(
                "tenantDao.getTenantByFName(\"default\")",
                () -> invoke(tenants, "getTenantByFName", "default"));
        check(
                "tenantDao.getTenantByFName(\"none\")",
                () -> invoke(tenants, "getTenantByFName", "none"));
    }

    /** Calls a public method of a bean whose type is not public */
    private static Object invoke(Object target, String name, Object... args) {
        for (final java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    }
                    throw new IllegalStateException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalArgumentException(target.getClass() + " has no method " + name);
    }

    private void pags(GenericXmlApplicationContext ctx) {
        final IPersonAttributesGroupDefinitionDao groups =
                ctx.getBean(IPersonAttributesGroupDefinitionDao.class);
        final IPersonAttributesGroupTestGroupDefinitionDao testGroups =
                ctx.getBean(IPersonAttributesGroupTestGroupDefinitionDao.class);
        final IPersonAttributesGroupTestDefinitionDao tests =
                ctx.getBean(IPersonAttributesGroupTestDefinitionDao.class);
        final IPersonAttributesGroupDefinition[] created = new IPersonAttributesGroupDefinition[2];
        run(
                () -> {
                    final IPersonAttributesGroupDefinition students =
                            groups.createPersonAttributesGroupDefinition(
                                    "Students", "All students");
                    final IPersonAttributesGroupDefinition parent =
                            groups.createPersonAttributesGroupDefinition(
                                    "PAGS Root", "Root of PAGS");
                    groups.createPersonAttributesGroupDefinition("Empty", null);
                    final IPersonAttributesGroupTestGroupDefinition testGroup =
                            testGroups.createPersonAttributesGroupTestGroupDefinition(students);
                    tests.createPersonAttributesGroupTestDefinition(
                            testGroup,
                            "eduPersonAffiliation",
                            "org.apereo.portal.groups.pags.testers.StringEqualsIgnoreCaseTester",
                            "student");
                    parent.setMembers(new HashSet<>(Collections.singletonList(students)));
                    groups.updatePersonAttributesGroupDefinition(parent);
                    created[0] = students;
                    created[1] = parent;
                });
        final IPersonAttributesGroupDefinition students = created[0];
        final IPersonAttributesGroupDefinition parent = created[1];

        check(
                "pagsGroupDefinitionDao.getPersonAttributesGroupDefinitions()",
                groups::getPersonAttributesGroupDefinitions);
        check(
                "pagsGroupDefinitionDao.getPersonAttributesGroupDefinitionByName(\"Students\")",
                () -> groups.getPersonAttributesGroupDefinitionByName("Students"));
        check(
                "pagsGroupDefinitionDao.getPersonAttributesGroupDefinitionByName(\"students\")",
                () -> groups.getPersonAttributesGroupDefinitionByName("students"));
        check(
                "pagsGroupDefinitionDao.getParentPersonAttributesGroupDefinitions(Students)",
                () -> groups.getParentPersonAttributesGroupDefinitions(students));
        check(
                "pagsGroupDefinitionDao.getParentPersonAttributesGroupDefinitions(PAGS Root)",
                () -> groups.getParentPersonAttributesGroupDefinitions(parent));
    }

    private void portletLists(GenericXmlApplicationContext ctx) {
        final IPortletListDao dao = ctx.getBean(IPortletListDao.class);
        final IPerson alice = person("alice");
        for (final String[] l :
                new String[][] {
                    {"alice", "Favorites", "weather,calendar"},
                    {"alice", "Empty", ""},
                    {"bob", "Bobs_list", "calendar"}
                }) {
            final PortletList list = new PortletList();
            list.setOwnerUsername(l[0]);
            list.setName(l[1]);
            final List<PortletListItem> items = new ArrayList<>();
            for (final String entity : l[2].isEmpty() ? new String[0] : l[2].split(",")) {
                items.add(new PortletListItem(entity));
            }
            list.clearAndSetItems(items);
            dao.createPortletList(list, l[0].equals("alice") ? alice : person(l[0]));
        }
        check("portletListDao.getPortletLists(\"alice\")", () -> dao.getPortletLists("alice"));
        check("portletListDao.getPortletLists()", dao::getPortletLists);
        check(
                "portletListDao.getPortletList(\"no-such-id\")",
                () -> dao.getPortletList("no-such-id"));
    }

    private static IPerson person(String userName) {
        final PersonImpl person = new PersonImpl();
        person.setUserName(userName);
        return person;
    }

    private void misc(GenericXmlApplicationContext ctx) {
        final VersionDao versions = ctx.getBean(VersionDao.class);
        final AuthDao auth = ctx.getBean(AuthDao.class);
        final IPortletCookieDao cookies = ctx.getBean(IPortletCookieDao.class);
        versions.setVersion("PortalDb", 5, 17, 10, null);
        versions.setVersion("Golden", 1, 2, 3, 4);
        check("versionDao.getVersion(\"PortalDb\")", () -> versions.getVersion("PortalDb"));
        check("versionDao.getVersion(\"Golden\")", () -> versions.getVersion("Golden"));
        check("versionDao.getVersion(\"none\")", () -> versions.getVersion("none"));

        final String token = auth.getAuthToken("golden-service");
        check(
                "authDao.getAuthToken(\"golden-service\") again is the same",
                () ->
                        token.equals(auth.getAuthToken("golden-service"))
                                + " (length "
                                + token.length()
                                + ")");

        final IPortalCookie[] created = new IPortalCookie[2];
        // In one transaction, as the portlet cookie service does
        run(
                () -> {
                    created[0] = cookies.createPortalCookie(3600);
                    created[1] = cookies.createPortalCookie(-3600);
                    final Cookie cookie = new Cookie("JSESSIONID", "abc");
                    cookie.setMaxAge(600);
                    cookie.setPath("/uPortal");
                    cookies.addOrUpdatePortletCookie(created[0], cookie);
                });
        final IPortalCookie live = created[0];
        final IPortalCookie expired = created[1];
        check(
                "portletCookieDao.getPortalCookie(live)",
                () -> cookies.getPortalCookie(live.getValue()));
        run(() -> cookies.purgeExpiredCookies(0));
        check("after purgeExpiredCookies: live", () -> cookies.getPortalCookie(live.getValue()));
        check(
                "after purgeExpiredCookies: expired",
                () -> cookies.getPortalCookie(expired.getValue()));
    }

    private void run(Runnable runnable) {
        transactions.execute(
                status -> {
                    runnable.run();
                    return null;
                });
    }

    private void check(String label, Callable<?> query) {
        check(label, false, query);
    }

    /** For queries that define an order (ORDER BY, or the DAO sorts) */
    private void ordered(String label, Callable<?> query) {
        check(label, true, query);
    }

    private void check(String label, boolean ordered, Callable<?> query) {
        List<String> result;
        try {
            result =
                    transactions.execute(
                            status -> {
                                try {
                                    return render(query.call(), ordered);
                                } catch (Exception e) {
                                    status.setRollbackOnly();
                                    return Collections.singletonList(
                                            "throws " + e.getClass().getName());
                                }
                            });
        } catch (RuntimeException e) {
            // Failed when the transaction committed (a constraint checked at flush)
            result = Collections.singletonList("throws on commit " + e.getClass().getName());
        }
        lines.add(label + " -> " + result.get(0));
        for (final String element : result.subList(1, result.size())) {
            lines.add(label + "   " + element);
        }
    }

    /** The first line describes the result; collections add a line per element */
    private List<String> render(Object result, boolean ordered) {
        final List<String> rendered = new ArrayList<>();
        if (result instanceof Collection) {
            final Collection<?> collection = (Collection<?>) result;
            rendered.add(
                    collection.getClass().getSimpleName()
                            + " of "
                            + collection.size()
                            + (ordered ? ", in order" : ""));
            final List<String> elements = new ArrayList<>();
            for (final Object element : collection) {
                elements.add(renderer.render(element));
            }
            if (!ordered) {
                Collections.sort(elements);
            }
            for (int i = 0; i < elements.size(); i++) {
                rendered.add((ordered ? "[" + i + "] " : "- ") + elements.get(i));
            }
        } else if (result instanceof Map) {
            rendered.add(result.getClass().getSimpleName() + " of " + ((Map<?, ?>) result).size());
            final List<String> entries = new ArrayList<>();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) result).entrySet()) {
                entries.add(
                        "- "
                                + renderer.render(entry.getKey())
                                + " = "
                                + renderer.render(entry.getValue()));
            }
            Collections.sort(entries);
            rendered.addAll(entries);
        } else if (result == null
                || result instanceof Number
                || result instanceof Boolean
                || result instanceof String) {
            rendered.add(value(result));
        } else {
            rendered.add(renderer.render(result));
        }
        return rendered;
    }

    /** A plain value with its class, so that Integer/Long/Double changes show */
    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return GoldenRenderer.renderValue(value)
                    + " ("
                    + value.getClass().getSimpleName()
                    + ")";
        }
        if (value instanceof String) {
            return GoldenRenderer.renderValue(value);
        }
        return String.valueOf(value);
    }
}
