package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.google.common.collect.Lists;

/**
 * This is a simple and generic provider to send a notification via email to our sysops/devops email address. The first
 * use of this is to verify that a participant roster has been completed and sent to the consent notification email for
 * a study, because if this fails, we get no information about it one way or another.
 */
public class NotifyOperationsEmailProvider implements MimeTypeEmailProvider {

    private static final String SYSOPS_EMAIL = BridgeConfigFactory.getConfig().getProperty("sysops.email");
    private static final String MIME_TYPE_TEXT = "text/plain";
    
    private final String subject;
    private final String message;
    
    public NotifyOperationsEmailProvider(String subject, String message) {
        checkNotNull(subject);
        checkNotNull(message);
        this.subject = subject;
        this.message = message;
    }
    
    String getSubject() {
        return subject;
    }
    
    String getMessage() {
        return message;
    }
    
    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        MimeBodyPart body = new MimeBodyPart();
        body.setContent(message, MIME_TYPE_TEXT);
        
        return new MimeTypeEmailBuilder()
                .withSender(SYSOPS_EMAIL)
                .withRecipients(Lists.newArrayList(SYSOPS_EMAIL))
                .withSubject(subject)
                .withMessageParts(body)
                .build();
    }

}
