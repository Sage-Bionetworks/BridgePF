package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;

public class BasicEmailProvider extends MimeTypeEmailProvider {
    private final String overrideSenderEmail;
    private final Set<String> recipientEmails;
    private final Map<String,String> tokenMap;
    private final EmailTemplate template;
    
    private BasicEmailProvider(Study study, String overrideSenderEmail, Map<String,String> tokenMap,
            Set<String> recipientEmails, EmailTemplate template) {
        super(study);
        this.overrideSenderEmail = overrideSenderEmail;
        this.recipientEmails = recipientEmails;
        this.tokenMap = tokenMap;
        this.template = template;
    }

    /**
     * If overrideSenderEmail is specified, returns that. Otherwise, returns the plain sender email from the study,
     * as specified in {@link MimeTypeEmailProvider#getPlainSenderEmail}.
     */
    @Override
    public String getPlainSenderEmail() {
        if (StringUtils.isNotBlank(overrideSenderEmail)) {
            return overrideSenderEmail;
        } else {
            return super.getPlainSenderEmail();
        }
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
        tokenMap.putAll(BridgeUtils.studyTemplateVariables(getStudy()));
        
        final MimeTypeEmailBuilder emailBuilder = new MimeTypeEmailBuilder();

        final String formattedSubject = BridgeUtils.resolveTemplate(template.getSubject(), tokenMap);
        emailBuilder.withSubject(formattedSubject);

        final String sendFromEmail = getFormattedSenderEmail();
        emailBuilder.withSender(sendFromEmail);

        for (String recipientEmail : recipientEmails) {
            emailBuilder.withRecipient(recipientEmail);    
        }
        final String formattedBody = BridgeUtils.resolveTemplate(template.getBody(), tokenMap);
        
        final MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(formattedBody, template.getMimeType().toString());
        emailBuilder.withMessageParts(bodyPart);
        
        return emailBuilder.build();
    }

    public static class Builder {
        private Study study;
        private String overrideSenderEmail;
        private Map<String,String> tokenMap = Maps.newHashMap();
        private Set<String> recipientEmails = Sets.newHashSet();
        private EmailTemplate template;

        public Builder withStudy(Study study) {
            this.study = study;
            return this;
        }

        /**
         * Specify the sender email, instead of getting it from the study. This is the plain, unformmated email, for
         * example "example@example.com".
         */
        public Builder withOverrideSenderEmail(String overrideSenderEmail) {
            this.overrideSenderEmail = overrideSenderEmail;
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
        public Builder withExpirationPeriod(String name, int expireInSeconds) {
            withToken(name, BridgeUtils.secondsToPeriodString(expireInSeconds));
            return this;
        }
        public BasicEmailProvider build() {
            checkNotNull(study);
            checkNotNull(template);

            return new BasicEmailProvider(study, overrideSenderEmail, tokenMap, recipientEmails, template);
        }
    }
}
