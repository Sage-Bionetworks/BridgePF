package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;

@RunWith(MockitoJUnitRunner.class)
public class SendMailViaAmazonServiceTest {

    private static final String SUPPORT_EMAIL = "email@email.com";
    private static final String RECIPIENT_EMAIL = "recipient@recipient.com";
    
    private SendMailViaAmazonService service;
    
    private Study study;
    
    @Mock
    private AmazonSimpleEmailServiceClient emailClient;
    
    @Mock
    private EmailVerificationService emailVerificationService;
    
    @Mock
    private SendRawEmailResult result;
    
    @Before
    public void before() {
        study = Study.create();
        study.setName("Name");
        study.setSupportEmail(SUPPORT_EMAIL);
        
        service = new SendMailViaAmazonService();
        service.setEmailClient(emailClient);
        service.setEmailVerificationService(emailVerificationService);
    }
    
    @Test
    public void unverifiedEmailThrowsException() {
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(false);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withRecipientEmail(RECIPIENT_EMAIL)
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
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(true);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withRecipientEmail(RECIPIENT_EMAIL)
                .withEmailTemplate(new EmailTemplate("subject", "body", MimeType.HTML))
                .build();
        service.sendEmail(provider);
    }
}
