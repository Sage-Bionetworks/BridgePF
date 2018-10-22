package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.dao.SmsOptOutSettingsDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.sms.SmsServiceProvider;
import org.sagebionetworks.bridge.sms.TwilioHelper;
import org.sagebionetworks.bridge.time.DateUtils;

public class SmsServiceTest {
    private static final String EXPECTED_TWILIO_MESSAGE_BODY = "My Study: lorem ipsum Text STOP to unsubscribe.";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final long MOCK_NOW_MILLIS = DateUtils.convertToMillisFromEpoch("2018-10-17T16:21:52.749Z");
    private static final String PHONE_NUMBER = "+12065550123";
    private static final long SENT_ON = 1539732997760L;
    private static final String STUDY_SHORT_NAME = "My Study";
    private static final Phone UK_PHONE = new Phone("+447911123456", "UK");
    private static final String USER_ID = "test-user";

    private static final Phone PHONE = new Phone(PHONE_NUMBER, "US");
    private static final StudyParticipant PARTICIPANT_WITH_NO_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .build();
    private static final StudyParticipant PARTICIPANT_WITH_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .withPhone(PHONE).build();

    private SmsMessageDao mockMessageDao;
    private SmsOptOutSettingsDao mockOptOutSettingsDao;
    private ParticipantService mockParticipantService;
    private AmazonSNSClient mockSnsClient;
    private TwilioHelper mockTwilioHelper;
    private Study study;
    private SmsService svc;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @Before
    public void before() {
        // Mock SMS providers.
        mockSnsClient = mock(AmazonSNSClient.class);
        when(mockSnsClient.publish(any())).thenReturn(new PublishResult().withMessageId(MESSAGE_ID));

        mockTwilioHelper = mock(TwilioHelper.class);
        when(mockTwilioHelper.sendSms(any(), any())).thenReturn(MESSAGE_ID);

        // Mock study service. This is only used to get the study short name.
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setShortName(STUDY_SHORT_NAME);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);

        // Mock other DAOs and services.
        mockMessageDao = mock(SmsMessageDao.class);
        mockOptOutSettingsDao = mock(SmsOptOutSettingsDao.class);
        mockParticipantService = mock(ParticipantService.class);

        // Set up service.
        svc = new SmsService();
        svc.setMessageDao(mockMessageDao);
        svc.setOptOutSettingsDao(mockOptOutSettingsDao);
        svc.setParticipantService(mockParticipantService);
        svc.setSnsClient(mockSnsClient);
        svc.setStudyService(mockStudyService);
        svc.setTwilioHelper(mockTwilioHelper);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void sendTransactionalSMSMessageOK() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(MESSAGE_BODY, request.getMessage());
        assertEquals("Transactional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals(STUDY_SHORT_NAME,
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());

        // We log the SMS message.
        verifyLoggedSmsMessage(MESSAGE_BODY, SmsType.TRANSACTIONAL);

