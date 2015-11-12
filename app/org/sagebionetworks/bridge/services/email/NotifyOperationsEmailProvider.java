package org.sagebionetworks.bridge.services.email;

import java.nio.charset.StandardCharsets;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class NotifyOperationsEmailProvider implements MimeTypeEmailProvider {

    private static final String SYSOPS_EMAIL = "sysops.email";
    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();
    
    private String subject;
    private String message;
    
    public NotifyOperationsEmailProvider(String subject, String message) {
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
        String sender = (defaultSender != null) ? defaultSender : CONFIG.getProperty(SYSOPS_EMAIL);
        
        MimeBodyPart body = new MimeBodyPart();
        body.setText(message, StandardCharsets.UTF_8.name(), "text/plain");
        
        return new MimeTypeEmailBuilder()
                .withSender(sender)
                .withRecipient(CONFIG.getProperty(SYSOPS_EMAIL))
                .withSubject(subject)
                .withMessageParts(body)
                .build();
    }

}
