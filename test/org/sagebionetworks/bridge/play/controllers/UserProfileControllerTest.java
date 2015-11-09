package org.sagebionetworks.bridge.play.controllers;

import static play.test.Helpers.contentAsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.play.controllers.UserProfileController;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantOptionsServiceImpl;

import play.mvc.Http;
import play.mvc.Result;

public class UserProfileControllerTest {

    private ParticipantOptionsService optionsService;
    
    @Test
    public void canSubmitExternalIdentifier() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"identifier\":\"ABC-123-XYZ\"}"));
        
        UserProfileController controller = controllerForExternalIdTests();
                
        Result result = controller.createExternalIdentifier();
        assertEquals(200, result.status());
        assertEquals("application/json", result.contentType());
        assertEquals("{\"message\":\"External identifier added to user profile.\"}", contentAsString(result));
        
        verify(optionsService).setExternalIdentifier(TEST_STUDY, "healthCode", "ABC-123-XYZ");
    }
    
    @Test
    public void externalIdentifierVerifiesIdentifierExists() throws Exception {
        Http.Context.current.set(TestUtils.mockPlayContextWithJson("{\"identifier\":\"\"}"));
        
        UserProfileController controller = controllerForExternalIdTests();

        try {
            controller.createExternalIdentifier();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ExternalIdentifier is not valid.", e.getMessage());
        }
        verifyNoMoreInteractions(optionsService);
    }

    private UserSession mockSession() {
        User user = mock(User.class);
        when(user.getHealthCode()).thenReturn("healthCode");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(TEST_STUDY);
        return session;
    }
    
    private UserProfileController controllerForExternalIdTests() {
        optionsService = mock(ParticipantOptionsServiceImpl.class);
        
        UserProfileController controller = spy(new UserProfileController());
        controller.setParticipantOptionsService(optionsService);
        doReturn(mockSession()).when(controller).getAuthenticatedSession();

        return controller;
    }
}
