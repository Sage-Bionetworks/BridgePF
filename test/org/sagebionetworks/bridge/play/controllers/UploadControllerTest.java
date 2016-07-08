package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.UploadService;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class UploadControllerTest {
    
    private static final String UPLOAD_ID = "upload-id";
    
    @Spy
    private UploadController controller;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private HealthCodeDao healthCodeDao;
    
    @Mock
    private UserSession workerSession;
    
    @Mock
    private UserSession consentedUserSession;
    
    @Mock
    private UserSession otherUserSession;
    
    @Mock 
    private Metrics metrics;
    
    @Captor
    private ArgumentCaptor<Upload> uploadCaptor;
    
    @Before
    public void before() {
        controller.setUploadService(uploadService);
        controller.setHealthCodeDao(healthCodeDao);
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode("consented-user-health-code");
        upload.setUploadId("upload-id");
        doReturn(upload).when(uploadService).getUpload("upload-id");
        
        doReturn(metrics).when(controller).getMetrics();
        
        doReturn("worker-health-code").when(workerSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("worker-study-id")).when(workerSession).getStudyIdentifier();
        doReturn(true).when(workerSession).isInRole(Roles.WORKER);
        
        doReturn("consented-user-health-code").when(consentedUserSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("consented-user-study-id")).when(consentedUserSession).getStudyIdentifier();
        doReturn(false).when(consentedUserSession).isInRole(Roles.WORKER);
        
        doReturn("worker-study-id").when(healthCodeDao).getStudyIdentifier("worker-health-code");
        doReturn("consented-user-study-id").when(healthCodeDao).getStudyIdentifier("consented-user-health-code");
    }
    
    @Test
    public void uploadCompleteAcceptsWorker() throws Exception {
        doReturn(workerSession).when(controller).getAuthenticatedSession();
        TestUtils.mockPlayContext();
        
        Result result = controller.uploadComplete(UPLOAD_ID);
        TestUtils.assertResult(result, 200, "Upload upload-id complete!");
        
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")), uploadCaptor.capture());
        
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());
    }

    @Test
    public void uploadCompleteAcceptsConsentedUser() throws Exception {
        doReturn(consentedUserSession).when(controller).getAuthenticatedSession();
        doReturn(consentedUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlayContext();
        
        Result result = controller.uploadComplete(UPLOAD_ID);
        TestUtils.assertResult(result, 200, "Upload upload-id complete!");
        
        verify(uploadService).uploadComplete(eq(new StudyIdentifierImpl("consented-user-study-id")), uploadCaptor.capture());
        
        Upload upload = uploadCaptor.getValue();
        assertEquals("consented-user-health-code", upload.getHealthCode());
    }
    
    @Test
    public void differentUserInSameStudyCannotCompleteUpload() throws Exception {
        doReturn("other-health-code").when(otherUserSession).getHealthCode();
        doReturn(new StudyIdentifierImpl("consented-user-study-id")).when(otherUserSession).getStudyIdentifier();
        doReturn(false).when(otherUserSession).isInRole(Roles.WORKER);
        
        doReturn(otherUserSession).when(controller).getAuthenticatedSession();
        doReturn(otherUserSession).when(controller).getAuthenticatedAndConsentedSession();
        TestUtils.mockPlayContext();
        
        try {
            controller.uploadComplete(UPLOAD_ID);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            
        }
        verify(uploadService, never()).uploadComplete(any(), any());
    }
}
