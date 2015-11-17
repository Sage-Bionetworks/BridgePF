package org.sagebionetworks.bridge.services.email;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;

public class WithdrawConsentEmailProvider implements MimeTypeEmailProvider {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("MMMM d, yyyy");
    private static final String CONSENT_EMAIL_SUBJECT = "Notification of consent withdrawal for %s";
    private static final String SUB_TYPE_HTML = "html";

    private Study study;
    private String externalId;
    private User user;
    private Withdrawal withdrawal;
    private long withdrewOn;
    
    public WithdrawConsentEmailProvider(Study study, String externalId, User user, Withdrawal withdrawal, long withdrewOn) {
        this.study = study;
        this.externalId = externalId;
        this.user = user;
        this.withdrawal = withdrawal;
        this.withdrewOn = withdrewOn;
    }
    
    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        String subject = String.format(CONSENT_EMAIL_SUBJECT, study.getName());
        builder.withSubject(subject);

        final String sendFromEmail = String.format("%s <%s>", study.getName(), study.getSupportEmail());
        builder.withSender(sendFromEmail);

        Set<String> emailAddresses = BridgeUtils.commaListToSet(study.getConsentNotificationEmail());
        builder.withRecipients(emailAddresses);

        String content = String.format("<p>User %s withdrew from the study on %s. </p>", 
                getUserLabel(), FORMATTER.print(withdrewOn));
        content += "<p>Reason:</p>";
        if (StringUtils.isBlank(withdrawal.getReason())) {
            content += "<p><i>No reason given.</i></p>";
        } else {
            content += "<p>"+withdrawal.getReason()+"</p>";
        }
        
        // Consent agreement as message body in HTML
        final MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(content, StandardCharsets.UTF_8.name(), SUB_TYPE_HTML);
        builder.withMessageParts(bodyPart);

        return builder.build();
    }
    
    private String getUserLabel() {
        String label = String.format("%s %s &lt;%s&gt;", nullSafe(user.getFirstName()), nullSafe(user.getLastName()), user.getEmail());
        if (externalId != null) {
            label += " (external ID: " + externalId + ") ";
        }
        return label;
    }

    private String nullSafe(String value) {
        return (value == null) ? "" : value;
    }
}
