package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.dao.SmsOptOutSettingsDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.sms.IncomingSms;

public class SmsServiceTest {
    private static final String MESSAGE_HELP = "HELP";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final String MESSAGE_START = "START";
    private static final String MESSAGE_STOP = "STOP";
    private static final String PHONE_NUMBER = "+12065550123";
    private static final long SENT_ON = 1539732997760L;
    private static final String STUDY_SHORT_NAME = "My Study";
    private static final String USER_ID = "test-user";

    private static final Phone PHONE = new Phone(PHONE_NUMBER, "US");
    private static final StudyParticipant PARTICIPANT_WITH_NO_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .build();
    private static final StudyParticipant PARTICIPANT_WITH_PHONE = new StudyParticipant.Builder().withId(USER_ID)
            .withPhone(PHONE).build();

    private static final Study STUDY;
    static {
        STUDY = Study.create();
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        STUDY.setShortName(STUDY_SHORT_NAME);
    }

    private SmsMessageDao mockMessageDao;
    private SmsOptOutSettingsDao mockOptOutSettingsDao;
    private ParticipantService mockParticipantService;
    private SmsService svc;

    @Before
    public void before() {
        // Mock study service. This is only used to get the study short name.
        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(STUDY);

        // Mock other DAOs and services.
        mockMessageDao = mock(SmsMessageDao.class);
        mockOptOutSettingsDao = mock(SmsOptOutSettingsDao.class);
        mockParticipantService = mock(ParticipantService.class);

        // Set up service.
        svc = new SmsService();
        svc.setMessageDao(mockMessageDao);
        svc.setOptOutSettingsDao(mockOptOutSettingsDao);
        svc.setParticipantService(mockParticipantService);
        svc.setStudyService(mockStudyService);
    }

    @Test(expected = InvalidEntityException.class)
    public void handleIncomingSmsValidatesInput() {
        svc.handleIncomingSms(new IncomingSms());
    }

    @Test
    public void handleIncomingSms_NoSentMessages() {
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(null);
        String response = svc.handleIncomingSms(makeIncomingSms(MESSAGE_HELP));
        assertNull(response);
        verify(mockOptOutSettingsDao, never()).setOptOutSettings(any());
    }

    @Test
    public void handleIncomingSms_OptOutFromPromotionalMessages() {
        // Mock DAOs.
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(makeSmsMessage(SmsType.PROMOTIONAL));

        SmsOptOutSettings optOutSettings = makeValidOptOutSettings();
        optOutSettings.getPromotionalOptOuts().put("other_study", false);
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(optOutSettings);

        // Execute and validate. We set the global promotion opt-out and clear any per-study overrides.
        String response = svc.handleIncomingSms(makeIncomingSms(MESSAGE_STOP));
        assertTrue(response.contains("You have opted out of notifications and subscriptions"));

        verify(mockOptOutSettingsDao).setOptOutSettings(same(optOutSettings));
        assertTrue(optOutSettings.getGlobalPromotionalOptOut());
        assertTrue(optOutSettings.getPromotionalOptOuts().isEmpty());
    }

    @Test
    public void handleIncomingSms_OptOutFromTransactionalMessages() {
        // Mock DAOs.
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(makeSmsMessage(SmsType.TRANSACTIONAL));

        SmsOptOutSettings optOutSettings = makeValidOptOutSettings();
        optOutSettings.getPromotionalOptOuts().put("other_study", false);
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(optOutSettings);

        // Execute and validate. We set the global promotion opt-out and clear any per-study overrides. We also set the
        // transactional opt-out for the stsudy.
        String response = svc.handleIncomingSms(makeIncomingSms(MESSAGE_STOP));
        assertTrue(response.contains("You have opted out of account management messages"));

        verify(mockOptOutSettingsDao).setOptOutSettings(same(optOutSettings));
        assertTrue(optOutSettings.getGlobalPromotionalOptOut());
        assertTrue(optOutSettings.getPromotionalOptOuts().isEmpty());
        assertTrue(optOutSettings.getTransactionalOptOutForStudy(TestConstants.TEST_STUDY_IDENTIFIER));
    }

    @Test
    public void handleIncomingSms_CreateNewOptOutSettings() {
        // Mock DAOs.
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(makeSmsMessage(SmsType.PROMOTIONAL));
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(null);

        // Execute and validate. This test just tests that we created the new opt-out settings.
        svc.handleIncomingSms(makeIncomingSms(MESSAGE_STOP));

        ArgumentCaptor<SmsOptOutSettings> savedOptOutSettingsCaptor = ArgumentCaptor.forClass(SmsOptOutSettings.class);
        verify(mockOptOutSettingsDao).setOptOutSettings(savedOptOutSettingsCaptor.capture());
        assertEquals(PHONE_NUMBER, savedOptOutSettingsCaptor.getValue().getNumber());
    }

