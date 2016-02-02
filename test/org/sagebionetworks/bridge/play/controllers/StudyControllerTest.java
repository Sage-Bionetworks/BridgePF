package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UserProfileService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import play.mvc.Http;
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
    private UserProfileService mockUserProfileService;
    @Mock
    private StudyService mockStudyService;
    @Mock
    private Study study;
    @Mock
    private EmailVerificationService mockVerificationService;
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Before
    public void before() throws Exception {
        controller = spy(new StudyController());
        
        // mock session with study identifier
        studyId = new StudyIdentifierImpl(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSession.getStudyIdentifier()).thenReturn(studyId);
        
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockStudy.getSupportEmail()).thenReturn(EMAIL_ADDRESS);
        when(mockStudyService.getStudy(studyId)).thenReturn(mockStudy);
        
        when(mockVerificationService.getEmailStatus(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);

        mockUploadCertService = mock(UploadCertificateService.class);
        when(mockUploadCertService.getPublicKeyAsPem(any(StudyIdentifier.class))).thenReturn(PEM_TEXT);
        
        controller.setStudyService(mockStudyService);
        controller.setCacheProvider(mockCacheProvider);
        controller.setEmailVerificationService(mockVerificationService);
        controller.setUploadCertificateService(mockUploadCertService);
        controller.setUserProfileService(mockUserProfileService);
        
        Http.Context context = mockPlayContext();
        Http.Context.current.set(context);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        User user = new User();
        user.setHealthCode("healthCode");
        user.setRoles(Sets.newHashSet());
        when(mockSession.getUser()).thenReturn(user);

        // this should fail, returning a session without the role
        reset(controller);
        doReturn(mockSession).when(controller).getAuthenticatedSession(); 

        controller.getStudyPublicKeyAsPem();
    }
    
    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        User user = new User();
        user.setHealthCode("healthCode");
        user.setRoles(Sets.newHashSet(Roles.DEVELOPER));
        when(mockSession.getUser()).thenReturn(user);

        doReturn(mockSession).when(controller).getAuthenticatedSession();
        
        Result result = controller.getStudyPublicKeyAsPem();
        String pemFile = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(pemFile);
        assertTrue(node.get("publicKey").asText().contains("-----BEGIN CERTIFICATE-----"));
        assertEquals("CmsPublicKey", node.get("type").asText());
    }
    
    @Test
    public void canSendEmailRoster() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(RESEARCHER);
        
        Result result = controller.sendStudyParticipantsRoster();
        assertEquals(202, result.status());
        
        String content = Helpers.contentAsString(result);
        assertTrue(content.contains("A roster of study participants will be emailed"));

        verify(mockUserProfileService).sendStudyParticipantRoster(mockStudy);
    }
    
    @Test
    public void getEmailStatusNoCache() throws Exception {
        controller.getEmailStatus();
        
        verify(mockVerificationService).getEmailStatus(EMAIL_ADDRESS);
    }
    
    @Test
    public void getEmailStatusIsCached() throws Exception {
        when(mockCacheProvider.getEmailVerificationStatus(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);
        
        Result result = controller.getEmailStatus();

        verify(mockCacheProvider).getEmailVerificationStatus(EMAIL_ADDRESS);
        verify(mockVerificationService, never()).getEmailStatus(EMAIL_ADDRESS);
        
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }
    
    @Test
    public void verifyEmailNoCache() throws Exception {
        when(mockVerificationService.verifyEmailAddress(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);
        
        Result result = controller.verifyEmail();
        
        verify(mockVerificationService).verifyEmailAddress(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }

    @Test
    public void verifyEmailIsCached() throws Exception {
        when(mockCacheProvider.getEmailVerificationStatus(EMAIL_ADDRESS))
            .thenReturn(EmailVerificationStatus.VERIFIED);
        
        Result result = controller.verifyEmail();
        
        verify(mockCacheProvider).getEmailVerificationStatus(EMAIL_ADDRESS);
        verify(mockVerificationService, never()).verifyEmailAddress(EMAIL_ADDRESS);

        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }
}
