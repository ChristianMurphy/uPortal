package org.apereo.portal.io.xml.portlet;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.List;
import javax.xml.datatype.DatatypeFactory;
import org.apereo.portal.AuthorizationException;
import org.apereo.portal.IUserIdentityStore;
import org.apereo.portal.portlet.dao.jpa.PortletDefinitionImpl;
import org.apereo.portal.portlet.dao.jpa.PortletLifecycleEntryImpl;
import org.apereo.portal.portlet.dao.jpa.PortletTypeImpl;
import org.apereo.portal.portlet.om.IPortletDefinition;
import org.apereo.portal.portlet.om.IPortletLifecycleEntry;
import org.apereo.portal.portlet.om.IPortletType;
import org.apereo.portal.portlet.om.PortletLifecycleState;
import org.apereo.portal.security.IPerson;
import org.apereo.portal.security.provider.BrokenSecurityContext;
import org.apereo.portal.security.provider.PersonImpl;
import org.junit.Test;

public class ExternalPortletDefinitionUnmarshallerTest {

    private static final long MILLIS_IN_ONE_DAY =
            1000L // Millis
                    * 60L // Seconds
                    * 60L // Minutes
                    * 24L; // Hours

    @Test
    public void testUnmarshallLifecycle() {

        final long currentTimeMillis = System.currentTimeMillis();

        final String createdUser = "createdUser";
        final String approvedUser = "approvedUser";
        final String publishedUser = "publishedUser";
        final String expiredUser = "expiredUser";
        final String maintenanceUser = "maintenanceUser";

        final Calendar createdCalendar =
                getCalendarForMillis(currentTimeMillis - (5L * MILLIS_IN_ONE_DAY));
        final Calendar approvedCalendar =
                getCalendarForMillis(currentTimeMillis - (4L * MILLIS_IN_ONE_DAY));
        final Calendar publishedCalendar =
                getCalendarForMillis(currentTimeMillis - (3L * MILLIS_IN_ONE_DAY));
        final Calendar expiredCalendar =
                getCalendarForMillis(currentTimeMillis + MILLIS_IN_ONE_DAY);
        final Calendar maintenanceCalendar =
                getCalendarForMillis(currentTimeMillis - (2L * MILLIS_IN_ONE_DAY));

        // Created 5 days in the past
        final LifecycleEntry created = new LifecycleEntry();
        created.setName("CREATED");
        created.setUser(createdUser);
        created.setValue(createdCalendar);

        // Approved 4 days in the past
        final LifecycleEntry approved = new LifecycleEntry();
        approved.setName("APPROVED");
        approved.setUser(approvedUser);
        approved.setValue(approvedCalendar);

        // Published 3 days in the past
        final LifecycleEntry published = new LifecycleEntry();
        published.setName("PUBLISHED");
        published.setUser(publishedUser);
        published.setValue(publishedCalendar);

        // Expired 1 day in the future
        final LifecycleEntry expired = new LifecycleEntry();
        expired.setName("EXPIRED");
        expired.setUser(expiredUser);
        expired.setValue(expiredCalendar);

        // Maintenance mode 2 days in the past
        final LifecycleEntry maintenance = new LifecycleEntry();
        maintenance.setName("MAINTENANCE");
        maintenance.setUser(maintenanceUser);
        maintenance.setValue(maintenanceCalendar);

        final Lifecycle lifecycle1 = new Lifecycle();
        lifecycle1.getEntries().add(created);

        final Lifecycle lifecycle2 = new Lifecycle();
        lifecycle2.getEntries().add(created);
        lifecycle2.getEntries().add(approved);

        final Lifecycle lifecycle3 = new Lifecycle();
        lifecycle3.getEntries().add(published);
        lifecycle3.getEntries().add(expired);

        final Lifecycle lifecycle4 = new Lifecycle();
        lifecycle4.getEntries().add(created);
        lifecycle4.getEntries().add(approved);
        lifecycle4.getEntries().add(published);
        lifecycle4.getEntries().add(maintenance);
        lifecycle4.getEntries().add(expired);

        final Lifecycle[] lifecyclesToTest =
                new Lifecycle[] {lifecycle1, lifecycle2, lifecycle3, lifecycle4};

        final IPortletLifecycleEntry[][] expectedPortletLifecycles =
                new IPortletLifecycleEntry[][] {
                    // 1
                    new IPortletLifecycleEntry[] {
                        new PortletLifecycleEntryImpl(
                                createdUser.hashCode(),
                                PortletLifecycleState.CREATED,
                                createdCalendar.getTime())
                    },
                    // 2
                    new IPortletLifecycleEntry[] {
                        new PortletLifecycleEntryImpl(
                                createdUser.hashCode(),
                                PortletLifecycleState.CREATED,
                                createdCalendar.getTime()),
                        new PortletLifecycleEntryImpl(
                                approvedUser.hashCode(),
                                PortletLifecycleState.APPROVED,
                                approvedCalendar.getTime())
                    },
                    // 3
                    new IPortletLifecycleEntry[] {
                        new PortletLifecycleEntryImpl(
                                publishedUser.hashCode(),
                                PortletLifecycleState.PUBLISHED,
                                publishedCalendar.getTime()),
                        new PortletLifecycleEntryImpl(
                                expiredUser.hashCode(),
                                PortletLifecycleState.EXPIRED,
                                expiredCalendar.getTime())
                    },
                    // 4
                    new IPortletLifecycleEntry[] {
                        new PortletLifecycleEntryImpl(
                                createdUser.hashCode(),
                                PortletLifecycleState.CREATED,
                                createdCalendar.getTime()),
                        new PortletLifecycleEntryImpl(
                                approvedUser.hashCode(),
                                PortletLifecycleState.APPROVED,
                                approvedCalendar.getTime()),
                        new PortletLifecycleEntryImpl(
                                publishedUser.hashCode(),
                                PortletLifecycleState.PUBLISHED,
                                publishedCalendar.getTime()),
                        new PortletLifecycleEntryImpl(
                                maintenanceUser.hashCode(),
                                PortletLifecycleState.MAINTENANCE,
                                maintenanceCalendar.getTime()),
                        new PortletLifecycleEntryImpl(
                                expiredUser.hashCode(),
                                PortletLifecycleState.EXPIRED,
                                expiredCalendar.getTime())
                    }
                };

        final ExternalPortletDefinitionUnmarshaller unmarshaller =
                new ExternalPortletDefinitionUnmarshaller();
        unmarshaller.setUserIdentityStore(new MockUserIdentityStore());

        final IPortletType portletType =
                new PortletTypeImpl("FakePortletType", "http://not/a/real/uri");
        for (int i = 0; i < lifecyclesToTest.length; i++) {
            final Lifecycle lifecycle = lifecyclesToTest[i];
            final IPortletDefinition pDef =
                    new PortletDefinitionImpl(
                            portletType,
                            "fake-portlet",
                            "Fake Portlet",
                            "Fake Portlet",
                            "FakePortletApp",
                            "fake-portlet-def",
                            false);
            unmarshaller.unmarshallLifecycle(lifecycle, pDef);
            final IPortletLifecycleEntry[] expectedLifecycle = expectedPortletLifecycles[i];

            for (IPortletLifecycleEntry y : pDef.getLifecycle()) {
                System.out.println(" ## ");
                System.out.println(" ## y.getLifecycleState()=" + y.getLifecycleState());
            }

            assertArrayEquals(pDef.getLifecycle().toArray(), expectedLifecycle);
        }
    }

