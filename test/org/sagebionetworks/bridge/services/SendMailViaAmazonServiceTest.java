package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;

import com.amazonaws.regions.Region;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class SendMailViaAmazonServiceTest {
    
    private static final String recipientEmail = "test2@sagebase.org";
    
    private SendMailViaAmazonService service;
    private AmazonSimpleEmailServiceClient emailClient;
    private ResearchConsent consent;
    private Study study;
    private ArgumentCaptor<SendEmailRequest> argument;

    @Before
    public void setUp() throws Exception {
        emailClient = mock(AmazonSimpleEmailServiceClient.class);
        argument = ArgumentCaptor.forClass(SendEmailRequest.class);
        
        service = new SendMailViaAmazonService();
        service.setFromEmail(recipientEmail);
        service.setEmailClient(emailClient);
        
        consent = new ResearchConsent("Test 2", "1950-05-05");
        study = new Study("Second Study", "secondstudy", 17, null, null, null, TestConstants.secondStudyConsent);
    }
    
    @Test
    public void sendConsentEmail() {
        service.sendConsentAgreement(recipientEmail, consent, study);
        
        verify(emailClient).setRegion(any(Region.class));
        verify(emailClient).sendEmail(argument.capture());
        
        Destination destination = argument.getValue().getDestination();
        Message message = argument.getValue().getMessage();
        String html = message.getBody().getHtml().getData();
        
        assertEquals("Correct sender", recipientEmail, destination.getToAddresses().get(0));
        assertTrue("Contains consent content", html.startsWith("This is a test study consent."));
        assertTrue("Date transposed to document", html.indexOf("|1950-05-05|") > -1);
        assertTrue("Name transposed to document", html.indexOf("|Test 2|") > -1);
    }
    
}
