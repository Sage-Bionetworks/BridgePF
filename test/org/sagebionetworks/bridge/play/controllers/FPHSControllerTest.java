package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.FPHSExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.FPHSService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

public class FPHSControllerTest {

    private FPHSController controller;
    private User user;
    private AuthenticationService authenticationService;
    private FPHSService fphsService;
    private ConsentService consentService;
    
    @Before
    public void before() {
        fphsService = mock(FPHSService.class);
        authenticationService = mock(AuthenticationService.class);
        consentService = mock(ConsentService.class);
        
        controller = spy(new FPHSController());
        controller.setFPHSService(fphsService);
        controller.setAuthenticationService(authenticationService);
        controller.setConsentService(consentService);
        controller.setCacheProvider(mock(CacheProvider.class));
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
    
    private void setFPHSExternalIdentifiersPost(List<FPHSExternalIdentifier> list) throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
    }
    
    private void setData() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create("foo");
        id1.setRegistered(true);
        
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create("bar");
        
        List<FPHSExternalIdentifier> identifiers = Lists.newArrayList(id1, id2);
        
        when(fphsService.getExternalIdentifiers()).thenReturn(identifiers);
    }
    
    private User setUserSession() {
        user = new User();
        user.setHealthCode("BBB");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("test-study"));
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
        return user;
    }
    
    @Test
    public void verifyOK() throws Exception {
        Result result = controller.verifyExternalIdentifier("foo");
        JsonNode node = resultToJson(result);
        
        // No session is required
        verifyNoMoreInteractions(authenticationService);
        assertEquals("foo", node.get("externalId").asText());
        assertEquals(200, result.status());
    }
    
    @Test
    public void verifyFails() throws Exception {
        doThrow(new EntityNotFoundException(FPHSExternalIdentifier.class)).when(fphsService).verifyExternalIdentifier(any());
        
        try {
            controller.verifyExternalIdentifier("foo");
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals("ExternalIdentifier not found.", e.getMessage());
        }
    }
    
    @Test
    public void verifyFailsWhenNull() throws Exception {
        doThrow(new InvalidEntityException("ExternalIdentifier cannot be blank, null or missing.")).when(fphsService).verifyExternalIdentifier(any());
        
        try {
            controller.verifyExternalIdentifier(null);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ExternalIdentifier cannot be blank, null or missing.", e.getMessage());
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
        User user = setUserSession();
        setExternalIdentifierPost(new ExternalIdentifier("foo"));

        Result result = controller.registerExternalIdentifier();
        JsonNode node = resultToJson(result);
        assertEquals("External identifier added to user profile.", node.get("message").asText());

        assertEquals(Sets.newHashSet("football_player"), user.getDataGroups());
        verify(consentService).getConsentStatuses(any(ScheduleContext.class));
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
        user.setRoles(Sets.newHashSet(Roles.ADMIN));
        
        Result result = controller.getExternalIdentifiers();
        JsonNode node = resultToJson(result);
        
        assertEquals(2, node.get("items").size());
        
        verify(fphsService).getExternalIdentifiers();
    }
    
    @Test
    public void addIdentifiersRequiresAdmin() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create("AAA");
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create("BBB");
        setFPHSExternalIdentifiersPost(Lists.newArrayList(id1, id2));
        
        // There's a user, but not an admin user
        setUserSession();
        try {
            controller.addExternalIdentifiers();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            assertEquals("Caller does not have permission to access this service.", e.getMessage());
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void addIdentifiersOK() throws Exception {
        FPHSExternalIdentifier id1 = FPHSExternalIdentifier.create("AAA");
        FPHSExternalIdentifier id2 = FPHSExternalIdentifier.create("BBB");
        setFPHSExternalIdentifiersPost(Lists.newArrayList(id1, id2));
        
        setUserSession();
        
        // Now when we have an admin user, we get back results
        user.setRoles(Sets.newHashSet(Roles.ADMIN));
        Result result = controller.addExternalIdentifiers();
        
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(fphsService).addExternalIdentifiers(captor.capture());
        
        List<FPHSExternalIdentifier> passedList = (List<FPHSExternalIdentifier>)captor.getValue();
        assertEquals(2, passedList.size());
        
        JsonNode node = resultToJson(result);
        assertEquals("External identifiers added.", node.get("message").asText());
    }
}