    @Test
    public void handleIncomingSms_OptInWithNoOptOuts() {
        // Mock DAOs.
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(makeSmsMessage(SmsType.PROMOTIONAL));
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(null);

        // Execute and validate. Since the user was never opted out, we do nothing.
        String response = svc.handleIncomingSms(makeIncomingSms(MESSAGE_START));
        assertTrue(response.contains("You have opted into messages"));

        verify(mockOptOutSettingsDao, never()).setOptOutSettings(any());
    }

    @Test
    public void handleIncomingSms_OptInAfterOptOut() {
        // Mock DAOs.
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(makeSmsMessage(SmsType.PROMOTIONAL));

        SmsOptOutSettings optOutSettings = makeValidOptOutSettings();
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(optOutSettings);

        // Execute and validate. Since the user was never opted out, we do nothing.
        String response = svc.handleIncomingSms(makeIncomingSms(MESSAGE_START));
        assertTrue(response.contains("You have opted into messages"));

        verify(mockOptOutSettingsDao).setOptOutSettings(same(optOutSettings));
        assertFalse(optOutSettings.getPromotionalOptOutForStudy(TestConstants.TEST_STUDY_IDENTIFIER));
        assertFalse(optOutSettings.getTransactionalOptOutForStudy(TestConstants.TEST_STUDY_IDENTIFIER));
    }

    @Test
    public void handleIncomingSmS_InfoMessage() {
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(makeSmsMessage(SmsType.PROMOTIONAL));
        String response = svc.handleIncomingSms(makeIncomingSms(MESSAGE_HELP));
        assertTrue(response.contains("This channel sends account management messages, notifications, and subscriptions"));
    }

    private static IncomingSms makeIncomingSms(String message) {
        IncomingSms incomingSms = new IncomingSms();
        incomingSms.setMessageId(MESSAGE_ID);
        incomingSms.setBody(message);
        incomingSms.setSenderNumber(PHONE_NUMBER);
        return incomingSms;
    }

    private static SmsMessage makeSmsMessage(SmsType type) {
        SmsMessage message = makeValidSmsMessage();
        message.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        message.setSmsType(type);
        return message;
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
        when(mockParticipantService.getParticipant(STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.getMostRecentMessage(STUDY, USER_ID);
    }

    @Test
    public void getMostRecentMessage_ParticipantWithPhone() {
        // Mock dependencies.
        SmsMessage daoOutput = makeValidSmsMessage();
        when(mockMessageDao.getMostRecentMessage(PHONE_NUMBER)).thenReturn(daoOutput);

        when(mockParticipantService.getParticipant(STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        SmsMessage svcOutput = svc.getMostRecentMessage(STUDY, USER_ID);
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
        when(mockParticipantService.getParticipant(STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.getOptOutSettings(STUDY, USER_ID);
    }

    @Test
    public void getOptOutSettings_ParticipantWithPhone() {
        // Mock dependencies.
        SmsOptOutSettings daoOutput = makeValidOptOutSettings();
        when(mockOptOutSettingsDao.getOptOutSettings(PHONE_NUMBER)).thenReturn(daoOutput);

        when(mockParticipantService.getParticipant(STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        SmsOptOutSettings svcOutput = svc.getOptOutSettings(STUDY, USER_ID);
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
        when(mockParticipantService.getParticipant(STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_NO_PHONE);
        svc.setOptOutSettings(STUDY, USER_ID, makeValidOptOutSettings());
    }

    @Test
    public void setOptOutSettings_ParticipantWithPhone() {
        // Mock participant service.
        when(mockParticipantService.getParticipant(STUDY, USER_ID, false)).thenReturn(
                PARTICIPANT_WITH_PHONE);

        // Input SMS settings has a different phone number, just to make sure SmsService sets it.
        SmsOptOutSettings svcInput = makeValidOptOutSettings();
        svcInput.setNumber("wrong number");

        // Execute and verify.
        svc.setOptOutSettings(STUDY, USER_ID, svcInput);

        verify(mockOptOutSettingsDao).setOptOutSettings(same(svcInput));
        assertEquals(PHONE_NUMBER, svcInput.getNumber());
    }

    private static SmsMessage makeValidSmsMessage() {
        SmsMessage message = SmsMessage.create();
        message.setNumber(PHONE_NUMBER);
        message.setSentOn(SENT_ON);
        message.setMessageId(MESSAGE_ID);
        message.setMessageBody(MESSAGE_BODY);
        message.setSmsType(SmsType.PROMOTIONAL);
        message.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        return message;
    }

    private static SmsOptOutSettings makeValidOptOutSettings() {
        SmsOptOutSettings settings = SmsOptOutSettings.create();
        settings.setNumber(PHONE_NUMBER);
        return settings;
    }
}
