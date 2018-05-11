package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

public class ConsentPdfTest {
    private static final long TIMESTAMP = DateTime.parse("2017-10-04").getMillis();
    private static final String LEGACY_DOCUMENT = "<html><head></head><body>Passed through as is." +
            "|@@name@@|@@signing.date@@|@@email@@|@@sharing@@|" +
            "<img src=\"cid:consentSignature\" /></body></html>";
    private static final String NEW_DOCUMENT_FRAGMENT = "<p>This is a consent agreement body</p>";
    // This is an actual 2x2 image
    private static final String DUMMY_IMAGE_DATA =
            "Qk1GAAAAAAAAADYAAAAoAAAAAgAAAAIAAAABABgAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAA////AAAAAAAAAAD///8AAA==";
    private static final StudyParticipant EMAIL_PARTICIPANT = new StudyParticipant.Builder()
            .withEmail("user@user.com").withEmailVerified(true).build();;
    
    private String consentBodyTemplate;
    private Study study;
    
    @Before
    public void before() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP);
        consentBodyTemplate = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml"));
        
        study = new DynamoStudy();
        study.setName("Study Name");
        study.setSponsorName("Sponsor Name");
        study.setSupportEmail("sender@default.com");
        study.setConsentNotificationEmail("consent@consent.com");
        study.setConsentNotificationEmailVerified(true);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void createsBytes() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING, LEGACY_DOCUMENT,
                consentBodyTemplate);
        
        assertTrue(consentPdf.getBytes().length > 0);
    }

    @Test
    public void docWithNullUserTimeZone() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING, LEGACY_DOCUMENT,
                consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now(DateTimeZone.UTC));
        assertTrue("Signing date formatted with default zone", output.contains(dateStr));
    }
    
    @Test
    public void legacyDocWithoutSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING, LEGACY_DOCUMENT,
                consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument();
        validateLegacyDocBody(output);
    }

    @Test
    public void newDocWithoutSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument(); 
        validateNewDocBody(output);
    }

    @Test
    public void legacyDocWithSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING, LEGACY_DOCUMENT,
                consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument();
        validateLegacyDocBody(output);
    }

    @Test
    public void newDocWithSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateNewDocBody(output);
    }
    @Test
    public void legacyDocWithInvalidSig() throws Exception {
        ConsentSignature sig = makeInvalidSignature();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateNewDocBody(output);
    }

    @Test
    public void newDocWithInvalidSig() throws Exception {
        ConsentSignature sig = makeInvalidSignature();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateNewDocBody(output);
    }
    
    @Test
    public void phoneSignature() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        StudyParticipant phoneParticipant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE).withPhoneVerified(true).build();
        
        ConsentPdf consentPdf = new ConsentPdf(study, phoneParticipant, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        String output = consentPdf.getFormattedConsentDocument();
        assertTrue(output.contains(">(971) 248-6796<"));
        assertTrue(output.contains(">Phone Number<"));
    }
    
    @Test
    public void externalIdSignature() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        StudyParticipant extIdParticipant = new StudyParticipant.Builder().withExternalId("anId").build();
        
        ConsentPdf consentPdf = new ConsentPdf(study, extIdParticipant, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        String output = consentPdf.getFormattedConsentDocument();
        assertTrue(output.contains(">anId<"));
        assertTrue(output.contains(">ID<"));
    }
    
    @Test 
    public void dateFormattedCorrectly() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(study, EMAIL_PARTICIPANT, sig, SharingScope.NO_SHARING,
                NEW_DOCUMENT_FRAGMENT, consentBodyTemplate);
        String output = consentPdf.getFormattedConsentDocument();

        assertTrue("Contains formatted date", output.contains("October 4, 2017 (GMT)"));
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
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now());
        assertTrue("Signing date correct", bodyContent.contains(dateStr));
        assertTrue("Name correct", bodyContent.contains("|Test Person|"));
        assertTrue("User email correct", bodyContent.contains("|user@user.com|"));
        assertTrue("Sharing correct", bodyContent.contains("|Not Sharing|"));
        assertTrue("HTML markup preserved", bodyContent.contains("<html><head></head><body>Passed through as is."));
    }

    private static void validateNewDocBody(String bodyContent) throws Exception {
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now());
        assertTrue("Signing date correct", bodyContent.contains(dateStr));
        assertTrue("Study name correct", bodyContent.contains("<title>Study Name Consent To Research</title>"));
        assertTrue("Name correct", bodyContent.contains(">Test Person<"));
        assertTrue("User email correct", bodyContent.contains(">user@user.com<"));
        assertTrue("Sharing correct", bodyContent.contains(">Not Sharing<"));
        assertTrue("Contact correctly labeled", bodyContent.contains(">Email Address<"));
    }
}
