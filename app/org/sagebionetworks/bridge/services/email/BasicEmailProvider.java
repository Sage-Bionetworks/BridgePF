package org.sagebionetworks.bridge.services.email;

import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class BasicEmailProvider implements MimeTypeEmailProvider {
    private final Study study;
    private final String recipientEmail;
    private final Map<String,String> tokenMap;
    private final EmailTemplate template;
    
    private BasicEmailProvider(Study study, Map<String,String> tokenMap, String recipientEmail, EmailTemplate template) {
        this.study = study;
        this.recipientEmail = recipientEmail;
        this.tokenMap = tokenMap;
        this.template = template;
    }
    public Study getStudy() {
        return study;
    }
    public String getRecipientEmail() {
        return recipientEmail;
    }
    public Map<String,String> getTokenMap() {
        return ImmutableMap.copyOf(tokenMap);
    }
    public EmailTemplate getTemplate() {
        return template;
    }
    
    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        tokenMap.put("studyName", study.getName());
        tokenMap.put("studyId", study.getIdentifier());
        tokenMap.put("supportEmail", study.getSupportEmail());
        tokenMap.put("technicalEmail", study.getTechnicalEmail());
        tokenMap.put("sponsorName", study.getSponsorName());
        tokenMap.put("email", BridgeUtils.encodeURIComponent(recipientEmail));
        tokenMap.put("host", BridgeConfigFactory.getConfig().getHostnameWithPostfix("webservices"));
        
        final MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        final String formattedSubject = BridgeUtils.resolveTemplate(template.getSubject(), tokenMap);
        builder.withSubject(formattedSubject);

        final String sendFromEmail = String.format("%s <%s>", study.getName(), study.getSupportEmail());
        builder.withSender(sendFromEmail);

        builder.withRecipient(recipientEmail);

        final String formattedBody = BridgeUtils.resolveTemplate(template.getBody(), tokenMap);
        
        final MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(formattedBody, template.getMimeType().toString());
        builder.withMessageParts(bodyPart);
        
        return builder.build();
    }
    
    public static class Builder {
        private Study study;
        private Map<String,String> tokenMap = Maps.newHashMap();
        private String recipientEmail;
        private EmailTemplate template;

        public Builder withStudy(Study study) {
            this.study = study;
            return this;
        }
        public Builder withRecipientEmail(String recipientEmail) {
            this.recipientEmail = recipientEmail;
            return this;
        }
        public Builder withEmailTemplate(EmailTemplate template) {
            this.template = template;
            return this;
        }
        public Builder withToken(String name, String value) {
            tokenMap.put(name, value);
            return this;
        }
        public BasicEmailProvider build() {
            return new BasicEmailProvider(study, tokenMap, recipientEmail, template);
        }
    }
}