    /**
     * The 4.3 upgrade transform gives a MAINTENANCE entry that had no date the placeholder {@link
     * ExternalPortletDefinitionUnmarshaller#USE_CURRENT_DATETIME_SIGNAL_DATE}, and the import must
     * replace it with the current time (MOD-I101). Kept as 1999, the entry sorts before every other
     * entry, so the portlet is no longer in maintenance after the import.
     */
    @Test
    public void testUnmarshallLifecycleReplacesCurrentDatetimeSignal() throws Exception {
        // Parse the placeholder the way JAXB parses an xs:dateTime in an imported document
        final Calendar signal =
                DatatypeFactory.newInstance()
                        .newXMLGregorianCalendar(
                                ExternalPortletDefinitionUnmarshaller
                                        .USE_CURRENT_DATETIME_SIGNAL_DATE)
                        .toGregorianCalendar();

        final LifecycleEntry published = new LifecycleEntry();
        published.setName("PUBLISHED");
        published.setUser("publishedUser");
        published.setValue(
                getCalendarForMillis(System.currentTimeMillis() - (3L * MILLIS_IN_ONE_DAY)));

        final LifecycleEntry maintenance = new LifecycleEntry();
        maintenance.setName("MAINTENANCE");
        maintenance.setUser("system");
        maintenance.setValue(signal);

        final Lifecycle lifecycle = new Lifecycle();
        lifecycle.getEntries().add(published);
        lifecycle.getEntries().add(maintenance);

        final ExternalPortletDefinitionUnmarshaller unmarshaller =
                new ExternalPortletDefinitionUnmarshaller();
        unmarshaller.setUserIdentityStore(new MockUserIdentityStore());
        final IPortletDefinition pDef =
                new PortletDefinitionImpl(
                        new PortletTypeImpl("FakePortletType", "http://not/a/real/uri"),
                        "fake-portlet",
                        "Fake Portlet",
                        "Fake Portlet",
                        "FakePortletApp",
                        "fake-portlet-def",
                        false);

        final long before = System.currentTimeMillis();
        unmarshaller.unmarshallLifecycle(lifecycle, pDef);
        final long after = System.currentTimeMillis();

        final List<IPortletLifecycleEntry> entries = pDef.getLifecycle();
        assertEquals(2, entries.size());
        final IPortletLifecycleEntry maintenanceEntry =
                entries.stream()
                        .filter(e -> e.getLifecycleState() == PortletLifecycleState.MAINTENANCE)
                        .findFirst()
                        .orElseThrow(AssertionError::new);
        final long maintenanceMillis = maintenanceEntry.getDate().getTime();
        assertTrue(
                "MAINTENANCE should get the import time, but got " + maintenanceEntry.getDate(),
                maintenanceMillis >= before && maintenanceMillis <= after);
        assertSame(maintenanceEntry, entries.get(1));

        // getLifecycleState() only counts entries strictly before now
        while (System.currentTimeMillis() <= after) {
            Thread.sleep(1L);
        }
        assertEquals(PortletLifecycleState.MAINTENANCE, pDef.getLifecycleState());
    }

    private Calendar getCalendarForMillis(long millis) {
        final Calendar result = Calendar.getInstance();
        result.setTimeInMillis(millis);
        return result;
    }

    private static final class MockUserIdentityStore implements IUserIdentityStore {
        @Override
        public int getPortalUID(IPerson person) throws AuthorizationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPortalUID(IPerson person, boolean createPortalData)
                throws AuthorizationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public IPerson getPerson(String userName, boolean createPortalData)
                throws AuthorizationException {
            final IPerson result = new PersonImpl();
            result.setUserName(userName);
            result.setID(userName.hashCode());
            result.setSecurityContext(new BrokenSecurityContext());
            return result;
        }

        @Override
        public void removePortalUID(String userName) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removePortalUID(int uPortalUID) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPortalUserName(int uPortalUID) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Integer getPortalUserId(String userName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean validateUsername(String username) {
            throw new UnsupportedOperationException();
        }
    }
}
