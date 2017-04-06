package org.sagebionetworks.bridge.services.email;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;

public class EmailSignInEmailProvider implements MimeTypeEmailProvider {

    private final Study study;
    private final String recipientEmail;
    private final String token;
    
    public EmailSignInEmailProvider(Study study, String recipientEmail, String token) {
        this.study = study;
        this.recipientEmail = recipientEmail;
        this.token = token;
    }
    
    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        String encodedRecipientEmail = null;
        try {
            encodedRecipientEmail = URLEncoder.encode(recipientEmail, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new BadRequestException("Email was malformed: " + recipientEmail + ".");
        }
        
        Map<String,String> map = Maps.newHashMap();
        map.put("studyName", study.getName());
        map.put("studyId", study.getIdentifier());
        map.put("supportEmail", study.getSupportEmail());
        map.put("technicalEmail", study.getTechnicalEmail());
        map.put("sponsorName", study.getSponsorName());
        map.put("token", token);
        map.put("email", encodedRecipientEmail);
        map.put("host", BridgeConfigFactory.getConfig().getHostnameWithPostfix("webservices"));
        
        final MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        final EmailTemplate template = study.getEmailSignInTemplate();
        final String subject = template.getSubject();
        final String formattedSubject = BridgeUtils.resolveTemplate(subject, map);
        builder.withSubject(formattedSubject);

        final String sendFromEmail = String.format("%s <%s>", study.getName(), study.getSupportEmail());
        builder.withSender(sendFromEmail);

        builder.withRecipient(recipientEmail);

        final MimeBodyPart bodyPart = new MimeBodyPart();
        final String body = template.getBody();
        final MimeType mimeType = template.getMimeType();
        final String formattedBody = BridgeUtils.resolveTemplate(body, map);
        bodyPart.setContent(formattedBody, mimeType.toString());
        builder.withMessageParts(bodyPart);
        
        return builder.build();
    }
    
}
