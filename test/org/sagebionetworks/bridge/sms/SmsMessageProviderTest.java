package org.sagebionetworks.bridge.sms;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;

import com.amazonaws.services.sns.model.PublishRequest;

public class SmsMessageProviderTest {
    @Test
    public void test() throws Exception {
        // Set up dependencies
        Study study = Study.create();
        study.setName("Name");
        study.setShortName("ShortName");
        study.setIdentifier("id");
        study.setSponsorName("SponsorName");
        study.setSupportEmail("support@email.com");
        study.setTechnicalEmail("tech@email.com");
        study.setConsentNotificationEmail("consent@email.com,consent2@email.com");

        SmsTemplate template = new SmsTemplate("${studyShortName} ${url} ${supportEmail}");
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withSmsTemplate(template)
            .withToken("url", "some-url").build();

        // Check email
        PublishRequest request = provider.getSmsRequest();
        assertEquals("ShortName some-url support@email.com", request.getMessage());
        
        assertEquals(study.getShortName(),
                request.getMessageAttributes().get(BridgeConstants.SENDER_ID).getStringValue());
        assertEquals(BridgeConstants.SMS_TYPE_TRANSACTIONAL,
                request.getMessageAttributes().get(BridgeConstants.SMS_TYPE).getStringValue());
    }
    
    @Test
    public void defaultsShortNameToBridge() {
        // Set up dependencies
        Study study = Study.create();
        SmsTemplate template = new SmsTemplate("${studyShortName} ${url} ${supportEmail}");
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withSmsTemplate(template)
            .withToken("url", "some-url").build();
        PublishRequest request = provider.getSmsRequest();
        assertEquals("Bridge some-url", request.getMessage());
    }

}
