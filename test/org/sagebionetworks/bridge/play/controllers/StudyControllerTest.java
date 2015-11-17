package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.UploadCertificateService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

public class StudyControllerTest {

    private static final String PEM_TEXT = "-----BEGIN CERTIFICATE-----\nMIIExDCCA6ygAwIBAgIGBhCnnOuXMA0GCSqGSIb3DQEBBQUAMIGeMQswCQYDVQQG\nEwJVUzELMAkGA1UECAwCV0ExEDAOBgNVBAcMB1NlYXR0bGUxGTAXBgNVBAoMEFNh\nVlOwuuAxumMyIq5W4Dqk8SBcH9Y4qlk7\nEND CERTIFICATE-----";

    @Test(expected = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        UserSession session = mock(UserSession.class);
        StudyIdentifier studyId = mock(StudyIdentifier.class);
        when(session.getStudyIdentifier()).thenReturn(studyId);
        User user = new User();
        user.setHealthCode("healthCode");
        user.setRoles(Sets.newHashSet());
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
        User user = new User();
        user.setHealthCode("healthCode");
        user.setRoles(Sets.newHashSet(Roles.DEVELOPER));
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

}
