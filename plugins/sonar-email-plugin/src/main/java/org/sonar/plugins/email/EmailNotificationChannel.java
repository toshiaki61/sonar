/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2011 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.email;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.database.model.User;
import org.sonar.api.notifications.Notification;
import org.sonar.api.notifications.NotificationChannel;
import org.sonar.api.security.UserFinder;
import org.sonar.plugins.email.api.EmailMessage;
import org.sonar.plugins.email.api.EmailTemplate;

/**
 * References:
 * <ul>
 * <li><a href="http://tools.ietf.org/html/rfc4021">Registration of Mail and MIME Header Fields</a></li>
 * <li><a href="http://tools.ietf.org/html/rfc2919">List-Id: A Structured Field and Namespace for the Identification of Mailing Lists</a></li>
 * <li><a href="https://github.com/blog/798-threaded-email-notifications">GitHub: Threaded Email Notifications</a></li>
 * </ul>
 * 
 * @since 2.10
 */
public class EmailNotificationChannel extends NotificationChannel {

  private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationChannel.class);

  /**
   * @see org.apache.commons.mail.Email#setSocketConnectionTimeout(int)
   * @see org.apache.commons.mail.Email#setSocketTimeout(int)
   */
  private static final int SOCKET_TIMEOUT = 30000;

  /**
   * Email Header Field: "List-ID".
   * Value of this field should contain mailing list identifier as specified in <a href="http://tools.ietf.org/html/rfc2919">RFC 2919</a>.
   */
  private static String LIST_ID_HEADER = "List-ID";

  /**
   * Email Header Field: "List-Archive".
   * Value of this field should contain URL of mailing list archive as specified in <a href="http://tools.ietf.org/html/rfc2369">RFC 2369</a>.
   */
  private static String LIST_ARCHIVE_HEADER = "List-Archive";

  /**
   * Email Header Field: "In-Reply-To".
   * Value of this field should contain related message identifier as specified in <a href="http://tools.ietf.org/html/rfc2822">RFC 2822</a>.
   */
  private static String IN_REPLY_TO_HEADER = "In-Reply-To";

  /**
   * Email Header Field: "References".
   * Value of this field should contain related message identifier as specified in <a href="http://tools.ietf.org/html/rfc2822">RFC 2822</a>
   */
  private static String REFERENCES_HEADER = "References";

  private static final String FROM_NAME_DEFAULT = "Sonar";
  private static final String SUBJECT_DEFAULT = "Notification";

  private EmailConfiguration configuration;
  private EmailTemplate[] templates;
  private UserFinder userFinder;

  public EmailNotificationChannel(EmailConfiguration configuration, EmailTemplate[] templates, UserFinder userFinder) {
    this.configuration = configuration;
    this.templates = templates;
    this.userFinder = userFinder;
  }

  @Override
  public void deliver(Notification notification, String username) {
    User user = userFinder.findByLogin(username);
    if (StringUtils.isBlank(user.getEmail())) {
      LOG.warn("Email not defined for user: " + username);
      return;
    }
    EmailMessage emailMessage = format(notification, username);
    if (emailMessage != null) {
      emailMessage.setTo(user.getEmail());
      deliver(emailMessage);
    }
  }

  private EmailMessage format(Notification notification, String username) {
    for (EmailTemplate template : templates) {
      EmailMessage email = template.format(notification);
      if (email != null) {
        return email;
      }
    }
    LOG.warn("Email template not found for notification: {}", notification);
    return null;
  }

  /**
   * Visibility has been relaxed for tests.
   */
  void deliver(EmailMessage emailMessage) {
    if (StringUtils.isBlank(configuration.getSmtpHost())) {
      LOG.warn("SMTP host was not configured - email will not be sent");
      return;
    }
    try {
      send(emailMessage);
    } catch (EmailException e) {
      LOG.error("Unable to send email", e);
    }
  }

  private void send(EmailMessage emailMessage) throws EmailException {
    LOG.info("Sending email: {}", emailMessage);
    String host = null;
    try {
      host = new URL(configuration.getServerBaseURL()).getHost();
    } catch (MalformedURLException e) {
      // ignore
    }

    SimpleEmail email = new SimpleEmail();
    if (StringUtils.isNotBlank(host)) {
      /*
       * Set headers for proper threading: GMail will not group messages, even if they have same subject, but don't have "In-Reply-To" and
       * "References" headers. TODO investigate threading in other clients like KMail, Thunderbird, Outlook
       */
      if (StringUtils.isNotEmpty(emailMessage.getMessageId())) {
        String messageId = "<" + emailMessage.getMessageId() + "@" + host + ">";
        email.addHeader(IN_REPLY_TO_HEADER, messageId);
        email.addHeader(REFERENCES_HEADER, messageId);
      }
      // Set headers for proper filtering
      email.addHeader(LIST_ID_HEADER, "Sonar <sonar." + host + ">");
      email.addHeader(LIST_ARCHIVE_HEADER, configuration.getServerBaseURL());
    }
    // Set general information
    email.setFrom(configuration.getFrom(), StringUtils.defaultIfBlank(emailMessage.getFrom(), FROM_NAME_DEFAULT));
    email.addTo(emailMessage.getTo(), " ");
    String subject = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(configuration.getPrefix()) + " ", "")
        + StringUtils.defaultString(emailMessage.getSubject(), SUBJECT_DEFAULT);
    email.setSubject(subject);
    email.setMsg(emailMessage.getMessage());
    // Send
    email.setHostName(configuration.getSmtpHost());
    email.setSmtpPort(Integer.parseInt(configuration.getSmtpPort()));
    email.setTLS(configuration.isUseTLS());
    if (StringUtils.isNotBlank(configuration.getSmtpUsername()) || StringUtils.isNotBlank(configuration.getSmtpPassword())) {
      email.setAuthentication(configuration.getSmtpUsername(), configuration.getSmtpPassword());
    }
    email.setSocketConnectionTimeout(SOCKET_TIMEOUT);
    email.setSocketTimeout(SOCKET_TIMEOUT);
    email.send();
  }

  /**
   * Send test email. This method called from Ruby.
   * 
   * @throws EmailException when unable to send
   */
  public void sendTestEmail(String toAddress, String subject, String message) throws EmailException {
    EmailMessage emailMessage = new EmailMessage();
    emailMessage.setTo(toAddress);
    emailMessage.setSubject(subject);
    emailMessage.setMessage(message);
    send(emailMessage);
  }

}