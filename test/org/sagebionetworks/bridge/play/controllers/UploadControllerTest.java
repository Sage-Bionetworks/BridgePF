package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.ADMIN;

import java.net.URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
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
    private HealthCodeDao healthCodeDao;
    
    @Mock
    private HealthDataService healthDataService;
    
    @Mock
    private UserSession workerSession;
    
    @Mock
    private UserSession developerSession;
    
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
    
    @Before
    public void before() {
        controller.setUploadService(uploadService);
        controller.setHealthCodeDao(healthCodeDao);
        controller.setCacheProvider(cacheProvider);
        controller.setHealthDataService(healthDataService);

        // mock uploadService.getUpload()
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode("consented-user-health-code");
        upload.setUploadId("upload-id");
        doReturn(upload).when(uploadService).getUpload("upload-id");

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
        doReturn("worker-health-code").when(workerSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("worker-study-id")).when(workerSession).getStudyIdentifier();
        doReturn(true).when(workerSession).isInRole(Roles.WORKER);
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.WORKER)).build();
        doReturn(participant).when(workerSession).getParticipant();
        
        doReturn("dev-health-code").when(developerSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("dev-study-id")).when(developerSession).getStudyIdentifier();
        doReturn(true).when(developerSession).isInRole(Roles.DEVELOPER);
        participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        doReturn(participant).when(developerSession).getParticipant();
        
        doReturn("consented-user-health-code").when(consentedUserSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("consented-user-study-id")).when(consentedUserSession).getStudyIdentifier();
        doReturn(true).when(consentedUserSession).isAuthenticated();
        doReturn(true).when(consentedUserSession).doesConsent();
        doReturn("userId").when(consentedUserSession).getId();
        doReturn(new StudyParticipant.Builder().build()).when(consentedUserSession).getParticipant();
        
        doReturn("researcher-health-code").when(researcherSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("researcher-study-id")).when(researcherSession).getStudyIdentifier();
        doReturn(true).when(researcherSession).isInRole(Roles.RESEARCHER);
        doReturn(true).when(researcherSession).isAuthenticated();
        doReturn(false).when(researcherSession).doesConsent();
        
        doReturn("other-user-health-code").when(otherUserSession).getHealthCode();
        participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet()).build();
        doReturn(participant).when(otherUserSession).getParticipant();
        doReturn(true).when(otherUserSession).doesConsent();

        // mock healthCodeDao
        doReturn("worker-study-id").when(healthCodeDao).getStudyIdentifier("worker-health-code");
        doReturn("consented-user-study-id").when(healthCodeDao).getStudyIdentifier("consented-user-health-code");
    }
    
    @Test
    public void startingUploadRecordedInRequestInfo() throws Exception {
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlayContextWithJson(TestUtils.createJson(
            "{'name':'uploadName','contentLength':100,'contentMd5':'abc','contentType':'application/zip'}"));
        
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
        // setup controller
        doReturn(workerSession).when(controller).getAuthenticatedSession();
        TestUtils.mockPlayContext();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, null);
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")), eq(UploadCompletionClient.S3_WORKER), uploadCaptor.capture());
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());

        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void uploadCompleteAcceptsConsentedUser() throws Exception {
        // setup controller
        doReturn(consentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlayContext();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, null);
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")), eq(UploadCompletionClient.APP), uploadCaptor.capture());
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());

        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }
    
    @Test
    public void differentUserInSameStudyCannotCompleteUpload() throws Exception {
        // setup controller
        doReturn("other-health-code").when(otherUserSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("consented-user-study-id")).when(otherUserSession).getStudyIdentifier();
        doReturn(false).when(otherUserSession).isInRole(Roles.WORKER);
        
        doReturn(otherUserSession).when(controller).getAuthenticatedSession();
        doReturn(otherUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlayContext();

        // execute and catch exception
        try {
            controller.uploadComplete(UPLOAD_ID, null);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            // expected exception
        }

        // verify back-end calls
        verify(uploadService, never()).uploadComplete(any(), any(), any());
        verify(uploadService, never()).getUploadValidationStatus(any());
        verify(uploadService, never()).pollUploadValidationStatusUntilComplete(any());
    }

    @Test
    public void uploadCompleteSynchronousMode() throws Exception {
        // setup controller
        doReturn(consentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlayContext();

        // execute and validate
        Result result = controller.uploadComplete(UPLOAD_ID, "true");
        validateValidationStatus(result);

        // verify back-end calls
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")),
                eq(UploadCompletionClient.APP), any());
        verify(uploadService).pollUploadValidationStatusUntilComplete(UPLOAD_ID);
        verify(uploadService, never()).getUploadValidationStatus(any());
    }

    @Test
    public void getValidationStatusWorks() throws Exception {
        doReturn(consentedUserSession).when(controller).getSessionEitherConsentedOrInRole(Roles.RESEARCHER);
        Result result = controller.getValidationStatus(UPLOAD_ID);
        validateValidationStatus(result);
        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
    }

    @Test
    public void getValidationStatusWorksForResearcher() throws Exception {
        doReturn(researcherSession).when(controller).getSessionEitherConsentedOrInRole(Roles.RESEARCHER);
        Result result = controller.getValidationStatus(UPLOAD_ID);
        validateValidationStatus(result);
        verify(uploadService).getUploadValidationStatus(UPLOAD_ID);
    }

    @Test(expected = UnauthorizedException.class)
    public void getValidationStatusEnforcesHealthCodeMatch() throws Exception {
        doReturn(otherUserSession).when(controller).getSessionEitherConsentedOrInRole(Roles.RESEARCHER);
        controller.getValidationStatus(UPLOAD_ID);
    }
    
    @Test
    public void getUploadById() throws Exception {
        TestUtils.mockPlayContext();
        doReturn(developerSession).when(controller).getAuthenticatedSession(RESEARCHER, ADMIN);
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("dev-study-id");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).build();
        
        when(uploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);
        
        Result result = controller.getUpload(UPLOAD_ID);
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("s3_worker", node.get("completedBy").textValue());
        assertEquals("Upload", node.get("type").textValue());
    }

    @Test
    public void getUploadByRecordId() throws Exception {
        TestUtils.mockPlayContext();
        doReturn(developerSession).when(controller).getAuthenticatedSession(RESEARCHER, ADMIN);
        
        HealthDataRecord record = HealthDataRecord.create();
        record.setUploadId(UPLOAD_ID);
        when(healthDataService.getRecordById("record-id")).thenReturn(record);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("dev-study-id");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).build();
        
        when(uploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);

        Result result = controller.getUpload("recordId:record-id");
        
        assertEquals(200, result.status());
        JsonNode node = TestUtils.getJson(result);
        assertEquals("s3_worker", node.get("completedBy").textValue());
        assertEquals("Upload", node.get("type").textValue());
    }
    
    @Test(expected = UnauthorizedException.class)
    public void getUploadFromOtherStudyFails() throws Exception {
        TestUtils.mockPlayContext();
        doReturn(developerSession).when(controller).getAuthenticatedSession(RESEARCHER, ADMIN);
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setStudyId("different-study");
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        UploadView uploadView = new UploadView.Builder().withUpload(upload).build();
        
        when(uploadService.getUploadView(UPLOAD_ID)).thenReturn(uploadView);
        
        controller.getUpload(UPLOAD_ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getUploadByRecordIdRecordMissing() throws Exception {
        TestUtils.mockPlayContext();
        doReturn(developerSession).when(controller).getAuthenticatedSession(RESEARCHER, ADMIN);
        
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
