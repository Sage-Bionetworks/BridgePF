package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BasicEmailProvider implements MimeTypeEmailProvider {

    private final Study study;
    private final Set<String> recipientEmails;
    private final Map<String,String> tokenMap;
    private final EmailTemplate template;
    
    private BasicEmailProvider(Study study, Map<String,String> tokenMap, Set<String> recipientEmails, EmailTemplate template) {
        this.study = study;
        this.recipientEmails = recipientEmails;
        this.tokenMap = tokenMap;
        this.template = template;
    }
    public Study getStudy() {
        return study;
    }
    public Set<String> getRecipientEmails() {
        return recipientEmails;
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
        tokenMap.put("host", BridgeConfigFactory.getConfig().getHostnameWithPostfix("webservices"));
        
        final MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        final String formattedSubject = BridgeUtils.resolveTemplate(template.getSubject(), tokenMap);
        builder.withSubject(formattedSubject);

        Set<String> senderEmails = BridgeUtils.commaListToOrderedSet(study.getSupportEmail());
        String senderEmail = Iterables.getFirst(senderEmails, null);
        final String sendFromEmail = String.format("%s <%s>", study.getName(), senderEmail);
        builder.withSender(sendFromEmail);

        for (String recipientEmail : recipientEmails) {
            builder.withRecipient(recipientEmail);    
        }
        final String formattedBody = BridgeUtils.resolveTemplate(template.getBody(), tokenMap);
        
        final MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(formattedBody, template.getMimeType().toString());
        builder.withMessageParts(bodyPart);
        
        return builder.build();
    }

    public static class Builder {
        private Study study;
        private Map<String,String> tokenMap = Maps.newHashMap();
        private Set<String> recipientEmails = Sets.newHashSet();
        private EmailTemplate template;

        public Builder withStudy(Study study) {
            this.study = study;
            return this;
        }
        public Builder withRecipientEmail(String recipientEmail) {
            this.recipientEmails.add(recipientEmail);
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
            checkNotNull(study);
            checkNotNull(template);
            return new BasicEmailProvider(study, tokenMap, recipientEmails, template);
        }
    }
}
