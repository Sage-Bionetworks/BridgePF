package org.sagebionetworks.bridge.services.email;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;

public class EmailSignInEmailProvider implements MimeTypeEmailProvider {

    private final Study study;
    private final String recipientEmail;
    private final String subject;
    private final String body;
    private final String token;
    
    public EmailSignInEmailProvider(Study study, String recipientEmail, String subject, String body, String token) {
        this.study = study;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.token = token;
    }
    
    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("supportEmail", study.getSupportEmail());
        map.put("technicalEmail", study.getTechnicalEmail());
        map.put("sponsorName", study.getSponsorName());
        map.put("token", token);
        map.put("host", BridgeConfigFactory.getConfig().getHostnameWithPostfix("webservices"));
        
        MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        String formattedSubject = BridgeUtils.resolveTemplate(subject, map);
        builder.withSubject(formattedSubject);

        final String sendFromEmail = String.format("%s <%s>", study.getName(), study.getSupportEmail());
        builder.withSender(sendFromEmail);

        builder.withRecipient(recipientEmail);

        final MimeBodyPart bodyPart = new MimeBodyPart();
        final String formattedBody = BridgeUtils.resolveTemplate(body, map);
        bodyPart.setText(formattedBody, StandardCharsets.UTF_8.name(), "html");
        builder.withMessageParts(bodyPart);
        
        return builder.build();
    }
    
}
