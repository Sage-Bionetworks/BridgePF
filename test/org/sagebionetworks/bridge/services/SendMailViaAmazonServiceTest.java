package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

import com.amazonaws.regions.Region;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class SendMailViaAmazonServiceTest {
    
    private static final String recipientEmail = "test2@sagebase.org";
    
    private SendMailViaAmazonService service;
    private AmazonSimpleEmailServiceClient emailClient;
    private StudyService studyService;
    private ConsentSignature consent;
    private StudyConsent studyConsent;
    private ArgumentCaptor<SendEmailRequest> argument;

    @Before
    public void setUp() throws Exception {
        Study study = new Study(null, "api", 17, null, null, null, null);
        
        studyService = mock(StudyService.class);
        when(studyService.getStudyByIdentifier(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);
        emailClient = mock(AmazonSimpleEmailServiceClient.class);
        argument = ArgumentCaptor.forClass(SendEmailRequest.class);
        
        service = new SendMailViaAmazonService();
        service.setFromEmail(recipientEmail);
        service.setEmailClient(emailClient);
        service.setStudyService(studyService);
        
        consent = new ConsentSignature("Test 2", "1950-05-05");
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
    public void sendConsentEmail() {
        User user = new User();
        user.setEmail(recipientEmail);
        service.sendConsentAgreement(user, consent, studyConsent);
        
        verify(emailClient).setRegion(any(Region.class));
        verify(emailClient).sendEmail(argument.capture());
        
        Destination destination = argument.getValue().getDestination();
        Message message = argument.getValue().getMessage();
        String html = message.getBody().getHtml().getData();
        
        assertEquals("Correct sender", recipientEmail, destination.getToAddresses().get(0));
        assertTrue("Contains consent content", html.indexOf("Had this been a real study") > -1);
        assertTrue("Date transposed to document", html.indexOf("May 5, 1950") > -1);
        assertTrue("Name transposed to document", html.indexOf("Test 2") > -1);
    }
    
}
