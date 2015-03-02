package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

import com.amazonaws.regions.Region;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;

/**
 * Set-up here for consent-specific tests is extensive, so tests for the participant roster
 * are in a separate class.
 */
public class SendMailViaAmazonServiceConsentTest {

    private SendMailViaAmazonService service;
    private AmazonSimpleEmailServiceClient emailClient;
    private StudyService studyService;
    private StudyConsent studyConsent;
    private ArgumentCaptor<SendRawEmailRequest> argument;
    private Study study;
    private static final String FROM_STUDY_AS_FORMATTED = "Test Study (Sage) <study-support-email@study.com>";
    private static final String FROM_DEFAULT_AS_FORMATTED = "Sage Bionetworks <test-sender@sagebase.org>";
    
    @Before
    public void setUp() throws Exception {
        study = new DynamoStudy();
        study.setName("Test Study (Sage)");
        study.setIdentifier("api");
        study.setSupportEmail("study-support-email@study.com");
        study.setMinAgeOfConsent(17);

        studyService = mock(StudyService.class);
        when(studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);
        emailClient = mock(AmazonSimpleEmailServiceClient.class);
        when(emailClient.sendRawEmail(notNull(SendRawEmailRequest.class))).thenReturn(new SendRawEmailResult()
                .withMessageId("test message id"));
        argument = ArgumentCaptor.forClass(SendRawEmailRequest.class);

        service = new SendMailViaAmazonService();
        service.setSupportEmail(FROM_DEFAULT_AS_FORMATTED);
        service.setEmailClient(emailClient);
        service.setStudyService(studyService);

        studyConsent = new StudyConsent() {
            @Override
            public String getStudyKey() {
                return TestConstants.TEST_STUDY_IDENTIFIER;
            }
            @Override
            public long getCreatedOn() {
                return 0;
            }
            @Override
            public boolean getActive() {
                return true;
            }
            @Override
            public String getPath() {
                return "conf/email-templates/api-consent.html";
            }
            @Override
            public int getMinAge() {
                return 17;
            }
        };
    }
    
    @Test
    public void whenNoStudySupportEmailUsesDefaultSupportEmail() {
        study.setSupportEmail(""); // just a blank string, tricky
        
        ConsentSignature consent = ConsentSignature.create("Test 2", "1950-05-05", null, null);
        User user = new User();
        user.setEmail("test-user@sagebase.org");
        service.sendConsentAgreement(user, consent, studyConsent);
        
        verify(emailClient).sendRawEmail(argument.capture());
        SendRawEmailRequest req = argument.getValue();
        assertEquals("Correct sender", FROM_DEFAULT_AS_FORMATTED, req.getSource());
    }
    
    @Test
    public void sendConsentEmail() {
        ConsentSignature consent = ConsentSignature.create("Test 2", "1950-05-05", null, null);
        User user = new User();
        user.setEmail("test-user@sagebase.org");
        service.sendConsentAgreement(user, consent, studyConsent);

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
        assertTrue("Email transposed to document", rawMessage.contains(user.getEmail()));
    }

    @Test
    public void sendConsentEmailWithSignatureImage() {
        ConsentSignature consent = ConsentSignature.create("Eggplant McTester", "1970-05-01",
                TestConstants.DUMMY_IMAGE_DATA, "image/fake");
        User user = new User();
        user.setEmail("test-user@sagebase.org");
        service.sendConsentAgreement(user, consent, studyConsent);

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
        assertTrue("Email transposed to document", rawMessage.contains(user.getEmail()));

        // Validate message contains signature image. To avoid coupling too closely with MIME implementation, just
        // validate that our content type shows up and that we contain the first few chars of the image data.
        assertTrue("Contains signature image MIME type", rawMessage.contains("image/fake"));
        assertTrue("Contains signature image data", rawMessage.contains(TestConstants.DUMMY_IMAGE_DATA.substring(
                0, 10)));
    }
}