        // We don't call Twilio.
        verify(mockTwilioHelper, never()).sendSms(any(), any());
    }

    @Test
    public void sendPromotionalSMSMessageOK() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(MESSAGE_BODY, request.getMessage());
        assertEquals("Promotional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals(STUDY_SHORT_NAME,
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());

        // We log the SMS message.
        verifyLoggedSmsMessage(MESSAGE_BODY, SmsType.PROMOTIONAL);

        // We don't call Twilio.
        verify(mockTwilioHelper, never()).sendSms(any(), any());
    }

    @Test(expected = BridgeServiceException.class)
    public void sendSMSMessageTooLongInvalid() {
        String message = "This is my SMS message.";
        for (int i=0; i < 5; i++) {
            message += message;
        }
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(message))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(provider);
    }

    @Test
    public void sendSmsViaTwilio() {
        enableTwilio();

        // Set up input.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);

        // Verify Twilio call.
        verify(mockTwilioHelper).sendSms(TestConstants.PHONE, EXPECTED_TWILIO_MESSAGE_BODY);

        // We log the SMS message.
        verifyLoggedSmsMessage(EXPECTED_TWILIO_MESSAGE_BODY, SmsType.PROMOTIONAL);

        // We don't call SNS.
        verify(mockSnsClient, never()).publish(any());
    }

    @Test
    public void optedOutOfPromotional() {
        enableTwilio();
        setupPromotionalOptOut();

        // Set up input.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);

        // We don't send or log any messages.
        verify(mockSnsClient, never()).publish(any());
        verify(mockTwilioHelper, never()).sendSms(any(), any());
        verify(mockMessageDao, never()).logMessage(any());
    }

    @Test
    public void promotionalOptOutDoesntStopTransactional() {
        enableTwilio();
        setupPromotionalOptOut();

        // Set up input.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);

        // Verify Twilio call.
        verify(mockTwilioHelper).sendSms(TestConstants.PHONE, EXPECTED_TWILIO_MESSAGE_BODY);

        // SMS send is verified in detail in other tests.
    }

    @Test
    public void optedOutOfTransactional() {
        enableTwilio();
        setupTransactionalOptOut();

        // Set up input.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);

        // We don't send or log any messages.
        verify(mockSnsClient, never()).publish(any());
        verify(mockTwilioHelper, never()).sendSms(any(), any());
        verify(mockMessageDao, never()).logMessage(any());
    }

    @Test
    public void transactionalOptOutDoesntStopPromotional() {
        enableTwilio();
        setupTransactionalOptOut();

        // Set up input.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);

        // Verify Twilio call.
        verify(mockTwilioHelper).sendSms(TestConstants.PHONE, EXPECTED_TWILIO_MESSAGE_BODY);

        // SMS send is verified in detail in other tests.
    }

    @Test(expected = BadRequestException.class)
    public void cantSendToNonUSPhone() {
        enableTwilio();

        // Set up input.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(UK_PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);
    }

    @Test
    public void twilioDoesntAddStudyNameOrOptOutTextTwice() {
        enableTwilio();

        // Set up input.
        String customMessageBody = "Custom message from My Study. Text STOP to unsubscribe. lorem ipsum";
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(customMessageBody))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        // Execute.
        svc.sendSmsMessage(provider);

        // Verify Twilio call.
        verify(mockTwilioHelper).sendSms(TestConstants.PHONE, customMessageBody);

        // SMS send is verified in detail in other tests.
    }

    private void enableTwilio() {
        study.setSmsServiceProvider(SmsServiceProvider.TWILIO);
    }

    private void setupPromotionalOptOut() {
        SmsOptOutSettings optOutSettings = SmsOptOutSettings.create();
        optOutSettings.getPromotionalOptOuts().put(TestConstants.TEST_STUDY_IDENTIFIER, true);
        when(mockOptOutSettingsDao.getOptOutSettings(TestConstants.PHONE.getNumber())).thenReturn(optOutSettings);
    }

    private void setupTransactionalOptOut() {
        SmsOptOutSettings optOutSettings = SmsOptOutSettings.create();
        optOutSettings.getTransactionalOptOuts().put(TestConstants.TEST_STUDY_IDENTIFIER, true);
        when(mockOptOutSettingsDao.getOptOutSettings(TestConstants.PHONE.getNumber())).thenReturn(optOutSettings);
    }

    private void verifyLoggedSmsMessage(String expectedMessage, SmsType expectedSmsType) {
        ArgumentCaptor<SmsMessage> loggedMessageCaptor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(mockMessageDao).logMessage(loggedMessageCaptor.capture());

        SmsMessage loggedMessage = loggedMessageCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), loggedMessage.getPhoneNumber());
        assertEquals(MOCK_NOW_MILLIS, loggedMessage.getSentOn());
        assertEquals(expectedMessage, loggedMessage.getMessageBody());
        assertEquals(MESSAGE_ID, loggedMessage.getMessageId());
        assertEquals(expectedSmsType, loggedMessage.getSmsType());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, loggedMessage.getStudyId());
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_NullPhoneNumber() {
        svc.getMostRecentMessage(null);
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_EmptyPhoneNumber() {
        svc.getMostRecentMessage("");
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_BlankPhoneNumber() {
        svc.getMostRecentMessage("   ");
    }

    @Test
    public void getMostRecentMessage_NormalCase() {
        SmsMessage daoOutput = makeValidSmsMessage();
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(daoOutput);

        SmsMessage svcOutput = svc.getMostRecentMessage(PHONE_NUMBER);
        assertSame(daoOutput, svcOutput);
    }

    @Test(expected = BadRequestException.class)
    public void getMostRecentMessage_ParticipantWithNoPhone() {
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.getMostRecentMessage(study, USER_ID);
    }

    @Test
    public void getMostRecentMessage_ParticipantWithPhone() {
        // Mock dependencies.
        SmsMessage daoOutput = makeValidSmsMessage();
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(daoOutput);

        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        SmsMessage svcOutput = svc.getMostRecentMessage(study, USER_ID);
        assertSame(daoOutput, svcOutput);
    }

    @Test(expected = BadRequestException.class)
    public void logMessage_NullMessage() {
        svc.logMessage(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void logMessage_InvalidMessage() {
        svc.logMessage(SmsMessage.create());
    }

    @Test
    public void logMessage_NormalCase() {
        // Execute.
        SmsMessage svcInput = makeValidSmsMessage();
        svc.logMessage(svcInput);

        // Verify DAO.
        verify(mockMessageDao).logMessage(same(svcInput));
    }

    @Test(expected = BadRequestException.class)
    public void getOptOutSettings_NullNumber() {
        svc.getOptOutSettings(null);
    }

    @Test(expected = BadRequestException.class)
    public void getOptOutSettings_EmptyNumber() {
        svc.getOptOutSettings("");
    }

    @Test(expected = BadRequestException.class)
    public void getOptOutSettings_BlankNumber() {
        svc.getOptOutSettings("   ");
    }

    @Test
    public void getOptOutSettings_NormalCase() {
        SmsOptOutSettings daoOutput = makeValidOptOutSettings();
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(daoOutput);

        SmsOptOutSettings svcOuptut = svc.getOptOutSettings(PHONE_NUMBER);
        assertSame(daoOutput, svcOuptut);
    }

    @Test(expected = BadRequestException.class)
    public void getOptOutSettings_ParticipantWithNoPhone() {
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.getOptOutSettings(study, USER_ID);
    }

    @Test
    public void getOptOutSettings_ParticipantWithPhone() {
        // Mock dependencies.
        SmsOptOutSettings daoOutput = makeValidOptOutSettings();
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(daoOutput);

        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        SmsOptOutSettings svcOutput = svc.getOptOutSettings(study, USER_ID);
        assertSame(daoOutput, svcOutput);
    }

    @Test(expected = BadRequestException.class)
    public void setOptOutSettings_NullSettings() {
        svc.setOptOutSettings(null);
    }

    @Test(expected = InvalidEntityException.class)
    public void setOptOutSettings_InvalidSettings() {
        svc.setOptOutSettings(SmsOptOutSettings.create());
    }

    @Test
    public void setOptOutSettings_NormalCase() {
        // Execute.
        SmsOptOutSettings svcInput = makeValidOptOutSettings();
        svc.setOptOutSettings(svcInput);

        // Verify DAO.
        verify(mockOptOutSettingsDao).setOptOutSettings(same(svcInput));
    }

    @Test(expected = BadRequestException.class)
    public void setOptOutSettings_ParticipantWithNoPhone() {
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.setOptOutSettings(study, USER_ID, makeValidOptOutSettings());
    }

    @Test
    public void setOptOutSettings_ParticipantWithPhone() {
        // Mock participant service.
        when(mockParticipantService.getParticipant(study, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        // Input SMS settings has a different phone number, just to make sure SmsService sets it.
        SmsOptOutSettings svcInput = makeValidOptOutSettings();
        svcInput.setPhoneNumber("wrong number");

        // Execute and verify.
        svc.setOptOutSettings(study, USER_ID, svcInput);

        verify(mockOptOutSettingsDao).setOptOutSettings(same(svcInput));
        assertEquals(PHONE_NUMBER, svcInput.getPhoneNumber());
    }

    private static SmsMessage makeValidSmsMessage() {
        SmsMessage message = SmsMessage.create();
        message.setPhoneNumber(PHONE_NUMBER);
        message.setSentOn(SENT_ON);
        message.setMessageId(MESSAGE_ID);
        message.setMessageBody(MESSAGE_BODY);
        message.setSmsType(SmsType.PROMOTIONAL);
        message.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        return message;
    }

    private static SmsOptOutSettings makeValidOptOutSettings() {
        SmsOptOutSettings settings = SmsOptOutSettings.create();
        settings.setPhoneNumber(PHONE_NUMBER);
        return settings;
    }
}
