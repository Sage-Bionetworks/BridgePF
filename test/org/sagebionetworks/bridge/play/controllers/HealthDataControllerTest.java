package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Result;
import play.test.Helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("ConstantConditions")
public class HealthDataControllerTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String CREATED_ON_STR = "2017-08-24T14:38:57.340+09:00";
    private static final DateTime CREATED_ON = DateTime.parse(CREATED_ON_STR);
    private static final String CREATED_ON_END_STR = "2017-08-24T19:03:02.757+09:00";
    private static final DateTime CREATED_ON_END = DateTime.parse(CREATED_ON_END_STR);
    private static final String HEALTH_CODE = "health-code";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();
    private static final StudyParticipant OTHER_PARTICIPANT = new StudyParticipant.Builder()
            .withHealthCode(HEALTH_CODE).build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .build();
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final String TEST_RECORD_ID = "record-to-update";
    private static final HealthDataRecord.ExporterStatus TEST_STATUS = HealthDataRecord.ExporterStatus.SUCCEEDED;
    private static final String USER_ID = "test-user";

    private static final Study STUDY;
    static {
        STUDY = Study.create();
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
    }

    private static final String TEST_STATUS_JSON = "{\n" +
            "   \"recordIds\":[\"record-to-update\"],\n" +
            "   \"synapseExporterStatus\":\"SUCCEEDED\"\n" +
            "}";

    ArgumentCaptor<RecordExportStatusRequest> requestArgumentCaptor = ArgumentCaptor.forClass(RecordExportStatusRequest.class);

    @Spy
    private HealthDataController controller;

    @Mock
    private CacheProvider cacheProvider;

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private ParticipantService participantService;

    @Mock
    private StudyService studyService;

    @Mock
    private Metrics metrics;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Before
    public void before() {
        // spy controller
        controller = spy(new HealthDataController());
        controller.setCacheProvider(cacheProvider);
        controller.setHealthDataService(healthDataService);
        controller.setParticipantService(participantService);
        controller.setStudyService(studyService);

        // mock Metrics
        doReturn(metrics).when(controller).getMetrics();

        // Mock services.
        when(participantService.getParticipant(same(STUDY), eq(USER_ID), anyBoolean())).thenReturn(OTHER_PARTICIPANT);
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);

        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        mockSession.setParticipant(PARTICIPANT);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());

        // mock RequestInfo
        doReturn(new RequestInfo.Builder()).when(controller).getRequestInfoBuilder(mockSession);
    }

    @Test
    public void getRecordsByHealthCodeCreatedOn() throws Exception {
        // Mock health data service to return a couple dummy health data records.
        HealthDataRecord record1 = HealthDataRecord.create();
        record1.setId(TEST_RECORD_ID + "1");
        record1.setHealthCode(HEALTH_CODE);

        HealthDataRecord record2 = HealthDataRecord.create();
        record2.setId(TEST_RECORD_ID + "2");
        record2.setHealthCode(HEALTH_CODE);

        when(healthDataService.getRecordsByHealthCodeCreatedOn(HEALTH_CODE, CREATED_ON, CREATED_ON_END)).thenReturn(
                ImmutableList.of(record1, record2));

        // Execute and verify.
        Result result = controller.getRecordsByCreatedOn(CREATED_ON_STR, CREATED_ON_END_STR);
        TestUtils.assertResult(result, 200);

        DateTimeRangeResourceList<HealthDataRecord> recordResourceList = BridgeObjectMapper.get().readValue(
                Helpers.contentAsString(result), HealthDataController.RECORD_RESOURCE_LIST_TYPE_REF);
        assertEquals(CREATED_ON_STR, recordResourceList.getRequestParams().get(ResourceList.START_TIME));
        assertEquals(CREATED_ON_END_STR, recordResourceList.getRequestParams().get(ResourceList.END_TIME));

        // Note that we filter out health code.
        List<HealthDataRecord> recordList = recordResourceList.getItems();
        assertEquals(2, recordList.size());

        assertEquals(TEST_RECORD_ID + "1", recordList.get(0).getId());
        assertNull(recordList.get(0).getHealthCode());

        assertEquals(TEST_RECORD_ID + "2", recordList.get(1).getId());
        assertNull(recordList.get(1).getHealthCode());
    }

    @Test
    public void submitHealthData() throws Exception {
        // mock request JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"" + CREATED_ON_STR + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"data\":{\n" +
                "       \"foo\":\"foo-value\",\n" +
                "       \"bar\":42\n" +
                "   }\n" +
                "}";
        TestUtils.mockPlay().withJsonBody(jsonText).mock();

        // mock back-end call - We only care about record ID. Also add Health Code to make sure it's being filtered.
        HealthDataRecord svcRecord = HealthDataRecord.create();
        svcRecord.setId(TEST_RECORD_ID);
        svcRecord.setHealthCode(HEALTH_CODE);
        when(healthDataService.submitHealthData(any(), any(), any())).thenReturn(svcRecord);

        // execute and validate - Just check record ID. Health Code is filtered out.
        Result result = controller.submitHealthData();
        TestUtils.assertResult(result, 201);
        HealthDataRecord controllerRecord = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                HealthDataRecord.class);
        assertEquals(TEST_RECORD_ID, controllerRecord.getId());
        assertNull(controllerRecord.getHealthCode());

        // validate call to healthDataService
        ArgumentCaptor<HealthDataSubmission> submissionCaptor = ArgumentCaptor.forClass(HealthDataSubmission.class);
        verify(healthDataService).submitHealthData(eq(TestConstants.TEST_STUDY), same(PARTICIPANT),
                submissionCaptor.capture());

        HealthDataSubmission submission = submissionCaptor.getValue();
        assertEquals(APP_VERSION, submission.getAppVersion());
        assertEquals(CREATED_ON, submission.getCreatedOn());
        assertEquals(PHONE_INFO, submission.getPhoneInfo());
        assertEquals(SCHEMA_ID, submission.getSchemaId());
        assertEquals(SCHEMA_REV, submission.getSchemaRevision().intValue());

        JsonNode data = submission.getData();
        assertEquals(2, data.size());
        assertEquals("foo-value", data.get("foo").textValue());
        assertEquals(42, data.get("bar").intValue());

        // validate metrics
        verify(metrics).setRecordId(TEST_RECORD_ID);

        // validate request info uploadedOn - Time zone doesn't matter because we flatten everything to UTC anyway.
        ArgumentCaptor<RequestInfo> requestInfoCaptor = ArgumentCaptor.forClass(RequestInfo.class);
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());

        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals(MOCK_NOW_MILLIS, requestInfo.getUploadedOn().getMillis());
    }

    @Test
    public void submitHealthDataForParticipant() throws Exception {
        // mock request JSON
        String jsonText = "{\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"createdOn\":\"" + CREATED_ON_STR + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\",\n" +
                "   \"schemaId\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"data\":{\n" +
                "       \"foo\":\"other value\",\n" +
                "       \"bar\":37\n" +
                "   }\n" +
                "}";
        TestUtils.mockPlay().withJsonBody(jsonText).mock();

        // mock back-end call - We only care about record ID. Also add Health Code to make sure it's being filtered.
        HealthDataRecord svcRecord = HealthDataRecord.create();
        svcRecord.setId(TEST_RECORD_ID);
        svcRecord.setHealthCode(HEALTH_CODE);
        when(healthDataService.submitHealthData(any(), any(), any())).thenReturn(svcRecord);

        // execute and validate - Just check record ID. Health Code is filtered out.
        Result result = controller.submitHealthDataForParticipant(USER_ID);
        TestUtils.assertResult(result, 201);
        HealthDataRecord controllerRecord = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                HealthDataRecord.class);
        assertEquals(TEST_RECORD_ID, controllerRecord.getId());
        assertNull(controllerRecord.getHealthCode());

        // validate call to healthDataService
        ArgumentCaptor<HealthDataSubmission> submissionCaptor = ArgumentCaptor.forClass(HealthDataSubmission.class);
        verify(healthDataService).submitHealthData(eq(TestConstants.TEST_STUDY), same(OTHER_PARTICIPANT),
                submissionCaptor.capture());

        HealthDataSubmission submission = submissionCaptor.getValue();
        assertEquals(APP_VERSION, submission.getAppVersion());
        assertEquals(CREATED_ON, submission.getCreatedOn());
        assertEquals(PHONE_INFO, submission.getPhoneInfo());
        assertEquals(SCHEMA_ID, submission.getSchemaId());
        assertEquals(SCHEMA_REV, submission.getSchemaRevision().intValue());

        JsonNode data = submission.getData();
        assertEquals(2, data.size());
        assertEquals("other value", data.get("foo").textValue());
        assertEquals(37, data.get("bar").intValue());

        // validate metrics
        verify(metrics).setRecordId(TEST_RECORD_ID);
    }

    @Test
    public void updateRecordsStatus() throws Exception {
        // mock request JSON
        TestUtils.mockPlay().withJsonBody(TEST_STATUS_JSON).mock();

        when(healthDataService.updateRecordsWithExporterStatus(any())).thenReturn(ImmutableList.of(TEST_RECORD_ID));

        // create a mock request entity
        RecordExportStatusRequest mockRequest = new RecordExportStatusRequest();
        mockRequest.setRecordIds(ImmutableList.of(TEST_RECORD_ID));
        mockRequest.setSynapseExporterStatus(TEST_STATUS);

        // execute and validate
        Result result = controller.updateRecordsStatus();
        TestUtils.assertResult(result, 200, "Update exporter status to: " + ImmutableList.of(TEST_RECORD_ID) + " complete.");

        // first verify if it calls the service
        verify(healthDataService).updateRecordsWithExporterStatus(any());
        // then verify if it parse json correctly as a request entity
        verify(healthDataService).updateRecordsWithExporterStatus(requestArgumentCaptor.capture());
        RecordExportStatusRequest capturedRequest = requestArgumentCaptor.getValue();
        assertEquals(TEST_RECORD_ID, capturedRequest.getRecordIds().get(0));
        assertEquals(TEST_STATUS, capturedRequest.getSynapseExporterStatus());
    }
}
