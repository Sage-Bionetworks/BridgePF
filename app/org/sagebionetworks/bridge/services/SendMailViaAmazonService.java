package org.sagebionetworks.bridge.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.PreencodedMimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.lowagie.text.DocumentException;

public class SendMailViaAmazonService implements SendMailService {
    
    private static final Logger logger = LoggerFactory.getLogger(SendMailViaAmazonService.class);

    private static final DateTimeFormatter fmt = DateTimeFormat.forPattern("MMMM d, yyyy");
    private static final String CONSENT_EMAIL_SUBJECT = "Consent Agreement for %s";
    private static final String HEADER_CONTENT_DISPOSITION_CONSENT_VALUE = "inline";
    private static final String HEADER_CONTENT_ID_CONSENT_VALUE = "<consentSignature>";
    
    private static final String PARTICIPANTS_EMAIL_SUBJECT = "Study participants for %s";
    private static final String HEADER_CONTENT_DISPOSITION_PARTICIPANTS_VALUE = "attachment; filename=participants.csv";
    private static final String HEADER_CONTENT_ID_PARTICIPANTS_VALUE = "<participantsCSV>";
    
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String HEADER_CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    private static final String HEADER_CONTENT_TRANSFER_ENCODING_VALUE = "base64";
    private static final String MIME_TYPE_TSV = "text/tab-separated-value";
    private static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_TEXT = "text/plain";
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String DELIMITER = "\t";
    private static final String NEWLINE = "\n";
    private static final Region region = Region.getRegion(Regions.US_EAST_1);

