package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;

public class ConsentPdfTest {
    private static final String LEGACY_DOCUMENT = "<html><head></head><body>Passed through as is." +
            "|@@name@@|@@signing.date@@|@@email@@|@@sharing@@|" +
            "<img src=\"cid:consentSignature\" /></body></html>";
    private static final String NEW_DOCUMENT_FRAGMENT = "<p>This is a consent agreement body</p>";

    // This is an actual 2x2 image
    private static final String DUMMY_IMAGE_DATA =
            "Qk1GAAAAAAAAADYAAAAoAAAAAgAAAAIAAAABABgAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAA////AAAAAAAAAAD///8AAA==";

    private static DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
    
    private String consentBodyTemplate;
    private Study study;
    private StudyParticipant participant;
    
    @Before
    public void before() throws Exception {
        consentBodyTemplate = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml"));
        
        study = new DynamoStudy();
        study.setName("Study Name");
        study.setSponsorName("Sponsor Name");
        study.setSupportEmail("sender@default.com");
        study.setConsentNotificationEmail("consent@consent.com");
        study.setConsentNotificationEmailVerified(true);

        participant = new StudyParticipant.Builder().withEmail("user@user.com").build();
    }
    
    @Test
    public void createsBytes() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, null, participant.getEmail(), sig,
            SharingScope.NO_SHARING, LEGACY_DOCUMENT, consentBodyTemplate);
        
        assertTrue(consentPdf.getBytes().length > 0);
    }

    @Test
    public void docWithNullUserTimeZone() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, null, participant.getEmail(), sig,
            SharingScope.NO_SHARING, LEGACY_DOCUMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now(DateTimeZone.UTC));
        assertTrue("Signing date formatted with default zone", output.contains(dateStr));
    }
    
    @Test
    public void legacyDocWithoutSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, PST, participant.getEmail(), sig,
                SharingScope.NO_SHARING, LEGACY_DOCUMENT, consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument();
        validateLegacyDocBody(output);
    }

    @Test
    public void newDocWithoutSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, PST, participant.getEmail(), sig,
                SharingScope.NO_SHARING, NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument(); 
        validateNewDocBody(output);
    }

    @Test
    public void legacyDocWithSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, PST, participant.getEmail(), sig,
                SharingScope.NO_SHARING, LEGACY_DOCUMENT, consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument();
        validateLegacyDocBody(output);
    }

    @Test
    public void newDocWithSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, PST, participant.getEmail(), sig,
                SharingScope.NO_SHARING, NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateNewDocBody(output);
    }
    @Test
    public void legacyDocWithInvalidSig() throws Exception {
        ConsentSignature sig = makeInvalidSignature();
        
        ConsentPdf consentPdf = new ConsentPdf(study, PST, participant.getEmail(), sig,
                SharingScope.NO_SHARING, NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateNewDocBody(output);
    }

    @Test
    public void newDocWithInvalidSig() throws Exception {
        ConsentSignature sig = makeInvalidSignature();
        
        ConsentPdf consentPdf = new ConsentPdf(study, PST, participant.getEmail(), sig,
                SharingScope.NO_SHARING, NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateNewDocBody(output);
    }

    private static ConsentSignature makeSignatureWithoutImage() {
        return new ConsentSignature.Builder().withName("Test Person").withBirthdate("1980-06-06").build();
    }

    private static ConsentSignature makeSignatureWithImage() {
        return new ConsentSignature.Builder().withName("Test Person").withBirthdate("1980-06-06")
                .withImageMimeType("image/bmp").withImageData(DUMMY_IMAGE_DATA).build();
    }

    private static ConsentSignature makeInvalidSignature() {
        return new ConsentSignature.Builder().withName("<a href=\"http://sagebase.org/\">Test Person</a>")
                .withBirthdate("1980-06-06").withImageMimeType("application/octet-stream")
                .withImageData("\" /><a href=\"http://sagebase.org/\">arbitrary link</a><br name=\"foo").build();
    }

    private static void validateLegacyDocBody(String bodyContent) throws Exception {
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now(PST));
        assertTrue("Signing date correct", bodyContent.contains(dateStr));
        assertTrue("Name correct", bodyContent.contains("|Test Person|"));
        assertTrue("User email correct", bodyContent.contains("|user@user.com|"));
        assertTrue("Sharing correct", bodyContent.contains("|Not Sharing|"));
        assertTrue("HTML markup preserved", bodyContent.contains("<html><head></head><body>Passed through as is."));
    }

    private static void validateNewDocBody(String bodyContent) throws Exception {
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now(PST));
        assertTrue("Signing date correct", bodyContent.contains(dateStr));
        assertTrue("Study name correct", bodyContent.contains("<title>Study Name Consent To Research</title>"));
        assertTrue("Name correct", bodyContent.contains(">Test Person<"));
        assertTrue("User email correct", bodyContent.contains(">user@user.com<"));
        assertTrue("Sharing correct", bodyContent.contains(">Not Sharing<"));
    }
}
