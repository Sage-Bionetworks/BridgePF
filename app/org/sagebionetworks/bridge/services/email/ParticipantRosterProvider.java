package org.sagebionetworks.bridge.services.email;

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;

public class ParticipantRosterProvider implements MimeTypeEmailProvider {

    private static final String PARTICIPANTS_EMAIL_SUBJECT = "Study participants for %s";
    private static final String HEADER_CONTENT_DISPOSITION_PARTICIPANTS_VALUE = "attachment; filename=participants.tsv";
    private static final String HEADER_CONTENT_ID_PARTICIPANTS_VALUE = "<participantsTSV>";
    private static final String MIME_TYPE_TSV = "text/tab-separated-value";
    private static final String DELIMITER = "\t";
    private static final String NEWLINE = "\n";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String HEADER_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    private static final String HEADER_CONTENT_TRANSFER_ENCODING_VALUE = "base64";
    private static final String MIME_TYPE_TEXT = "text/plain";
    
    private Study study;
    private List<StudyParticipant> participants;
    
    public ParticipantRosterProvider(Study study, List<StudyParticipant> participants) {
        this.study = study;
        this.participants = participants;
    }
    
    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();
        
        builder.withRecipient(study.getConsentNotificationEmail());
        
        String subject = String.format(PARTICIPANTS_EMAIL_SUBJECT, study.getName());
        builder.withSubject(subject);
        
        MimeBodyPart body = new MimeBodyPart();
        body.setContent(createInlineParticipantRoster(), MIME_TYPE_TEXT);
        builder.withMessageParts(body);
        
        MimeBodyPart tsvFile = new MimeBodyPart();
        tsvFile.setContentID(HEADER_CONTENT_ID_PARTICIPANTS_VALUE);
        tsvFile.setHeader(HEADER_CONTENT_DISPOSITION, HEADER_CONTENT_DISPOSITION_PARTICIPANTS_VALUE);
        tsvFile.setHeader(HEADER_CONTENT_TRANSFER_ENCODING, HEADER_CONTENT_TRANSFER_ENCODING_VALUE); 
        tsvFile.setContent(createParticipantTSV(), MIME_TYPE_TSV);
        builder.withMessageParts(tsvFile);
        
        return builder.build();
    }
    
    List<StudyParticipant> getParticipants() {
        return participants;
    }
    
    String createInlineParticipantRoster() {
        StringBuilder sb = new StringBuilder();
        if (participants.size() == 0) {
            sb.append("There are no users enrolled in this study.");
        } else if (participants.size() == 1) {
            sb.append("There is 1 user enrolled in this study. Please see the attached TSV file.");
        } else {
            sb.append("There are "+participants.size()+" users enrolled in this study. Please see the attached TSV file.");
        }
        sb.append(NEWLINE);
        return sb.toString();
    }

    String createParticipantTSV() {
        StringBuilder sb = new StringBuilder();
        append(sb, "Email", false);
        append(sb, "First Name", true);
        append(sb, "Last Name", true);
        append(sb, "Sharing Scope", true);
        append(sb, "Email Notifications", true);
        for (String attribute : study.getUserProfileAttributes()) {
            append(sb, StringUtils.capitalize(attribute), true);
        }
        if (study.isHealthCodeExportEnabled()) {
            append(sb, "Health Code", true);
        }
        sb.append(NEWLINE);
        for (int i=0; i < participants.size(); i++) {
            StudyParticipant participant = participants.get(i);
            SharingScope scope = participant.getSharingScope();
            Boolean notifyByEmail = participant.getNotifyByEmail();
            
            append(sb, participant.getEmail(), false);
            append(sb, participant.getFirstName(), true);
            append(sb, participant.getLastName(), true);
            append(sb, (scope == null) ? "" : scope.getLabel(), true);
            append(sb, (notifyByEmail == null) ? "" : notifyByEmail.toString().toLowerCase(), true);
            for (String attribute : study.getUserProfileAttributes()) {
                append(sb, participant.getEmpty(attribute), true);
            }
            if (study.isHealthCodeExportEnabled()) {
                append(sb, participant.getHealthCode(), true);
            }
            sb.append(NEWLINE);
        }
        return sb.toString();
    }
    
    private void append(StringBuilder sb, String value, boolean withComma) {
        if (withComma) {
            sb.append(DELIMITER);
        }
        sb.append(value.replaceAll("\t", " "));
    }

}
