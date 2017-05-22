package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;

@RunWith(MockitoJUnitRunner.class)
public class SendMailViaAmazonServiceTest {

    private static final String EMAIL = "email@email.com";
    
    private SendMailViaAmazonService service;
    
    @Mock
    private AmazonSimpleEmailServiceClient emailClient;
    
    @Mock
    private EmailVerificationService emailVerificationService;
    
    @Mock
    private SendRawEmailResult result;
    
    @Before
    public void before() {
        service = new SendMailViaAmazonService();
        service.setSupportEmail(EMAIL);
        service.setEmailClient(emailClient);
        service.setEmailVerificationService(emailVerificationService);
    }
    
    @Test
    public void unverifiedEmailThrowsException() {
        //when(emailClient.sendRawEmail(any())).thenReturn(result);
        when(emailVerificationService.isVerified(EMAIL)).thenReturn(false);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(Study.create())
                .withRecipientEmail(EMAIL)
                .withEmailTemplate(new EmailTemplate("subject", "body", MimeType.HTML))
                .build();
        try {
            service.sendEmail(provider);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(SendMailViaAmazonService.UNVERIFIED_EMAIL_ERROR, e.getMessage());
        }
    }
    
    @Test
    public void verifiedEmailWorks() {
        when(emailClient.sendRawEmail(any())).thenReturn(result);
        when(emailVerificationService.isVerified(EMAIL)).thenReturn(true);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(Study.create())
                .withRecipientEmail(EMAIL)
                .withEmailTemplate(new EmailTemplate("subject", "body", MimeType.HTML))
                .build();
        service.sendEmail(provider);
    }
}
