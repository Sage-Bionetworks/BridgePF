package org.sagebionetworks.bridge.services.email;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.PreencodedMimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import com.google.common.net.MediaType;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.common.collect.Maps;
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

    private Study study;
    private String userEmail;
    private ConsentSignature consentSignature;
    private SharingScope sharingScope;
    private String consentAgreementHTML;
    private String consentTemplate;

    public ConsentEmailProvider(Study study, String userEmail, ConsentSignature consentSignature,
            SharingScope sharingScope, String consentAgreementHTML, String consentTemplate) {
        this.study = study;
        this.userEmail = userEmail;
        this.consentSignature = consentSignature;
        this.sharingScope = sharingScope;
        this.consentAgreementHTML = consentAgreementHTML;
        this.consentTemplate = consentTemplate;
    }

    @Override
    public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
        MimeTypeEmailBuilder builder = new MimeTypeEmailBuilder();

        String subject = String.format(CONSENT_EMAIL_SUBJECT, study.getName());
        builder.withSubject(subject);

        final String sendFromEmail = String.format("%s <%s>", study.getName(), study.getSupportEmail());
        builder.withSender(sendFromEmail);

        // Must wrap in new list because set from BridgeUtils.commaListToSet() is immutable
        Set<String> recipients = BridgeUtils.commaListToOrderedSet(study.getConsentNotificationEmail());
        builder.withRecipients(recipients);
        builder.withRecipient(userEmail);

        final String consentDoc = createSignedDocument();

        // Consent agreement as message body in HTML
        final MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(consentDoc, StandardCharsets.UTF_8.name(), SUB_TYPE_HTML);
        builder.withMessageParts(bodyPart);

        // Users can inject arbitrary HTML in the consent signature. Scrub the consent sig and MIME type to make sure
        // it's valid.
        String imageMimeType = consentSignature.getImageMimeType();
        String imageData = consentSignature.getImageData();
        boolean validConsentSigImage = isImageMimeType(imageMimeType) && isBase64(imageData);

        // Consent agreement as a PDF attachment
        // Embed the signature image
        String consentDocWithSig = consentDoc;
        if (validConsentSigImage) {
            consentDocWithSig = consentDoc.replace("cid:consentSignature", "data:" + imageMimeType + ";base64," +
                    imageData);
        }

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
        if (validConsentSigImage) {
            // Use pre-encoded MIME part since our image data is already base64 encoded.
            MimeBodyPart sigPart = new PreencodedMimeBodyPart(HEADER_CONTENT_TRANSFER_ENCODING_VALUE);
            sigPart.setContentID(HEADER_CONTENT_ID_CONSENT_VALUE);
            sigPart.setHeader(HEADER_CONTENT_DISPOSITION, HEADER_CONTENT_DISPOSITION_CONSENT_VALUE);
            sigPart.setContent(imageData, imageMimeType);
            builder.withMessageParts(sigPart);
        }

        return builder.build();
    }

    /**
     * Consent documents were originally whole XHTML documents (they must be valid XML because PDF support from Java
     * libraries is limited and bad, and the tool we're using only works with XML as an input). In order to edit these
     * documents, we are moving to a system where only the content portion of the consent document, excluding the
     * signature block at the end, is available to researchers to edit. We then assemble the complete HTML document at
     * runtime. Here we branch based on the detection of a complete HTML document and do either one or the other.
     */
    private String createSignedDocument() {
        String signingDate = FORMATTER.print(DateUtils.getCurrentMillisFromEpoch());
        String sharingLabel = (sharingScope == null) ? "" : sharingScope.getLabel();

        // User's name may contain HTML. Clean it up
        String username = Jsoup.clean(consentSignature.getName(), Whitelist.none());
        
        if (consentAgreementHTML.contains("<html")) {
            // proceed as we used to
            String html = consentAgreementHTML.replace("@@name@@", username);
            html = html.replace("@@signing.date@@", signingDate);
            html = html.replace("@@email@@", userEmail);
            html = html.replace("@@sharing@@", sharingLabel);
            return html;
        } else {
            // This is now a fragment, assemble accordingly
            Map<String,String> map = Maps.newHashMap();
            map.put("studyName", study.getName());
            map.put("supportEmail", study.getSupportEmail());
            map.put("technicalEmail", study.getTechnicalEmail());
            map.put("sponsorName", study.getSponsorName());
            String resolvedConsentAgreementHTML = BridgeUtils.resolveTemplate(consentAgreementHTML, map);

            map = Maps.newHashMap();
            map.put("studyName", study.getName());
            map.put("consent.body", resolvedConsentAgreementHTML);
            map.put("participant.name", username);
            map.put("participant.signing.date", signingDate);
            map.put("participant.email", userEmail);
            map.put("participant.sharing", sharingLabel);
            
            return BridgeUtils.resolveTemplate(consentTemplate, map);
        }
    }

    private byte[] createPdf(final String consentDoc) {
        try (ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder()) {
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

    // Helper method to check if the given string is a valid Base64 string. Returns false for null or blank strings.
    private static boolean isBase64(String base64Str) {
        //noinspection SimplifiableIfStatement
        if (StringUtils.isBlank(base64Str)) {
            return false;
        } else {
            return Base64.isBase64(base64Str);
        }
    }

    // Helper method to check if a given MIME type is an image MIME type. Returns false for null or blank strings, or
    // for strings that are not valid MIME types.
    private static boolean isImageMimeType(String mimeTypeStr) {
        if (StringUtils.isBlank(mimeTypeStr)) {
            return false;
        }

        MediaType mimeType;
        try {
            mimeType = MediaType.parse(mimeTypeStr);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        return mimeType.is(MediaType.ANY_IMAGE_TYPE);
    }
}
