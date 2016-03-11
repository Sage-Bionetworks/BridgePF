package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.util.List;

import javax.mail.internet.MimeBodyPart;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyConsentService;

import com.google.common.collect.Sets;

public class ConsentEmailProviderTest {
    private static final String LEGACY_DOCUMENT = "<html><head></head><body>Passed through as is." +
            "|@@name@@|@@signing.date@@|@@email@@|@@sharing@@|" +
            "<img src=\"cid:consentSignature\" /></body></html>";
    private static final String NEW_DOCUMENT_FRAGMENT = "<p>This is a consent agreement body</p>";
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("subpopGuid");

    // This is an actual 2x2 image
    private static final String DUMMY_IMAGE_DATA =
            "Qk1GAAAAAAAAADYAAAAoAAAAAgAAAAIAAAABABgAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAA////AAAAAAAAAAD///8AAA==";

    private String consentBodyTemplate;
    private Study study;
    private StudyConsentService studyConsentService;
    private User user;
    
    @Before
    public void before() throws Exception {
        consentBodyTemplate = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml"));
        
        study = new DynamoStudy();
        study.setName("Study Name");
        study.setSponsorName("Sponsor Name");
        study.setSupportEmail("sender@default.com");
        study.setConsentNotificationEmail("consent@consent.com");
        
        user = new User();
        user.setEmail("user@user.com");
        
        studyConsentService = mock(StudyConsentService.class);
    }

