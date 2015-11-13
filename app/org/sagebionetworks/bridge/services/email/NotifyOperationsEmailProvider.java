package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.charset.StandardCharsets;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class NotifyOperationsEmailProvider implements MimeTypeEmailProvider {

    private static final String SYSOPS_EMAIL = BridgeConfigFactory.getConfig().getProperty("sysops.email");
    
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
    public MimeTypeEmail getEmail(String defaultSender) throws MessagingException {
        String sender = (defaultSender != null) ? defaultSender : SYSOPS_EMAIL;
        
        MimeBodyPart body = new MimeBodyPart();
        body.setText(message, StandardCharsets.UTF_8.name(), "text/plain");
        
        return new MimeTypeEmailBuilder()
                .withSender(sender)
                .withRecipient(SYSOPS_EMAIL)
                .withSubject(subject)
                .withMessageParts(body)
                .build();
    }

}
