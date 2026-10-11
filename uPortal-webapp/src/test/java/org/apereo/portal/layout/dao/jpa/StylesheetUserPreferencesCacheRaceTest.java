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
package org.apereo.portal.layout.dao.jpa;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import javax.persistence.EntityManagerFactory;
import org.apereo.portal.layout.dao.IStylesheetDescriptorDao;
import org.apereo.portal.layout.dao.IStylesheetUserPreferencesDao;
import org.apereo.portal.layout.om.IStylesheetDescriptor;
import org.apereo.portal.layout.om.IStylesheetUserPreferences;
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
 * A request that loads a user's stylesheet preferences while that user saves a layout change must
 * not leave the old stylesheet parameters or layout attributes in the second-level cache
 * (MOD-I312). A stale entry undoes the change and fails the user's next save with an
 * optimistic-lock error.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaPortalCacheRaceTestContext.xml")
public class StylesheetUserPreferencesCacheRaceTest extends BasePortalJpaDaoTest {

    private static final int USER_ID = 4231;
    private static final int PROFILE_ID = 1;

    @Autowired private IStylesheetDescriptorDao descriptorDao;
    @Autowired private IStylesheetUserPreferencesDao preferencesDao;

    private EntityManagerFactory emf;
    private long preferencesId;

    @Before
    public void createPreferences() {
        emf = getEntityManager().getEntityManagerFactory();
        preferencesId =
                executeInTransaction(
                        () -> {
                            final IStylesheetDescriptor descriptor =
                                    descriptorDao.createStylesheetDescriptor(
                                            "race-descriptor", "classpath:/race.xsl");
                            final IStylesheetUserPreferences preferences =
                                    preferencesDao.createStylesheetUserPreferences(
                                            descriptor, USER_ID, PROFILE_ID);
                            preferences.setStylesheetParameter("userName", "staff");
                            preferences.setStylesheetParameter("skin", "classic");
                            preferences.setLayoutAttribute("n1", "width", "60%");
                            preferencesDao.storeStylesheetUserPreferences(preferences);
                            return preferences.getId();
                        });
        CacheRace.evictAll(emf);
    }

    /**
     * Runs before the base class deletes every entity: a stale cached preferences row would fail
     * that delete with an optimistic-lock error and leave rows for the next test class.
     */
    @After
    public void evictCache() {
        CacheRace.evictAll(emf);
    }

    private IStylesheetUserPreferences preferences() {
        return preferencesDao.getStylesheetUserPreferences(preferencesId);
    }

    /** What a layout render reads: the stylesheet parameters and the node attributes. */
    private List<String> view() {
        return execute(
                () -> {
                    final IStylesheetUserPreferences preferences = preferences();
                    return Arrays.asList(
                            "userName=" + preferences.getStylesheetParameter("userName"),
                            "skin=" + preferences.getStylesheetParameter("skin"),
                            "n1.width=" + preferences.getLayoutAttribute("n1", "width"),
                            "n1.align=" + preferences.getLayoutAttribute("n1", "align"),
                            "n2.width=" + preferences.getLayoutAttribute("n2", "width"));
                });
    }

    @Test
    public void aLayoutSaveDuringALoadTakesEffect() throws Exception {
        CacheRace.race(
                emf,
                StylesheetUserPreferencesImpl.class,
                this::view,
                () ->
                        executeInTransaction(
                                () -> {
                                    final IStylesheetUserPreferences preferences = preferences();
                                    preferences.setStylesheetParameter("userName", "faculty");
                                    preferences.setStylesheetParameter("skin", "modern");
                                    preferences.setLayoutAttribute("n1", "width", "40%");
                                    preferences.setLayoutAttribute("n1", "align", "center");
                                    preferences.setLayoutAttribute("n2", "width", "20%");
                                    preferencesDao.storeStylesheetUserPreferences(preferences);
                                    return null;
                                }));
        assertEquals(
                "the preferences after the save",
                Arrays.asList(
                        "userName=faculty",
                        "skin=modern",
                        "n1.width=40%",
                        "n1.align=center",
                        "n2.width=20%"),
                view());
        // The user's next save must match the row's version
        executeInTransaction(
                () -> {
                    final IStylesheetUserPreferences preferences = preferences();
                    preferences.setStylesheetParameter("userName", "visitor");
                    preferencesDao.storeStylesheetUserPreferences(preferences);
                    return null;
                });
        assertEquals(
                "the parameter after the next save",
                "visitor",
                execute(() -> preferences().getStylesheetParameter("userName")));
    }
}
