package controllers;

// Absolutely no idea what the replacements are supposed to be for these deprecated methods.
// Later Play versions specify this but those new methods are not in 2.3.8.
import static play.test.Helpers.status;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantOptionsServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.mvc.Http;
import play.mvc.Result;

public class UserProfileControllerTest {

    private StudyIdentifier studyIdentifier = new StudyIdentifierImpl("api");
    
    @Test
    public void canSubmitExternalIdentifier() throws Exception {
        Http.Context.current.set(mockContext("{\"identifier\":\"ABC-123-XYZ\"}"));
        
        UserProfileController controller = controllerForExternalIdTests();
                
        Result result = controller.createExternalIdentifier();
        assertEquals(200, status(result));
        assertEquals("application/json", contentType(result));
        assertEquals("{\"message\":\"External identifier added to user profile.\"}", contentAsString(result));
        
        verify(controller.optionsService).setOption(studyIdentifier, "healthCode", 
            ParticipantOption.EXTERNAL_IDENTIFIER, "ABC-123-XYZ");
    }
    
    @Test
    public void externalIdentifierVerifiesIdentifierExists() throws Exception {
        Http.Context.current.set(mockContext("{\"identifier\":\"\"}"));
        
        UserProfileController controller = controllerForExternalIdTests();

        try {
            controller.createExternalIdentifier();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ExternalIdentifier is not valid.", e.getMessage());
        }
        verifyNoMoreInteractions(controller.optionsService);
    }
    
    private Http.Context mockContext(String json) throws Exception {
        JsonNode node = new ObjectMapper().readTree(json);
        
        Http.RequestBody body = mock(Http.RequestBody.class);
        when(body.asJson()).thenReturn(node);
        
        Http.Request request = mock(Http.Request.class);
        when(request.body()).thenReturn(body);

        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);

        return context;
    }    
    
    private UserSession mockSession() {
        User user = mock(User.class);
        when(user.getHealthCode()).thenReturn("healthCode");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(studyIdentifier);
        return session;
    }
    
    private UserProfileController controllerForExternalIdTests() {
        ParticipantOptionsService optionsService = mock(ParticipantOptionsServiceImpl.class);
        
        UserProfileController controller = spy(new UserProfileController());
        doReturn(mockSession()).when(controller).getAuthenticatedSession();
        controller.setParticipantOptionsService(optionsService);

        return controller;
    }
}
