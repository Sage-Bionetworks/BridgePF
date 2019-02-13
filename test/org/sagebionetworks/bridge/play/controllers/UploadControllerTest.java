package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.net.URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.models.upload.UploadValidationStatus;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.UploadService;

@RunWith(MockitoJUnitRunner.class)
public class UploadControllerTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String RECORD_ID = "record-id";
    private static final String UPLOAD_ID = "upload-id";
    private static final String VALIDATION_ERROR_MESSAGE = "There was a validation error";

    @Spy
    private UploadController controller;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private HealthDataService healthDataService;
    
    @Mock
    private HealthCodeDao healthCodeDao;
    
    @Mock
    private UserSession workerSession;
    
    @Mock
    private UserSession consentedUserSession;
    
    @Mock
    private UserSession otherUserSession;
    
    @Mock
    private UserSession researcherSession;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock 
    private Metrics metrics;
    
    @Captor
    private ArgumentCaptor<Upload> uploadCaptor;
    
    @Captor
    private ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    private DynamoUpload2 upload;
    
    @Before
    public void before() {
        controller.setUploadService(uploadService);
        controller.setCacheProvider(cacheProvider);
        controller.setHealthDataService(healthDataService);
        controller.setHealthCodeDao(healthCodeDao);

        // mock uploadService.getUpload()
        upload = new DynamoUpload2();
        upload.setHealthCode("consented-user-health-code");
        upload.setUploadId("upload-id");
        doReturn(upload).when(uploadService).getUpload("upload-id");
        
        upload.setStudyId("worker-health-code");
        // mock healthCodeDao
        //doReturn("worker-study-id").when(healthCodeDao).getStudyIdentifier("worker-health-code");
        //doReturn("consented-user-study-id").when(healthCodeDao).getStudyIdentifier("consented-user-health-code");

        // mock uploadService.get/pollUploadValidationStatus()
        // mock UploadService with validation status
        HealthDataRecord record = HealthDataRecord.create();
        record.setId(RECORD_ID);
        record.setHealthCode(HEALTH_CODE);

        UploadValidationStatus status = new UploadValidationStatus.Builder()
                .withId(UPLOAD_ID)
                .withRecord(record)
                .withMessageList(Lists.newArrayList(VALIDATION_ERROR_MESSAGE))
                .withStatus(UploadStatus.VALIDATION_FAILED).build();

        doReturn(status).when(uploadService).getUploadValidationStatus(UPLOAD_ID);
        doReturn(status).when(uploadService).pollUploadValidationStatusUntilComplete(UPLOAD_ID);

        // mock metrics
        doReturn(metrics).when(controller).getMetrics();

        // mock sessions
        doReturn(true).when(workerSession).isInRole(Roles.WORKER);
        
        doReturn("consented-user-health-code").when(consentedUserSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("consented-user-study-id")).when(consentedUserSession).getStudyIdentifier();
        doReturn("userId").when(consentedUserSession).getId();
        doReturn(new StudyParticipant.Builder().build()).when(consentedUserSession).getParticipant();
        
        doReturn("other-user-health-code").when(otherUserSession).getHealthCode();
    }
    
    @Test
    public void startingUploadRecordedInRequestInfo() throws Exception {
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlay().withJsonBody(TestUtils.createJson(
            "{'name':'uploadName','contentLength':100,'contentMd5':'abc','contentType':'application/zip'}"))
            .withHeader("User-Agent", "app/10").mock();
        
        UploadSession uploadSession = new UploadSession("id", new URL("http://server.com/"), 1000);
        
        doReturn(uploadSession).when(uploadService).createUpload(any(), any(), any());
        
        controller.upload();
        
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo info = requestInfoCaptor.getValue();
        assertNotNull(info.getUploadedOn());
        assertEquals("userId", info.getUserId());
    }
    
    @Test
    public void uploadCompleteAcceptsWorker() throws Exception {
        upload.setStudyId("consented-user-study-id");
        // setup controller
        doReturn(workerSession).when(controller).getAuthenticatedSession();
        TestUtils.mockPlay().mock();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, null, null);
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.S3_WORKER), uploadCaptor.capture(), eq(false));
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());

        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }
    
    @Test
    public void uploadCompleteWithMissingStudyId() throws Exception {
        upload.setStudyId(null); // no studyId, must look up by healthCode
        upload.setHealthCode(HEALTH_CODE);
        // setup controller
        doReturn(workerSession).when(controller).getAuthenticatedSession();
        doReturn("studyId").when(healthCodeDao).getStudyIdentifier(HEALTH_CODE);
        TestUtils.mockPlay().mock();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, null, null);
        validateValidationStatus(result);

        // verify back-end calls
        verify(healthCodeDao).getStudyIdentifier(HEALTH_CODE);
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("studyId")),
                eq(UploadCompletionClient.S3_WORKER), uploadCaptor.capture(), eq(false));
        Upload upload = uploadCaptor.getValue();
        assertEquals(HEALTH_CODE, upload.getHealthCode());

        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void uploadCompleteAcceptsConsentedUser() throws Exception {
        // setup controller
        doReturn(consentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlay().mock();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, null, null);
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), uploadCaptor.capture(), eq(false));
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());

        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }
    
    @Test
    public void differentUserInSameStudyCannotCompleteUpload() throws Exception {
        // setup controller
        doReturn("other-health-code").when(otherUserSession).getHealthCode();
        doReturn(false).when(otherUserSession).isInRole(Roles.WORKER);
        
        doReturn(otherUserSession).when(controller).getAuthenticatedSession();
        doReturn(otherUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlay().mock();

        // execute and catch exception
        try {
            controller.uploadComplete(UPLOAD_ID, null, null);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            // expected exception
        }

        // verify back-end calls
        verify(uploadService, never()).uploadComplete(any(), any(), any(), anyBoolean());
        verify(uploadService, never()).getUploadValidationStatus(any());
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void uploadCompleteSynchronousMode() throws Exception {
        // setup controller
        doReturn(consentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlay().mock();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, "true", null);
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), any(), eq(false));
        verify(uploadService).pollUploadValidationStatusUntilComplete(UPLOAD_ID);
        verify(uploadService, never()).getUploadValidationStatus(any());
    }

    @Test
    public void uploadCompleteRedriveFlag() throws Exception {
        // setup controller
        doReturn(consentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlay().mock();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, null, "true");
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), any(), eq(true));
        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void getValidationStatusWorks() throws Exception {
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        Result result = controller.getValidationStatus(UPLOAD_ID);
        validateValidationStatus(result);
        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
    }

    @Test(expected = UnauthorizedException.class)
    public void getValidationStatusEnforcesHealthCodeMatch() throws Exception {
        doReturn(otherUserSession).when(controller).getAuthenticatedAndConsentedSession();
        controller.getValidationStatus(UPLOAD_ID);
    }
    
    @Test
    public void getUploadById() throws Exception {
        TestUtils.mockPlay().mock();
        doReturn(researcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        
        HealthDataRecord record = HealthDataRecord.create();
        record.setHealthCode(HEALTH_CODE);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("researcher-study-id");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).withHealthDataRecord(record).build();
        
        when(uploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);
        
        Result result = controller.getUpload(UPLOAD_ID);
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("s3_worker", node.get("completedBy").textValue());
        assertEquals("Upload", node.get("type").textValue());
        assertEquals(HEALTH_CODE, node.get("healthData").get("healthCode").textValue());
    }

    @Test
    public void getUploadByRecordId() throws Exception {
        TestUtils.mockPlay().mock();
        doReturn(researcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        
        HealthDataRecord record = HealthDataRecord.create();
        record.setUploadId(UPLOAD_ID);
        record.setHealthCode(HEALTH_CODE);
        when(healthDataService.getRecordById("record-id")).thenReturn(record);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("researcher-study-id");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).withHealthDataRecord(record).build();
        
        when(uploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);

        Result result = controller.getUpload("recordId:record-id");
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("s3_worker", node.get("completedBy").textValue());
        assertEquals("Upload", node.get("type").textValue());
        assertEquals(HEALTH_CODE, node.get("healthData").get("healthCode").textValue());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getUploadByRecordIdRecordMissing() throws Exception {
        TestUtils.mockPlay().mock();
        doReturn(researcherSession).when(controller).getAuthenticatedSession(ADMIN, WORKER);
        
        when(healthDataService.getRecordById("record-id")).thenReturn(null);

        controller.getUpload("recordId:record-id");
    }
    
    private static void validateValidationStatus(Result result) throws Exception {
        TestUtils.assertResult(result, 200);

        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(UPLOAD_ID, node.get("id").textValue());
        assertEquals(UploadStatus.VALIDATION_FAILED.toString().toLowerCase(), node.get("status").textValue());
        assertEquals("UploadValidationStatus", node.get("type").textValue());

        JsonNode errors = node.get("messageList");
        assertEquals(VALIDATION_ERROR_MESSAGE, errors.get(0).textValue());

        JsonNode recordNode = node.get("record");
        assertEquals(RECORD_ID, recordNode.get("id").textValue());

        // Health code is filtered out of the record.
        assertNull(recordNode.get("healthCode"));
    }
}
