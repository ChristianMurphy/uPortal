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
package org.apereo.portal.persondir.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.persondir.ILocalAccountDao;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.apereo.portal.security.IPortalPasswordService;
import org.apereo.portal.security.PortalPasswordServiceImpl;
import org.apereo.portal.test.BasePortalJpaDaoTest;
import org.apereo.portal.test.CacheRace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * A login that loaded a local account before a password change, an attribute change or a delete
 * committed must not leave the old account in the second-level cache (MOD-I310). Each test loads
 * the account the way SimpleSecurityContext does (by user name, then the password check), paused
 * between its read and its cache put while the change commits, and then logs in again.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalCacheRaceTestContext.xml")
public class LocalAccountCacheRaceTest extends BasePortalJpaDaoTest {

    private static final String USER = "race-user";

    @Autowired private ILocalAccountDao localAccountDao;

    private final IPortalPasswordService passwords = new PortalPasswordServiceImpl();

    private EntityManagerFactory emf;

    @Before
    public void createAccount() {
        emf = getEntityManager().getEntityManagerFactory();
        executeInTransaction(
                () -> {
                    final ILocalAccountPerson account = localAccountDao.createPerson(USER);
                    account.setPassword(passwords.encryptPassword("old password"));
                    account.setAttribute("affiliation", "staff");
                    return localAccountDao.updateAccount(account);
                });
        // The login has to read the rows, as after a restart or an eviction
        CacheRace.evictAll(emf);
    }

    /**
     * Runs before the base class deletes every entity: a stale cached account would fail that
     * delete with an optimistic-lock error and leave rows for the next test class.
     */
    @After
    public void evictCache() {
        CacheRace.evictAll(emf);
    }

    /** What SimpleSecurityContext checks: the account by name, then its password. */
    private boolean login(String password) {
        return execute(
                () -> {
                    final ILocalAccountPerson account = localAccountDao.getPerson(USER);
                    return account != null
                            && passwords.validatePassword(password, account.getPassword());
                });
    }

    /** Which password an account holds, as the password check sees it. */
    private String password(ILocalAccountPerson account) {
        if (account == null) {
            return "no account";
        }
        if (passwords.validatePassword("new password", account.getPassword())) {
            return "new";
        }
        return passwords.validatePassword("old password", account.getPassword()) ? "old" : "none";
    }

    /**
     * The account as a login sees it (by name) and as the account screens and the password
     * self-service see it (by id, AccountPasswordFormValidator and UserAccountHelper).
     */
    private List<String> view(long id) {
        // The first lookup by name after the change runs the query and caches its result. The
        // logins after it use that result and take the account from the entity cache.
        execute(() -> localAccountDao.getPerson(USER));
        final boolean oldPassword = login("old password");
        final boolean newPassword = login("new password");
        final Object attributes =
                execute(() -> new TreeMap<>(localAccountDao.getPerson(USER).getAttributes()));
        final String byId = execute(() -> password(localAccountDao.getPerson(id)));
        final Object attributesById =
                execute(() -> new TreeMap<>(localAccountDao.getPerson(id).getAttributes()));
        return Arrays.asList(
                "login with old password=" + oldPassword,
                "login with new password=" + newPassword,
                "attributes=" + attributes,
                "password by id=" + byId,
                "attributes by id=" + attributesById);
    }

    private long id() {
        return execute(() -> localAccountDao.getPerson(USER).getId());
    }

    private void change(Consumer<ILocalAccountPerson> change) {
        executeInTransaction(
                () -> {
                    final ILocalAccountPerson account = localAccountDao.getPerson(USER);
                    change.accept(account);
                    return localAccountDao.updateAccount(account);
                });
    }

    @Test
    public void aPasswordChangeDuringALoginTakesEffect() throws Exception {
        final long id = id();
        CacheRace.evictAll(emf);
        CacheRace.race(
                emf,
                LocalAccountPersonImpl.class,
                () -> login("old password"),
                () ->
                        change(
                                account -> {
                                    account.setPassword(passwords.encryptPassword("new password"));
                                    account.setAttribute("affiliation", "former staff");
                                    account.setAttribute("department", "added");
                                }));
        assertEquals(
                "the account after the change",
                Arrays.asList(
                        "login with old password=false",
                        "login with new password=true",
                        "attributes={affiliation=[former staff], department=[added]}",
                        "password by id=new",
                        "attributes by id={affiliation=[former staff], department=[added]}"),
                view(id));
    }

    @Test
    public void removingThePasswordDuringALoginDisablesTheAccount() throws Exception {
        final long id = id();
        CacheRace.evictAll(emf);
        CacheRace.race(
                emf,
                LocalAccountPersonImpl.class,
                () -> login("old password"),
                () -> change(account -> account.setPassword(null)));
        execute(() -> localAccountDao.getPerson(USER));
        assertEquals("a login with the old password", false, login("old password"));
        assertEquals(
                "the password by id",
                "none",
                execute(() -> password(localAccountDao.getPerson(id))));
    }

    @Test
    public void aDeletedAccountStaysDeleted() throws Exception {
        final long id = id();
        CacheRace.evictAll(emf);
        CacheRace.race(
                emf,
                LocalAccountPersonImpl.class,
                () -> login("old password"),
                () ->
                        executeInTransaction(
                                () -> {
                                    localAccountDao.deleteAccount(localAccountDao.getPerson(USER));
                                    return null;
                                }));
        execute(() -> localAccountDao.getPerson(USER));
        assertEquals("a login after the delete", false, login("old password"));
        assertNull("the account by name", execute(() -> localAccountDao.getPerson(USER)));
        assertNull("the account by id", execute(() -> localAccountDao.getPerson(id)));
    }
}
