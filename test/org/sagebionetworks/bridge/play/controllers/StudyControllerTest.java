package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class StudyControllerTest {

    private static final String EMAIL_ADDRESS = "foo@foo.com";
    private static final String PEM_TEXT = "-----BEGIN CERTIFICATE-----\nMIIExDCCA6ygAwIBAgIGBhCnnOuXMA0GCSqGSIb3DQEBBQUAMIGeMQswCQYDVQQG\nEwJVUzELMAkGA1UECAwCV0ExEDAOBgNVBAcMB1NlYXR0bGUxGTAXBgNVBAoMEFNh\nVlOwuuAxumMyIq5W4Dqk8SBcH9Y4qlk7\nEND CERTIFICATE-----";

    private StudyController controller;
    private StudyIdentifier studyId;

    @Mock
    private UserSession mockSession;
    @Mock
    private UploadCertificateService mockUploadCertService;
    @Mock
    private Study mockStudy;
    @Mock
    private StudyService mockStudyService;
    @Mock
    private EmailVerificationService mockVerificationService;
    @Mock
    private CacheProvider mockCacheProvider;
    
    private Study study;
    
    @Before
    public void before() throws Exception {
        controller = spy(new StudyController());
        
        // mock session with study identifier
        studyId = new StudyIdentifierImpl(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSession.getStudyIdentifier()).thenReturn(studyId);
        when(mockSession.isAuthenticated()).thenReturn(true);
        
        study = new DynamoStudy();
        study.setSupportEmail(EMAIL_ADDRESS);
        
        when(mockStudyService.getStudy(studyId)).thenReturn(study);
        
        when(mockVerificationService.getEmailStatus(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);

        mockUploadCertService = mock(UploadCertificateService.class);
        when(mockUploadCertService.getPublicKeyAsPem(any(StudyIdentifier.class))).thenReturn(PEM_TEXT);
        
        controller.setStudyService(mockStudyService);
        controller.setCacheProvider(mockCacheProvider);
        controller.setEmailVerificationService(mockVerificationService);
        controller.setUploadCertificateService(mockUploadCertService);
        
        mockPlayContext();
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode")
                .withRoles(Sets.newHashSet()).build();
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        
        doReturn(session).when(controller).getSessionIfItExists();

        controller.getStudyPublicKeyAsPem();
    }
    
    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode")
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        
        Result result = controller.getStudyPublicKeyAsPem();
        String pemFile = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(pemFile);
        assertTrue(node.get("publicKey").asText().contains("-----BEGIN CERTIFICATE-----"));
        assertEquals("CmsPublicKey", node.get("type").asText());
    }
    
    @Test
    public void getEmailStatus() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Result result = controller.getEmailStatus();
        
        verify(mockVerificationService).getEmailStatus(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }
    
    @Test
    public void verifyEmail() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockVerificationService.verifyEmailAddress(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);
        
        Result result = controller.verifyEmail();
        
        verify(mockVerificationService).verifyEmailAddress(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }
    
    @Test
    public void developerCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(Roles.DEVELOPER);
    }
    
    @Test
    public void researcherCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(Roles.RESEARCHER);
    }
    
    @Test
    public void adminCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(Roles.ADMIN);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void userCannotAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(null);
    }
    
    private void testRoleAccessToCurrentStudy(Roles role) throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(role)).build();
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(studyId);
        doReturn(session).when(controller).getSessionIfItExists();
        
        Result result = controller.getCurrentStudy();
        assertEquals(200, result.status());
        
        Study study = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), Study.class);
        assertEquals(EMAIL_ADDRESS, study.getSupportEmail());        
    }

}
