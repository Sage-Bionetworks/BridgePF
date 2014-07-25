package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import org.joda.time.DateTime;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

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
        
        Resource resource = new FileSystemResource("test/conf/secondstudy-consent.html");
        consent = new ResearchConsent("Test 2", DateTime.parse("1950-05-05"));
        study = new Study("Second Study", "secondstudy", null, null, null, resource);
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
        assertTrue("Date transposed to document", html.indexOf("|May 5, 1950|") > -1);
        assertTrue("Name transposed to document", html.indexOf("|Test 2|") > -1);
    }
    
}