    @Test
    public void legacyDocWithoutSigImage() throws Exception {
        // Setup and execute.
        setupStudyConsentServiceWithLegacyDoc();
        ConsentSignature sig = makeSignatureWithoutImage();
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SUBPOP_GUID, user, sig,
                SharingScope.NO_SHARING, studyConsentService, consentBodyTemplate);

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);

        // Validate we have 2 parts (body and PDF)
        List<MimeBodyPart> mimePartList = email.getMessageParts();
        assertEquals(2, mimePartList.size());

        // Validate body.
        MimeBodyPart body = mimePartList.get(0);
        validateLegacyDocBody(body);

        // We can't validate the PDF body since that's encoded in PDF format.
    }

    @Test
    public void newDocWithoutSigImage() throws Exception {
        // Setup and execute.
        setupStudyConsentServiceWithNewDoc();
        ConsentSignature sig = makeSignatureWithoutImage();
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SUBPOP_GUID, user, sig,
                SharingScope.NO_SHARING, studyConsentService, consentBodyTemplate);

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);

        // Validate we have 2 parts (body and PDF)
        List<MimeBodyPart> mimePartList = email.getMessageParts();
        assertEquals(2, mimePartList.size());

        // Validate body.
        MimeBodyPart body = mimePartList.get(0);
        validateNewDocBody(body);

        // We can't validate the PDF body since that's encoded in PDF format.
    }

    @Test
    public void legacyDocWithSigImage() throws Exception {
        // Setup and execute.
        setupStudyConsentServiceWithLegacyDoc();
        ConsentSignature sig = makeSignatureWithImage();
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SUBPOP_GUID, user, sig,
                SharingScope.NO_SHARING, studyConsentService, consentBodyTemplate);

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);

        // Validate we have 3 parts (body, PDF, and image)
        List<MimeBodyPart> mimePartList = email.getMessageParts();
        assertEquals(3, mimePartList.size());

        // Validate body.
        MimeBodyPart body = mimePartList.get(0);
        validateLegacyDocBody(body);

        // We can't validate the PDF body since that's encoded in PDF format.

        // Validate attachment. Only validate the content, because once we write the content type to the MIME part,
        // there's no way to get it back.
        MimeBodyPart attachmentPart = mimePartList.get(2);
        assertEquals(DUMMY_IMAGE_DATA, attachmentPart.getContent());
    }

    @Test
    public void newDocWithSigImage() throws Exception {
        // Setup and execute.
        setupStudyConsentServiceWithNewDoc();
        ConsentSignature sig = makeSignatureWithImage();
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SUBPOP_GUID, user, sig,
                SharingScope.NO_SHARING, studyConsentService, consentBodyTemplate);

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);

        // Validate we have 3 parts (body, PDF, and image)
        List<MimeBodyPart> mimePartList = email.getMessageParts();
        assertEquals(3, mimePartList.size());

        // Validate body.
        MimeBodyPart body = mimePartList.get(0);
        validateNewDocBody(body);

        // We can't validate the PDF body since that's encoded in PDF format.

        // Validate attachment. Only validate the content, because once we write the content type to the MIME part,
        // there's no way to get it back.
        MimeBodyPart attachmentPart = mimePartList.get(2);
        assertEquals(DUMMY_IMAGE_DATA, attachmentPart.getContent());
    }
    @Test
    public void legacyDocWithInvalidSig() throws Exception {
        // Setup and execute.
        setupStudyConsentServiceWithLegacyDoc();
        ConsentSignature sig = makeInvalidSignature();
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SUBPOP_GUID, user, sig,
                SharingScope.NO_SHARING, studyConsentService, consentBodyTemplate);

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);

        // Validate we have 2 parts (body and PDF)
        List<MimeBodyPart> mimePartList = email.getMessageParts();
        assertEquals(2, mimePartList.size());

        // Validate body.
        MimeBodyPart body = mimePartList.get(0);
        validateLegacyDocBody(body);

        // We can't validate the PDF body since that's encoded in PDF format.
    }

    @Test
    public void newDocWithInvalidSig() throws Exception {
        // Setup and execute.
        setupStudyConsentServiceWithNewDoc();
        ConsentSignature sig = makeInvalidSignature();
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SUBPOP_GUID, user, sig,
                SharingScope.NO_SHARING, studyConsentService, consentBodyTemplate);

        // Validate common elements.
        MimeTypeEmail email = provider.getMimeTypeEmail();
        validateCommonElements(email);

        // Validate we have 2 parts (body and PDF)
        List<MimeBodyPart> mimePartList = email.getMessageParts();
        assertEquals(2, mimePartList.size());

        // Validate body.
        MimeBodyPart body = mimePartList.get(0);
        validateNewDocBody(body);

        // We can't validate the PDF body since that's encoded in PDF format.
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

    private void setupStudyConsentServiceWithLegacyDoc() {
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), LEGACY_DOCUMENT);
        when(studyConsentService.getActiveConsent(SUBPOP_GUID)).thenReturn(view);
    }

    private void setupStudyConsentServiceWithNewDoc() {
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), NEW_DOCUMENT_FRAGMENT);
        when(studyConsentService.getActiveConsent(SUBPOP_GUID)).thenReturn(view);
    }

    private static void validateCommonElements(MimeTypeEmail email) {
        assertEquals("Consent Agreement for Study Name", email.getSubject());
        assertEquals("\"Study Name\" <sender@default.com>", email.getSenderAddress());
        assertEquals(Sets.newHashSet("user@user.com","consent@consent.com"),
                Sets.newHashSet(email.getRecipientAddresses()));
    }

    private static void validateLegacyDocBody(MimeBodyPart body) throws Exception {
        String bodyContent = (String) body.getContent();
        assertTrue("Name correct", bodyContent.contains("|Test Person|"));
        assertTrue("User email correct", bodyContent.contains("|user@user.com|"));
        assertTrue("Sharing correct", bodyContent.contains("|Not Sharing|"));
        assertTrue("HTML markup preserved", bodyContent.contains("<html><head></head><body>Passed through as is."));
    }

    private static void validateNewDocBody(MimeBodyPart body) throws Exception {
        String bodyContent = (String) body.getContent();
        assertTrue("Study name correct", bodyContent.contains("<title>Study Name Consent To Research</title>"));
        assertTrue("Name correct", bodyContent.contains(">Test Person<"));
        assertTrue("User email correct", bodyContent.contains(">user@user.com<"));
        assertTrue("Sharing correct", bodyContent.contains(">Not Sharing<"));
    }
}
