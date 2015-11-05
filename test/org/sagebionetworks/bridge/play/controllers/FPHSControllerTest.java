package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.FPHSService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

public class FPHSControllerTest {

    private FPHSController controller;
    private User user;
    private AuthenticationService authenticationService;
    private FPHSService fphsService;
    
    @Before
    public void before() {
        fphsService = mock(FPHSService.class);
        authenticationService = mock(AuthenticationService.class);
        
        controller = spy(new FPHSController());
        controller.setFPHSService(fphsService);
        controller.setAuthenticationService(authenticationService);
    }
    
    private JsonNode resultToJson(Result result) throws Exception {
        String json = Helpers.contentAsString(result);
        return BridgeObjectMapper.get().readTree(json);
    }
    
    private void setExternalIdentifierPost(ExternalIdentifier externalId) throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(externalId);
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
    }
    
    private void setData() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create();
        id1.setExternalId("foo");
        id1.setRegistered(true);
        
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create();
        id2.setExternalId("bar");
        
        List<FPHSExternalIdentifier> identifiers = Lists.newArrayList(id1, id2);
        
        when(fphsService.getExternalIdentifiers()).thenReturn(identifiers);
    }
    
    private void setUserSession() {
        user = mock(User.class);
        when(user.getHealthCode()).thenReturn("BBB");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
    }
    
    @Test
    public void verifyOK() throws Exception {
        setExternalIdentifierPost(new ExternalIdentifier("foo"));
        
        Result result = controller.verifyExternalIdentifier();
        JsonNode node = resultToJson(result);
        
        // No session is required
        verifyNoMoreInteractions(authenticationService);
        
        assertEquals("External identifier is valid.", node.get("message").asText());
    }
    
    @Test
    public void verifyFails() throws Exception {
        setExternalIdentifierPost(new ExternalIdentifier("foo"));
        when(fphsService.verifyExternalIdentifier(any())).thenThrow(new EntityNotFoundException(FPHSExternalIdentifier.class));
        
        try {
            controller.verifyExternalIdentifier();
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals("ExternalIdentifier not found.", e.getMessage());
        }
    }
    
    @Test
    public void registrationRequiresAuthenticatedConsentedUser() throws Exception {
        setExternalIdentifierPost(new ExternalIdentifier("foo"));
        
        try {
            controller.registerExternalIdentifier();
            fail("Should have thrown exception");
        } catch(NotAuthenticatedException e) {
            assertEquals("Not signed in.", e.getMessage());
        }
    }
    
    @Test
    public void registrationOK() throws Exception {
        setUserSession();
        setExternalIdentifierPost(new ExternalIdentifier("foo"));
        
        Result result = controller.registerExternalIdentifier();
        JsonNode node = resultToJson(result);
        
        assertEquals("External identifier added to user profile.", node.get("message").asText());
    }
    
    @Test
    public void gettingAllIdentifiersRequiresAdmin() throws Exception {
        setData();
        
        // There's a user, but not an admin user
        setUserSession();
        try {
            controller.getExternalIdentifiers();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            assertEquals("Caller does not have permission to access this service.", e.getMessage());
        }
        
        // Now when we have an admin user, we get back results
        when(user.isInRole(Roles.ADMIN)).thenReturn(true);
        
        Result result = controller.getExternalIdentifiers();
        JsonNode node = resultToJson(result);
        assertEquals(2, node.get("items").size());
        
        verify(fphsService).getExternalIdentifiers(); // can't not be true, really
    }
    
    @Test
    @Ignore
    public void updatingAllIdentifiersRequiresAdmin() throws Exception {
        setData();
        
        // There's a user, but not an admin user
        setUserSession();
        try {
            controller.updateExternalIdentifiers();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            assertEquals("Caller does not have permission to access this service.", e.getMessage());
        }
        
        // Now when we have an admin user, we get back results
        when(user.isInRole(Roles.ADMIN)).thenReturn(true);
        
        Result result = controller.updateExternalIdentifiers();
        JsonNode node = resultToJson(result);
        assertEquals(2, node.get("items").size());
        verify(fphsService).getExternalIdentifiers();
    }
    
    /*
    public Result updateExternalIdentifiers() throws Exception {
        getAuthenticatedSession(ADMIN);

        List<FPHSExternalIdentifier> externalIds = mapper.convertValue(requestToJSON(request()), externalIdTypeRef);
        fphsService.updateExternalIdentifiers(externalIds);
        
        return ok("External identifiers updated");
    }
     */
}
