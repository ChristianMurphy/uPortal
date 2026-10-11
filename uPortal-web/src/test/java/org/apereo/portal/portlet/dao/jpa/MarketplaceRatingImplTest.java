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
package org.apereo.portal.portlet.dao.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** A review is trimmed and cut to 160 characters without splitting one (MOD-I276) */
public class MarketplaceRatingImplTest {

    private static String reviewOf(String review) {
        final MarketplaceRatingImpl rating = new MarketplaceRatingImpl();
        rating.setReview(review);
        return rating.getReview();
    }

    private static String repeat(String s, int times) {
        return new String(new char[times]).replace("\0", s);
    }

    @Test
    public void trimsAReviewWithSpacesAroundIt() {
        assertEquals("Great portlet", reviewOf("   Great portlet   "));
        assertEquals("", reviewOf("   "));
        assertNull(reviewOf(null));
    }

    @Test
    public void cutsALongReviewAt160Characters() {
        assertEquals(repeat("x", 160), reviewOf(repeat("x", 200)));
        assertEquals(repeat("x", 160), reviewOf("  " + repeat("x", 170) + "  "));
    }

    @Test
    public void neverSplitsACharacterOutsideTheBasicMultilingualPlane() {
        // 161 emoji are 322 UTF-16 chars; the cut keeps 160 whole ones
        assertEquals(repeat("😀", 160), reviewOf(repeat("😀", 161)));
        assertEquals("Zoë 😀", reviewOf("Zoë 😀"));
    }
}
