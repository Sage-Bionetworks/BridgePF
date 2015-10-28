package org.sagebionetworks.bridge.services.email;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.PreencodedMimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.models.studies.Study;

public class WithdrawConsentEmailProvider implements MimeTypeEmailProvider {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("MMMM d, yyyy");
    private static final String CONSENT_EMAIL_SUBJECT = "Notification of consent withdrawal for %s";
    private static final String HEADER_CONTENT_DISPOSITION_CONSENT_VALUE = "inline";
    private static final String SUB_TYPE_HTML = "html";

    private Study study;
    
    public WithdrawConsentEmailProvider(Study study) {
        this.study = study;
    }
    
    @Override
    public MimeTypeEmail getEmail(String defaultSender) throws MessagingException {
        MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        String subject = String.format(CONSENT_EMAIL_SUBJECT, study.getName());
        builder.withSubject(subject);

        final String sendFromEmail = isNotBlank(study.getSupportEmail()) ? String.format("%s <%s>", study.getName(),
                        study.getSupportEmail()) : defaultSender;
        builder.withSender(sendFromEmail);

        builder.withRecipient(user.getEmail());
        Set<String> emailAddresses = commaListToSet(study.getConsentNotificationEmail());
        for (String email : emailAddresses) {
            builder.withRecipient(email);
        }

        final String consentDoc = createSignedDocument();

        // Consent agreement as message body in HTML
        final MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(consentDoc, StandardCharsets.UTF_8.name(), SUB_TYPE_HTML);
        builder.withMessageParts(bodyPart);

        // Consent agreement as a PDF attachment
        // Embed the signature image
        String consentDocWithSig = consentDoc.replace("cid:consentSignature",
                        "data:" + consentSignature.getImageMimeType() + ";base64," + consentSignature.getImageData());
        final byte[] pdfBytes = createPdf(consentDocWithSig);
        final MimeBodyPart pdfPart = new MimeBodyPart();
        DataSource source = new ByteArrayDataSource(pdfBytes, MIME_TYPE_PDF);
        pdfPart.setDataHandler(new DataHandler(source));
        pdfPart.setFileName("consent.pdf");
        builder.withMessageParts(pdfPart);

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
        builder.withMessageParts(sigPart);

        return builder.build();
    }

}
