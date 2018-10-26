package org.sagebionetworks.bridge.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;

import com.amazonaws.services.sns.model.PublishRequest;

public class SmsMessageProviderTest {
    @Test
    public void test() {
        // Set up dependencies
        Study study = Study.create();
        study.setName("Name");
        study.setShortName("ShortName");
        study.setIdentifier("id");
        study.setSponsorName("SponsorName");
        study.setSupportEmail("support@email.com");
        study.setTechnicalEmail("tech@email.com");
        study.setConsentNotificationEmail("consent@email.com,consent2@email.com");

        SmsTemplate template = new SmsTemplate("${studyShortName} ${url} ${supportEmail} ${expirationPeriod}");
        String expectedMessage = "ShortName some-url support@email.com 4 hours";
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withSmsTemplate(template)
            .withTransactionType()
            .withExpirationPeriod("expirationPeriod", 60*60*4) // 4 hours
            .withToken("url", "some-url").build();
        assertEquals("Transactional", provider.getSmsType());
        assertEquals(SmsType.TRANSACTIONAL, provider.getSmsTypeEnum());
        assertEquals(expectedMessage, provider.getFormattedMessage());

        // Check email
        PublishRequest request = provider.getSmsRequest();
        assertEquals(expectedMessage, request.getMessage());
        assertEquals(study.getShortName(),
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());
        assertEquals("Transactional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        
        assertEquals("some-url", provider.getTokenMap().get("url"));
        assertEquals("4 hours", provider.getTokenMap().get("expirationPeriod"));
        // BridgeUtils.studyTemplateVariables() has been called
        assertEquals("Name", provider.getTokenMap().get("studyName"));
        assertEquals("ShortName", provider.getTokenMap().get("studyShortName"));
        assertEquals("id", provider.getTokenMap().get("studyId"));
        assertEquals("SponsorName", provider.getTokenMap().get("sponsorName"));
        assertEquals("support@email.com", provider.getTokenMap().get("supportEmail"));
        assertEquals("tech@email.com", provider.getTokenMap().get("technicalEmail"));
        assertEquals("consent@email.com", provider.getTokenMap().get("consentEmail"));
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
            .withPromotionType()
            .withToken("url", "some-url").build();
        PublishRequest request = provider.getSmsRequest();
        assertEquals("Bridge some-url", request.getMessage());
    }
    
    @Test
    public void nullTokenMapEntryDoesntBreakMap() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(Study.create())
                .withPhone(TestConstants.PHONE)
                .withSmsTemplate(new SmsTemplate(""))
                .withPromotionType()
                .withToken("url", null).build();
        
        Map<String,String> tokenMap = provider.getTokenMap();
        assertNull(tokenMap.get("supportName"));
    }
    
    @Test
    public void canConstructPromotionalMessage() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(Study.create())
                .withPhone(TestConstants.PHONE)
                .withSmsTemplate(new SmsTemplate(""))
                .withPromotionType().build();
        assertEquals("Promotional", provider.getSmsType());
        assertEquals(SmsType.PROMOTIONAL, provider.getSmsTypeEnum());
    }
}