    private String fromEmail;
    private AmazonSimpleEmailServiceClient emailClient;
    private StudyService studyService;

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public void setEmailClient(AmazonSimpleEmailServiceClient emailClient) {
        this.emailClient = emailClient;
    }

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Override
    public void sendConsentAgreement(User user, ConsentSignature consentSignature, StudyConsent studyConsent) {

        try {
            final String consentDoc = createSignedDocument(user, consentSignature, studyConsent);

            // Consent agreement as message body in HTML
            final MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(consentDoc, MIME_TYPE_HTML);

            // Consent agreement as a PDF attachment
            ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            ITextRenderer renderer = new ITextRenderer();
            // Embed the signature image
            String consentDocWithSig = consentDoc.replace("cid:consentSignature",
                    "data:" + consentSignature.getImageMimeType() +
                    ";base64," + consentSignature.getImageData());
            renderer.setDocumentFromString(consentDocWithSig);
            renderer.layout();
            renderer.createPDF(byteArrayBuilder);
            byteArrayBuilder.flush();
            final byte[] pdfBytes = byteArrayBuilder.toByteArray();
            byteArrayBuilder.close();

            final MimeBodyPart pdfPart = new MimeBodyPart();
            DataSource source = new ByteArrayDataSource(pdfBytes, MIME_TYPE_PDF);
            pdfPart.setDataHandler(new DataHandler(source));
            pdfPart.setFileName("consent.pdf");

            // Write signature image as an attachment, if it exists. Because of the validation in ConsentSignature, if
            // imageData is present, so will imageMimeType.
            // We need to send the signature image as an embedded image in an attachment because some email providers
            // (notably Gmail) block inline Base64 images.
            MimeBodyPart sigPart = null;
            if (consentSignature.getImageData() != null) {
                // Use pre-encoded MIME part since our image data is already base64 encoded.
                sigPart = new PreencodedMimeBodyPart(HEADER_CONTENT_TRANSFER_ENCODING_VALUE);
                sigPart.setContentID(HEADER_CONTENT_ID_CONSENT_VALUE);
                sigPart.setHeader(HEADER_CONTENT_DISPOSITION, HEADER_CONTENT_DISPOSITION_CONSENT_VALUE);
                sigPart.setContent(consentSignature.getImageData(), consentSignature.getImageMimeType());
            }

            // As recommended by Amazon, we send the emails separately
            final Study study = studyService.getStudy(studyConsent.getStudyKey());
            String subject = String.format(CONSENT_EMAIL_SUBJECT, study.getName());

            sendEmailTo(subject, user.getEmail(), bodyPart, pdfPart, sigPart);
            Set<String> emailAddresses = commaListToSet(study.getConsentNotificationEmail());
            for (String email : emailAddresses) {
                sendEmailTo(subject, email, bodyPart, pdfPart, sigPart);
            }
        } catch(IOException | MessagingException | DocumentException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    @Override
    public void sendStudyParticipantsRoster(Study study, List<StudyParticipant> participants) {
        try {
            // Very simple for now, let's just see it work.
            String body = createInlineParticipantRoster(participants);
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(body, MIME_TYPE_TEXT);
            
            body = createParticipantCSV(participants);
            MimeBodyPart csvPart = new MimeBodyPart();
            csvPart.setContentID(HEADER_CONTENT_ID_PARTICIPANTS_VALUE);
            csvPart.setHeader(HEADER_CONTENT_DISPOSITION, HEADER_CONTENT_DISPOSITION_PARTICIPANTS_VALUE);
            csvPart.setHeader(HEADER_CONTENT_TRANSFER_ENCODING, HEADER_CONTENT_TRANSFER_ENCODING_VALUE); 
            csvPart.setContent(body, MIME_TYPE_TSV);
            
            String subject = String.format(PARTICIPANTS_EMAIL_SUBJECT, study.getName());
            sendEmailTo(subject, study.getConsentNotificationEmail(), bodyPart, csvPart);
            
        } catch(MessagingException | AmazonClientException | IOException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    String createInlineParticipantRoster(List<StudyParticipant> participants) {
        StringBuilder sb = new StringBuilder();
        if (participants.size() == 0) {
            sb.append("There are no users enrolled in this study.");
        } else if (participants.size() == 1) {
            sb.append("There is 1 user enrolled in this study:");
        } else {
            sb.append("There are "+participants.size()+" users enrolled in this study:");
        }
        sb.append(NEWLINE);
        for (int i=0; i < participants.size(); i++) {
            StudyParticipant participant = participants.get(i);
            
            sb.append(NEWLINE).append(participant.getEmail()).append(" (");
            if (participant.getFirstName() == null && participant.getLastName() == null) {
                sb.append("No name given");
            } else if (participant.getFirstName() != null && participant.getLastName() != null) {
                sb.append(participant.getFirstName()).append(" ").append(participant.getLastName());
            } else if (participant.getFirstName() != null) {
                sb.append(participant.getFirstName());
            } else if (participant.getLastName() != null) {
                sb.append(participant.getLastName());
            }
            sb.append(")"+NEWLINE);
            if (participant.getPhone() != null) {
                sb.append("Phone: ").append(participant.getPhone()).append(NEWLINE);    
            }
        }
        return sb.toString();
    }
    
    String createParticipantCSV(List<StudyParticipant> participants) {
        StringBuilder sb = new StringBuilder();
        append(sb, "Email", true);
        append(sb, "First Name", true);
        append(sb, "Last Name", true);
        append(sb, "Phone", false);
        sb.append(NEWLINE);
        for (int i=0; i < participants.size(); i++) {
            StudyParticipant participant = participants.get(i);
            append(sb, participant.getEmail(), true);
            append(sb, participant.getFirstName(), true);
            append(sb, participant.getLastName(), true);
            append(sb, participant.getPhone(), false);
            sb.append(NEWLINE);
        }
        return sb.toString();
    }

    private void sendEmailTo(String subject, String toEmail, MimeBodyPart... parts) throws AmazonClientException,
            MessagingException, IOException {

        // Create email using JavaMail
        Session mailSession = Session.getInstance(new Properties(), null);
        MimeMessage mimeMessage = new MimeMessage(mailSession);
        mimeMessage.setFrom(new InternetAddress(fromEmail));
        mimeMessage.setSubject(subject, Charsets.UTF_8.name());
        mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));

        MimeMultipart mimeMultipart = new MimeMultipart();
        for (MimeBodyPart part : parts) {
            if (part != null) {
                mimeMultipart.addBodyPart(part);    
            }
        }

        // Convert MimeMessage to raw text to send to SES.
        mimeMessage.setContent(mimeMultipart);
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteOutputStream);
        RawMessage sesRawMessage = new RawMessage(ByteBuffer.wrap(byteOutputStream.toByteArray()));

        SendRawEmailRequest req = new SendRawEmailRequest(sesRawMessage);
        req.setSource(fromEmail);
        req.setDestinations(Collections.singleton(toEmail));
        emailClient.setRegion(region);
        SendRawEmailResult result = emailClient.sendRawEmail(req);

        logger.info(String.format("Sent email to SES with message ID %s", result.getMessageId()));
    }

    private Set<String> commaListToSet(String commaList) {
        if (StringUtils.isNotBlank(commaList)) {
            return org.springframework.util.StringUtils.commaDelimitedListToSet(commaList);    
        }
        return Collections.emptySet();
    }

    private String createSignedDocument(User user, ConsentSignature consent, StudyConsent studyConsent)
            throws IOException {

        String filePath = studyConsent.getPath();
        FileSystemResource resource = new FileSystemResource(filePath);
        InputStreamReader isr = new InputStreamReader(resource.getInputStream(), "UTF-8");
        String consentAgreementHTML = CharStreams.toString(isr);

        String signingDate = fmt.print(DateUtils.getCurrentMillisFromEpoch());

        String html = consentAgreementHTML.replace("@@name@@", consent.getName());
        html = html.replace("@@signing.date@@", signingDate);
        html = html.replace("@@email@@", user.getEmail());
        return html;
    }
    
    private void append(StringBuilder sb, String value, boolean withComma) {
        sb.append( (value != null) ? value.replaceAll("\t", " ") : "" );
        if (withComma) {
            sb.append(DELIMITER);
        }
    }
}
