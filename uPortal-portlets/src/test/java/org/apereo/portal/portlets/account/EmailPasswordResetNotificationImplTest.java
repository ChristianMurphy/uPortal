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
package org.apereo.portal.portlets.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URL;
import java.util.Locale;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.apereo.portal.persondir.ILocalAccountPerson;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** The reset email's log lines name the account, never the link, which carries the token. */
public class EmailPasswordResetNotificationImplTest {

    private static final String TOKEN = "Tok3nInTheResetLink9";

    @Test
    public void logsNoResetToken() throws Exception {
        final JavaMailSenderImpl mailSender = mock(JavaMailSenderImpl.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        final MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("text");
        final ILocalAccountPerson account = mock(ILocalAccountPerson.class);
        when(account.getName()).thenReturn("rhea");
        when(account.getAttributeValue(ILocalAccountPerson.ATTR_MAIL))
                .thenReturn("rhea@example.org");

        final EmailPasswordResetNotificationImpl notification =
                new EmailPasswordResetNotificationImpl();
        notification.setMailSender(mailSender);
        notification.setMessageSource(messageSource);
        notification.setPortalEmailAddress("portal@example.org");

        final Logger logger =
                (Logger) LoggerFactory.getLogger(EmailPasswordResetNotificationImpl.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level level = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            notification.sendNotification(
                    new URL(
                            "https://portal.example.org/uPortal/p/login/max/render.uP"
                                    + "?pP_username=rhea&pP_loginToken="
                                    + TOKEN),
                    account,
                    Locale.US);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(level);
        }

        assertFalse(appender.list.isEmpty());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("rhea"));
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage(), event.getFormattedMessage().contains(TOKEN));
        }
    }
}
