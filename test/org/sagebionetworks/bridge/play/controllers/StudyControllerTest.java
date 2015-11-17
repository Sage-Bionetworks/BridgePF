package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;

import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UserProfileService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

public class StudyControllerTest {

    private static final String PEM_TEXT = "-----BEGIN CERTIFICATE-----\nMIIExDCCA6ygAwIBAgIGBhCnnOuXMA0GCSqGSIb3DQEBBQUAMIGeMQswCQYDVQQG\nEwJVUzELMAkGA1UECAwCV0ExEDAOBgNVBAcMB1NlYXR0bGUxGTAXBgNVBAoMEFNh\nVlOwuuAxumMyIq5W4Dqk8SBcH9Y4qlk7\nEND CERTIFICATE-----";

    @Test(expected = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        UserSession session = mock(UserSession.class);
        StudyIdentifier studyId = mock(StudyIdentifier.class);
        when(session.getStudyIdentifier()).thenReturn(studyId);
        User user = mock(User.class);
        when(user.getHealthCode()).thenReturn("healthCode");
        when(user.getRoles()).thenReturn(Sets.newHashSet()); // not a developer
        when(session.getUser()).thenReturn(user);

        StudyController controller = spy(new StudyController());
        doReturn(session).when(controller).getAuthenticatedSession();

        UploadCertificateService uploadCertificateService = mock(UploadCertificateService.class);
        when(uploadCertificateService.getPublicKeyAsPem(any(StudyIdentifier.class))).thenReturn(PEM_TEXT);
        controller.setUploadCertificateService(uploadCertificateService);
        
        controller.getStudyPublicKeyAsPem();
    }
    
    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        UserSession session = mock(UserSession.class);
        StudyIdentifier studyId = mock(StudyIdentifier.class);
        when(session.getStudyIdentifier()).thenReturn(studyId);
        User user = mock(User.class);
        when(user.getHealthCode()).thenReturn("healthCode");
        when(user.isInRole(Roles.DEVELOPER)).thenReturn(true); // caller is a developer
        when(session.getUser()).thenReturn(user);

        StudyController controller = spy(new StudyController());
        doReturn(session).when(controller).getAuthenticatedSession();

        UploadCertificateService uploadCertificateService = mock(UploadCertificateService.class);
        when(uploadCertificateService.getPublicKeyAsPem(any(StudyIdentifier.class))).thenReturn(PEM_TEXT);
        controller.setUploadCertificateService(uploadCertificateService);
        
        Result result = controller.getStudyPublicKeyAsPem();
        String pemFile = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(pemFile);
        assertTrue(node.get("publicKey").asText().contains("-----BEGIN CERTIFICATE-----"));
        assertEquals("CmsPublicKey", node.get("type").asText());
    }
    
    @Test
    public void canSendEmailRoster() throws Exception {
        UserSession session = mock(UserSession.class);
        StudyIdentifier studyId = new StudyIdentifierImpl(TestConstants.TEST_STUDY_IDENTIFIER);
        when(session.getStudyIdentifier()).thenReturn(studyId);
        
        StudyController controller = spy(new StudyController());
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);
        
        StudyService mockStudyService = mock(StudyService.class);
        Study study = mock(Study.class);
        when(mockStudyService.getStudy(studyId)).thenReturn(study);
        controller.setStudyService(mockStudyService);
        
        UserProfileService userProfileService = mock(UserProfileService.class);
        controller.setUserProfileService(userProfileService);
        
        Http.Context context = mockPlayContext();
        Http.Context.current.set(context);
        
        Result result = controller.sendStudyParticipantsRoster();
        assertEquals(202, result.status());
        
        String content = Helpers.contentAsString(result);
        assertTrue(content.contains("A roster of study participants will be emailed"));

        verify(userProfileService).sendStudyParticipantRoster(study);
    }

}
