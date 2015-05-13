package org.sagebionetworks.bridge.services.email;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.PreencodedMimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.lowagie.text.DocumentException;

public class ConsentEmailProvider implements MimeTypeEmailProvider {

    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("MMMM d, yyyy");
    private static final String CONSENT_EMAIL_SUBJECT = "Consent Agreement for %s";
    private static final String HEADER_CONTENT_DISPOSITION_CONSENT_VALUE = "inline";
    private static final String HEADER_CONTENT_ID_CONSENT_VALUE = "<consentSignature>";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String HEADER_CONTENT_TRANSFER_ENCODING_VALUE = "base64";
    private static final String SUB_TYPE_HTML = "html";
    private static final String MIME_TYPE_PDF = "application/pdf";
    
    private User user;
    private Study study;
    private ConsentSignature consentSignature;
    private SharingScope sharingScope;
    private StudyConsentService studyConsentService;

    public ConsentEmailProvider(Study study, User user, ConsentSignature consentSignature, SharingScope sharingScope,
        StudyConsentService studyConsentService) {
        this.study = study;
        this.user = user;
        this.consentSignature = consentSignature;
        this.sharingScope = sharingScope;
        this.studyConsentService = studyConsentService;
    }

    @Override
    public MimeTypeEmail getEmail(String defaultSender) throws MessagingException {
        MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();
        
        String subject = String.format(CONSENT_EMAIL_SUBJECT, study.getName());
        builder.withSubject(subject);
        
        final String sendFromEmail = isNotBlank(study.getSupportEmail()) ?
                String.format("%s <%s>", study.getName(), study.getSupportEmail()) : defaultSender;
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
                "data:" + consentSignature.getImageMimeType() +
                ";base64," + consentSignature.getImageData());
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

    private String createSignedDocument() {
        StudyConsentView consent = studyConsentService.getActiveConsent(study.getStudyIdentifier());
        String consentAgreementHTML = consent.getDocumentContent();
        
        String signingDate = FORMATTER.print(DateUtils.getCurrentMillisFromEpoch());
        String html = consentAgreementHTML.replace("@@name@@", consentSignature.getName());
        html = html.replace("@@signing.date@@", signingDate);
        html = html.replace("@@email@@", user.getEmail());
        String sharingLabel = (sharingScope == null) ? "" : sharingScope.getLabel();
        html = html.replace("@@sharing@@", sharingLabel);
        return html;
    }

    private byte[] createPdf(final String consentDoc) {
        try (ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(consentDoc);
            renderer.layout();
            renderer.createPDF(byteArrayBuilder);
            byteArrayBuilder.flush();
            return byteArrayBuilder.toByteArray();
        } catch (DocumentException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    private Set<String> commaListToSet(String commaList) {
        if (isNotBlank(commaList)) {
            return org.springframework.util.StringUtils.commaDelimitedListToSet(commaList);    
        }
        return Collections.emptySet();
    }
}
