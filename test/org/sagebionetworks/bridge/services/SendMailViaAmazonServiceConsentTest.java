package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpleemail.model.MessageRejectedException;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.ConsentEmailProvider;

import com.amazonaws.regions.Region;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;

/**
 * Set-up here for consent-specific tests is extensive, so tests for the participant roster
 * are in a separate class.
 */
@SuppressWarnings("unchecked")
public class SendMailViaAmazonServiceConsentTest {
    private static final String FROM_STUDY_AS_FORMATTED = "\"Test Study (Sage)\" <study-support-email@study.com>";
    private static final String FROM_DEFAULT_UNFORMATTED = "Sage Bionetworks <test-sender@sagebase.org>";

    private SendMailViaAmazonService service;
    private AmazonSimpleEmailServiceClient emailClient;
    private StudyService studyService;
    private StudyConsentService studyConsentService;
    private ArgumentCaptor<SendRawEmailRequest> argument;
    private Study study;
    private String consentBodyTemplate;
    
    @Before
    public void setUp() throws Exception {
        consentBodyTemplate = IOUtils.toString(new FileInputStream("conf/study-defaults/consent-page.xhtml"));
        
        study = new DynamoStudy(); // TestUtils.getValidStudy();
        study.setName("Test Study (Sage)");
        study.setIdentifier("api");
        study.setSupportEmail("study-support-email@study.com");

        studyService = mock(StudyService.class);
        when(studyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        emailClient = mock(AmazonSimpleEmailServiceClient.class);
        argument = ArgumentCaptor.forClass(SendRawEmailRequest.class);

        service = new SendMailViaAmazonService();
        service.setSupportEmail(FROM_DEFAULT_UNFORMATTED);
        service.setEmailClient(emailClient);
        
        StudyConsentView view = new StudyConsentView(mock(StudyConsent.class), 
            "<document>Had this been a real study: @@name@@ @@signing.date@@ @@email@@ @@sharing@@</document>");
        
        studyConsentService = mock(StudyConsentService.class);
        when(studyConsentService.getActiveConsent(SubpopulationGuid.create("api"))).thenReturn(view);
    }

    @Test
    public void sendConsentEmail() {
        // mock email client with success
        when(emailClient.sendRawEmail(notNull(SendRawEmailRequest.class))).thenReturn(
                new SendRawEmailResult().withMessageId("test message id"));

        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Test 2").withBirthdate("1950-05-05")
                .withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
        
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SubpopulationGuid.create(study.getIdentifier()),
                "test-user@sagebase.org", consent, SharingScope.SPONSORS_AND_PARTNERS, studyConsentService,
                consentBodyTemplate);
        service.sendEmail(provider);

        verify(emailClient).setRegion(any(Region.class));
        verify(emailClient).sendRawEmail(argument.capture());

        // validate from
        SendRawEmailRequest req = argument.getValue();
        assertEquals("Correct sender", FROM_STUDY_AS_FORMATTED, req.getSource());

        // validate to
        List<String> toList = req.getDestinations();
        assertEquals("Correct number of recipients", 1, toList.size());
        assertEquals("Correct recipient", "test-user@sagebase.org", toList.get(0));

        // Validate message content. MIME message must be ASCII
        String rawMessage = new String(req.getRawMessage().getData().array(), Charsets.US_ASCII);
        assertTrue("Contains consent content", rawMessage.contains("Had this been a real study"));
        assertTrue("Name transposed to document", rawMessage.contains("Test 2"));
        assertTrue("Email transposed to document", rawMessage.contains("test-user@sagebase.org"));
        assertTrue("Has the PDF consent document", rawMessage.contains("pdf"));
        assertTrue("Has sharing option", rawMessage.contains("Sponsors and Partners"));
    }

    @Test
    public void sendConsentEmailWithSignatureImage() {
        // mock email client with success
        when(emailClient.sendRawEmail(notNull(SendRawEmailRequest.class))).thenReturn(
                new SendRawEmailResult().withMessageId("test message id"));

        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Eggplant McTester")
                .withBirthdate("1970-05-01").withImageData(TestConstants.DUMMY_IMAGE_DATA)
                .withImageMimeType("image/fake").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
        
        ConsentEmailProvider provider = new ConsentEmailProvider(study, SubpopulationGuid.create(study.getIdentifier()),
                "test-user@sagebase.org", consent, SharingScope.SPONSORS_AND_PARTNERS, studyConsentService,
                consentBodyTemplate);
        service.sendEmail(provider);

        verify(emailClient).setRegion(any(Region.class));
        verify(emailClient).sendRawEmail(argument.capture());

        // validate from
        SendRawEmailRequest req = argument.getValue();
        assertEquals("Correct sender", FROM_STUDY_AS_FORMATTED, req.getSource());

        // validate to
        List<String> toList = req.getDestinations();
        assertEquals("Correct number of recipients", 1, toList.size());
        assertEquals("Correct recipient", "test-user@sagebase.org", toList.get(0));

        // Validate message content. MIME message must be ASCII
        String rawMessage = new String(req.getRawMessage().getData().array(), Charsets.US_ASCII);
        assertTrue("Contains consent content", rawMessage.contains("Had this been a real study"));
        assertTrue("Name transposed to document", rawMessage.contains("Eggplant McTester"));
        assertTrue("Email transposed to document", rawMessage.contains("test-user@sagebase.org"));

        // Validate message contains signature image. To avoid coupling too closely with MIME implementation, just
        // validate that our content type shows up and that we contain the first few chars of the image data.
        assertTrue("Contains signature image MIME type", rawMessage.contains("image/fake"));
        assertTrue("Contains signature image data", rawMessage.contains(TestConstants.DUMMY_IMAGE_DATA.substring(
                0, 10)));
    }

    @Test
    public void messageRejectedNotPropagated() {
        // mock email client with exception
        when(emailClient.sendRawEmail(notNull(SendRawEmailRequest.class))).thenThrow(MessageRejectedException.class);

        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Test 2").withBirthdate("1950-05-05")
                .withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();

        ConsentEmailProvider provider = new ConsentEmailProvider(study, SubpopulationGuid.create(study.getIdentifier()),
                "test-user@sagebase.org", consent, SharingScope.SPONSORS_AND_PARTNERS, studyConsentService,
                consentBodyTemplate);

        // execute
        service.sendEmail(provider);

        // Verify email client was called. No need to test anything else, everything else is already tested in the
        // normal case above.
        verify(emailClient).sendRawEmail(any());
    }

    @Test(expected = BridgeServiceException.class)
    public void otherExceptionsPropagated() {
        // mock email client with exception
        when(emailClient.sendRawEmail(notNull(SendRawEmailRequest.class))).thenThrow(AmazonServiceException.class);

        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Test 2").withBirthdate("1950-05-05")
                .withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();

        ConsentEmailProvider provider = new ConsentEmailProvider(study, SubpopulationGuid.create(study.getIdentifier()),
                "test-user@sagebase.org", consent, SharingScope.SPONSORS_AND_PARTNERS, studyConsentService,
                consentBodyTemplate);

        // execute
        service.sendEmail(provider);
    }
}