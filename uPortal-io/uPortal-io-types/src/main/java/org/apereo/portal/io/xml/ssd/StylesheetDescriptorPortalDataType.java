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
package org.apereo.portal.io.xml.ssd;

import java.util.Collections;
import java.util.List;
import javax.xml.namespace.QName;
import org.apereo.portal.io.xml.AbstractPortalDataType;
import org.apereo.portal.io.xml.IExportAllPortalDataType;
import org.apereo.portal.io.xml.PortalDataKey;
import org.apereo.portal.layout.om.IStylesheetDescriptor;
import org.springframework.stereotype.Component;

/** Describes the {@link IStylesheetDescriptor} for import and export. */
@Component("stylesheetDescriptorPortalDataType")
public class StylesheetDescriptorPortalDataType extends AbstractPortalDataType
        implements IExportAllPortalDataType {

    public static final int ORDER = 30;

    public static final QName STYLESHEET_DESCRIPTOR_QNAME =
            new QName(
                    "https://source.jasig.org/schemas/uportal/io/stylesheet-descriptor",
                    "stylesheet-descriptor");

    public static final PortalDataKey IMPORT_40_DATA_KEY =
            new PortalDataKey(STYLESHEET_DESCRIPTOR_QNAME, null, "4.0");

    private static final List<PortalDataKey> PORTAL_DATA_KEYS =
            Collections.singletonList(IMPORT_40_DATA_KEY);

    public StylesheetDescriptorPortalDataType() {
        super(ORDER, STYLESHEET_DESCRIPTOR_QNAME);
    }

    @Override
    public List<PortalDataKey> getDataKeyImportOrder() {
        return PORTAL_DATA_KEYS;
    }

    @Override
    public String getTitleCode() {
        return "Stylesheet Descriptor";
    }

    @Override
    public String getDescriptionCode() {
        return "Stylesheet Descriptors published in the portal";
    }
}
