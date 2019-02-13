package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CheckIfPhoneNumberIsOptedOutRequest;
import com.amazonaws.services.sns.model.CheckIfPhoneNumberIsOptedOutResult;
import com.amazonaws.services.sns.model.OptInPhoneNumberRequest;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.time.DateUtils;

public class SmsServiceTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final long MOCK_NOW_MILLIS = DateUtils.convertToMillisFromEpoch("2018-10-17T16:21:52.749Z");
    private static final String PHONE_NUMBER = "+12065550123";
    private static final long SENT_ON = 1539732997760L;
    private static final String STUDY_SHORT_NAME = "My Study";
    private static final DateTimeZone TIME_ZONE = DateTimeZone.forOffsetHours(-7);
    private static final String USER_ID = "test-user";

    private static final StudyParticipant PARTICIPANT_WITH_TIME_ZONE = new StudyParticipant.Builder()
            .withId(USER_ID).withHealthCode(HEALTH_CODE).withTimeZone(TIME_ZONE).build();
    private static final StudyParticipant PARTICIPANT_WITHOUT_TIME_ZONE = new StudyParticipant.Builder()
            .withId(USER_ID).withHealthCode(HEALTH_CODE).build();

    private HealthDataService mockHealthDataService;
    private SmsMessageDao mockMessageDao;
    private ParticipantService mockParticipantService;
    private UploadSchemaService mockSchemaService;
    private AmazonSNSClient mockSnsClient;
    private Study study;
    private SmsService svc;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @Before
    public void before() {
        // Mock schema service to return dummy schema for message log. The schema is empty for the purposes of the
        // test, since we only care that it exists, not what's in it.
        mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SmsService.MESSAGE_LOG_SCHEMA_ID,
                SmsService.MESSAGE_LOG_SCHEMA_REV)).thenReturn(UploadSchema.create());

        // Mock SMS providers.
        mockSnsClient = mock(AmazonSNSClient.class);
        when(mockSnsClient.publish(any())).thenReturn(new PublishResult().withMessageId(MESSAGE_ID));

        // Mock study service. This is only used to get the study short name.
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setShortName(STUDY_SHORT_NAME);

        // Mock other DAOs and services.
        mockHealthDataService = mock(HealthDataService.class);
        mockMessageDao = mock(SmsMessageDao.class);
        mockParticipantService = mock(ParticipantService.class);

        // Set up service.
        svc = new SmsService();
        svc.setHealthDataService(mockHealthDataService);
        svc.setMessageDao(mockMessageDao);
        svc.setParticipantService(mockParticipantService);
        svc.setSchemaService(mockSchemaService);
        svc.setSnsClient(mockSnsClient);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void sendTransactionalSMSMessageOK() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITH_TIME_ZONE);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withTransactionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(HEALTH_CODE, provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(MESSAGE_BODY, request.getMessage());
        assertEquals("Transactional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals(STUDY_SHORT_NAME,
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());

        // We log the SMS message to DDB and to health data.
        verifyLoggedSmsMessage(HEALTH_CODE, MESSAGE_BODY, SmsType.TRANSACTIONAL);
        verifyHealthData(PARTICIPANT_WITH_TIME_ZONE, TIME_ZONE, SmsType.TRANSACTIONAL, MESSAGE_BODY);
    }

    @Test
    public void sendPromotionalSMSMessageOK() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITH_TIME_ZONE);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();

        svc.sendSmsMessage(HEALTH_CODE, provider);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), request.getPhoneNumber());
        assertEquals(MESSAGE_BODY, request.getMessage());
        assertEquals("Promotional",
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue());
        assertEquals(STUDY_SHORT_NAME,
                request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue());

        // We log the SMS message to DDB and to health data.
        verifyLoggedSmsMessage(HEALTH_CODE, MESSAGE_BODY, SmsType.PROMOTIONAL);
        verifyHealthData(PARTICIPANT_WITH_TIME_ZONE, TIME_ZONE, SmsType.PROMOTIONAL, MESSAGE_BODY);
    }

    @Test
    public void sendSmsMessage_NullUserIdOkay() throws Exception {
        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(null, provider);

        // Everything else is verified. Just verified that the sent message contains no health code.
        verifyLoggedSmsMessage(null, MESSAGE_BODY, SmsType.PROMOTIONAL);

        // We submit no health data.
        verify(mockHealthDataService, never()).submitHealthData(any(), any(), any());
    }

    // branch coverage
    @Test
    public void sendSmsMessage_NoParticipant() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(null);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(null, provider);

        // Everything else is verified. Just verified that the sent message contains no health code.
        verifyLoggedSmsMessage(null, MESSAGE_BODY, SmsType.PROMOTIONAL);

        // We submit no health data.
        verify(mockHealthDataService, never()).submitHealthData(any(), any(), any());
    }

    @Test
    public void sendSmsMessage_ParticipantHasNoTimeZone() throws Exception {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITHOUT_TIME_ZONE);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(HEALTH_CODE, provider);

        // Everything else is verified. Just verify the timezone in the health data.
        verifyHealthData(PARTICIPANT_WITHOUT_TIME_ZONE, DateTimeZone.UTC, SmsType.PROMOTIONAL, MESSAGE_BODY);
    }

    @Test
    public void sendSmsMessage_SchemaDoesNotExist() {
        // Mock participant service.
        when(mockParticipantService.getParticipant(any(), anyString(), eq(false))).thenReturn(
                PARTICIPANT_WITH_TIME_ZONE);

        // Schema Service has no schema (throws).
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SmsService.MESSAGE_LOG_SCHEMA_ID,
                SmsService.MESSAGE_LOG_SCHEMA_REV)).thenThrow(EntityNotFoundException.class);

        // Set up test and execute.
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(study)
                .withSmsTemplate(new SmsTemplate(MESSAGE_BODY))
                .withPromotionType()
                .withPhone(TestConstants.PHONE).build();
        svc.sendSmsMessage(HEALTH_CODE, provider);

        // Everything else is verified. Just verify that we create the new schema.
        ArgumentCaptor<UploadSchema> schemaCaptor = ArgumentCaptor.forClass(UploadSchema.class);
        verify(mockSchemaService).createSchemaRevisionV4(eq(TestConstants.TEST_STUDY), schemaCaptor.capture());

        UploadSchema schema = schemaCaptor.getValue();
        assertEquals(SmsService.MESSAGE_LOG_SCHEMA_ID, schema.getSchemaId());
        assertEquals(SmsService.MESSAGE_LOG_SCHEMA_REV, schema.getRevision());
        assertEquals(SmsService.MESSAGE_LOG_SCHEMA_NAME, schema.getName());
        assertEquals(UploadSchemaType.IOS_DATA, schema.getSchemaType());

        List<UploadFieldDefinition> fieldDefList = schema.getFieldDefinitions();
        assertEquals(3, fieldDefList.size());

        assertEquals(SmsService.FIELD_NAME_SENT_ON, fieldDefList.get(0).getName());
        assertEquals(UploadFieldType.TIMESTAMP, fieldDefList.get(0).getType());

        assertEquals(SmsService.FIELD_NAME_SMS_TYPE, fieldDefList.get(1).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(1).getType());
        assertEquals(SmsType.VALUE_MAX_LENGTH, fieldDefList.get(1).getMaxLength().intValue());

        assertEquals(SmsService.FIELD_NAME_MESSAGE_BODY, fieldDefList.get(2).getName());
        assertEquals(UploadFieldType.STRING, fieldDefList.get(2).getType());
        assertTrue(fieldDefList.get(2).isUnboundedText());
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

        svc.sendSmsMessage(HEALTH_CODE, provider);
    }

    private void verifyLoggedSmsMessage(String expectedHealthCode, String expectedMessage, SmsType expectedSmsType) {
        ArgumentCaptor<SmsMessage> loggedMessageCaptor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(mockMessageDao).logMessage(loggedMessageCaptor.capture());

        SmsMessage loggedMessage = loggedMessageCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), loggedMessage.getPhoneNumber());
        assertEquals(MOCK_NOW_MILLIS, loggedMessage.getSentOn());
        assertEquals(expectedHealthCode, loggedMessage.getHealthCode());
        assertEquals(expectedMessage, loggedMessage.getMessageBody());
        assertEquals(MESSAGE_ID, loggedMessage.getMessageId());
        assertEquals(expectedSmsType, loggedMessage.getSmsType());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, loggedMessage.getStudyId());
    }

    private void verifyHealthData(StudyParticipant expectedParticipant, DateTimeZone expectedTimeZone,
            SmsType expectedSmsType, String expectedMessage) throws Exception {
        ArgumentCaptor<HealthDataSubmission> healthDataCaptor = ArgumentCaptor.forClass(HealthDataSubmission.class);
        verify(mockHealthDataService).submitHealthData(eq(TestConstants.TEST_STUDY), same(expectedParticipant),
                healthDataCaptor.capture());
        HealthDataSubmission healthData = healthDataCaptor.getValue();

        // Verify simple attributes.
        assertEquals(SmsService.BRIDGE_SERVER_APP_VERSION, healthData.getAppVersion());
        assertEquals(SmsService.BRIDGE_SERVER_PHONE_INFO, healthData.getPhoneInfo());
        assertEquals(SmsService.MESSAGE_LOG_SCHEMA_ID, healthData.getSchemaId());
        assertEquals(SmsService.MESSAGE_LOG_SCHEMA_REV, healthData.getSchemaRevision().intValue());

        DateTime createdOn = healthData.getCreatedOn();
        assertEquals(MOCK_NOW_MILLIS, createdOn.getMillis());
        assertEquals(expectedTimeZone.getOffset(createdOn), createdOn.getZone().getOffset(createdOn));

        // Assert health data.
        JsonNode healthDataNode = healthData.getData();
        assertEquals(expectedSmsType.getValue(), healthDataNode.get(SmsService.FIELD_NAME_SMS_TYPE).textValue());
        assertEquals(expectedMessage, healthDataNode.get(SmsService.FIELD_NAME_MESSAGE_BODY).textValue());

        // sentOn is createdOn in string form.
        assertEquals(createdOn.toString(), healthDataNode.get(SmsService.FIELD_NAME_SENT_ON).textValue());
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

    @Test(expected = BadRequestException.class)
    public void optInPhoneNumber_NullUserId() {
        svc.optInPhoneNumber(null, TestConstants.PHONE);
    }

    @Test(expected = BadRequestException.class)
    public void optInPhoneNumber_EmptyUserId() {
        svc.optInPhoneNumber("", TestConstants.PHONE);
    }

    @Test(expected = BadRequestException.class)
    public void optInPhoneNumber_BlankUserId() {
        svc.optInPhoneNumber("   ", TestConstants.PHONE);
    }

    @Test(expected = BadRequestException.class)
    public void optInPhoneNumber_NullPhone() {
        svc.optInPhoneNumber(USER_ID, null);
    }

    @Test(expected = BadRequestException.class)
    public void optInPhoneNumber_InvalidPhone() {
        svc.optInPhoneNumber(USER_ID, new Phone("NaN", "US"));
    }

    @Test
    public void createPhoneParticipant_PhoneNotOptedOut() {
        // Mock SNS client to return false.
        when(mockSnsClient.checkIfPhoneNumberIsOptedOut(any())).thenReturn(new CheckIfPhoneNumberIsOptedOutResult()
                .withIsOptedOut(false));

        // Execute.
        svc.optInPhoneNumber(USER_ID, TestConstants.PHONE);

        // Verify calls to SNS.
        ArgumentCaptor<CheckIfPhoneNumberIsOptedOutRequest> checkRequestCaptor = ArgumentCaptor.forClass(
                CheckIfPhoneNumberIsOptedOutRequest.class);
        verify(mockSnsClient).checkIfPhoneNumberIsOptedOut(checkRequestCaptor.capture());
        CheckIfPhoneNumberIsOptedOutRequest checkRequest = checkRequestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), checkRequest.getPhoneNumber());

        verify(mockSnsClient, never()).optInPhoneNumber(any());
    }

    @Test
    public void createPhoneParticipant_OptPhoneBackIn() {
        // Mock SNS client to return true.
        when(mockSnsClient.checkIfPhoneNumberIsOptedOut(any())).thenReturn(new CheckIfPhoneNumberIsOptedOutResult()
                .withIsOptedOut(true));

        // Execute.
        svc.optInPhoneNumber(USER_ID, TestConstants.PHONE);

        // Verify calls to SNS.
        ArgumentCaptor<CheckIfPhoneNumberIsOptedOutRequest> checkRequestCaptor = ArgumentCaptor.forClass(
                CheckIfPhoneNumberIsOptedOutRequest.class);
        verify(mockSnsClient).checkIfPhoneNumberIsOptedOut(checkRequestCaptor.capture());
        CheckIfPhoneNumberIsOptedOutRequest checkRequest = checkRequestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), checkRequest.getPhoneNumber());

        ArgumentCaptor<OptInPhoneNumberRequest> optInRequestCaptor = ArgumentCaptor.forClass(
                OptInPhoneNumberRequest.class);
        verify(mockSnsClient).optInPhoneNumber(optInRequestCaptor.capture());
        OptInPhoneNumberRequest optInRequest = optInRequestCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), optInRequest.getPhoneNumber());
    }
}
