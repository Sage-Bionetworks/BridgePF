package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.healthdata.RecordExportStatusRequest;
import org.sagebionetworks.bridge.services.HealthDataService;
import play.mvc.Result;
import play.test.Helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("ConstantConditions")
public class HealthDataControllerTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final String CREATED_ON_STR = "2017-08-24T14:38:57.340+0900";
    private static final DateTime CREATED_ON = DateTime.parse(CREATED_ON_STR);
    private static final String HEALTH_CODE = "health-code";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().build();
    private static final String PHONE_INFO = "Unit Tests";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;
    private static final String TEST_RECORD_ID = "record-to-update";
    private static final HealthDataRecord.ExporterStatus TEST_STATUS = HealthDataRecord.ExporterStatus.SUCCEEDED;

    private static final String TEST_STATUS_JSON = "{\n" +
            "   \"recordIds\":[\"record-to-update\"],\n" +
            "   \"synapseExporterStatus\":\"SUCCEEDED\"\n" +
            "}";

    ArgumentCaptor<RecordExportStatusRequest> requestArgumentCaptor = ArgumentCaptor.forClass(RecordExportStatusRequest.class);

    @Mock
    private CacheProvider cacheProvider;

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private Metrics metrics;

    @Mock
    private UserSession workerSession;

    @Mock
    private UserSession consentedUserSession;

    @Mock
    private UserSession otherUserSession;

    @Mock
    private UserSession researcherSession;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
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
        TestUtils.mockPlayContextWithJson(jsonText);

        // spy controller
        HealthDataController controller = spy(new HealthDataController());
        controller.setCacheProvider(cacheProvider);
        controller.setHealthDataService(healthDataService);

        // mock Metrics
        doReturn(metrics).when(controller).getMetrics();

        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        mockSession.setParticipant(PARTICIPANT);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();

        // mock RequestInfo
        doReturn(new RequestInfo.Builder()).when(controller).getRequestInfoBuilder(mockSession);

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
    public void updateRecordsStatus() throws Exception {
        // mock session
        UserSession mockSession = new UserSession();

        // mock request JSON
        TestUtils.mockPlayContextWithJson(TEST_STATUS_JSON);

        when(healthDataService.updateRecordsWithExporterStatus(anyVararg())).thenReturn(ImmutableList.of(TEST_RECORD_ID));

        // spy controller
        HealthDataController controller = spy(new HealthDataController());
        controller.setHealthDataService(healthDataService);
        doReturn(mockSession).when(controller).getAuthenticatedSession(anyVararg());

        // create a mock request entity
        RecordExportStatusRequest mockRequest = new RecordExportStatusRequest();
        mockRequest.setRecordIds(ImmutableList.of(TEST_RECORD_ID));
        mockRequest.setSynapseExporterStatus(TEST_STATUS);

        // execute and validate
        Result result = controller.updateRecordsStatus();
        TestUtils.assertResult(result, 200, "Update exporter status to: " + ImmutableList.of(TEST_RECORD_ID) + " complete.");

        // first verify if it calls the service
        verify(healthDataService).updateRecordsWithExporterStatus(anyVararg());
        // then verify if it parse json correctly as a request entity
        verify(healthDataService).updateRecordsWithExporterStatus(requestArgumentCaptor.capture());
        RecordExportStatusRequest capturedRequest = requestArgumentCaptor.getValue();
        assertEquals(TEST_RECORD_ID, capturedRequest.getRecordIds().get(0));
        assertEquals(TEST_STATUS, capturedRequest.getSynapseExporterStatus());
    }
}
