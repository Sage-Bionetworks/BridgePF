package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

import com.amazonaws.regions.Region;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class SendMailViaAmazonServiceTest {
    
    private static final String recipientEmail = "test2@sagebase.org";
    
    private SendMailViaAmazonService service;
    private AmazonSimpleEmailServiceClient emailClient;
    private ConsentSignature consent;
    private Study study;
    private ArgumentCaptor<SendEmailRequest> argument;

    @Before
    public void setUp() throws Exception {
        emailClient = mock(AmazonSimpleEmailServiceClient.class);
        argument = ArgumentCaptor.forClass(SendEmailRequest.class);
        
        service = new SendMailViaAmazonService();
        service.setFromEmail(recipientEmail);
        service.setEmailClient(emailClient);
        
        consent = new ConsentSignature("Test 2", "1950-05-05");
        study = new Study("Second Study", "secondstudy", 17, null, null, null, TestConstants.secondStudyConsent);
    }
    
    @Test
    public void sendConsentEmail() {
        User user = new User();
        user.setEmail(recipientEmail);
        service.sendConsentAgreement(user, consent, study);
        
        verify(emailClient).setRegion(any(Region.class));
        verify(emailClient).sendEmail(argument.capture());
        
        Destination destination = argument.getValue().getDestination();
        Message message = argument.getValue().getMessage();
        String html = message.getBody().getHtml().getData();
        
        assertEquals("Correct sender", recipientEmail, destination.getToAddresses().get(0));
        assertTrue("Contains consent content", html.startsWith("This is a test study consent."));
        assertTrue("Date transposed to document", html.indexOf("|May 5, 1950|") > -1);
        assertTrue("Name transposed to document", html.indexOf("|Test 2|") > -1);
    }
    
}
