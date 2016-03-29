package org.sagebionetworks.bridge.play.controllers;

import static play.test.Helpers.contentAsString;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.play.controllers.UserProfileController;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserProfileService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class UserProfileControllerTest {
    
    private static final Map<SubpopulationGuid,ConsentStatus> CONSENT_STATUSES_MAP = Maps.newHashMap();
    
    @Mock
    private ParticipantOptionsService optionsService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private UserSession session;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private UserProfileService userProfileService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private ViewCache viewCache;
    
    @Spy
    private UserProfileController controller;
    
    private User user;
    
    private Study study;
    
    @Captor
    private ArgumentCaptor<UserProfile> userProfileCaptor;
    
    @Captor
    private ArgumentCaptor<Set<String>> stringSetCaptor;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;

    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setDataGroups(Sets.newHashSet("group1", "group2"));
        study.setUserProfileAttributes(Sets.newHashSet("phone"));
        
        when(consentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUSES_MAP);
        when(studyService.getStudy((StudyIdentifier)any())).thenReturn(study);
        
        controller.setStudyService(studyService);
        controller.setParticipantOptionsService(optionsService);
        controller.setCacheProvider(cacheProvider);
        controller.setConsentService(consentService);
        controller.setUserProfileService(userProfileService);
        controller.setViewCache(viewCache);
        
        user = new User();
        user.setStudyKey(TEST_STUDY.getIdentifier());
        user.setHealthCode("healthCode");
        
        session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession();
    }
    
    @Test
    public void canSubmitExternalIdentifier() throws Exception {
        TestUtils.mockPlayContextWithJson("{\"identifier\":\"ABC-123-XYZ\"}");
        
        Result result = controller.createExternalIdentifier();
        assertEquals(200, result.status());
        assertEquals("application/json", result.contentType());
        assertEquals("{\"message\":\"External identifier added to user profile.\"}", contentAsString(result));
        
        verify(optionsService).setString(TEST_STUDY, "healthCode", EXTERNAL_IDENTIFIER, "ABC-123-XYZ");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void validDataGroupsCanBeAdded() throws Exception {
        Set<String> dataGroupSet = Sets.newHashSet("group1");
        TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"group1\"]}");
        
        Result result = controller.updateDataGroups();
        
        verify(optionsService).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), stringSetCaptor.capture());
        verify(consentService).getConsentStatuses(contextCaptor.capture());
        
        assertEquals(dataGroupSet, stringSetCaptor.getValue());
        assertEquals(dataGroupSet, contextCaptor.getValue().getUserDataGroups());
        assertEquals(dataGroupSet, session.getUser().getDataGroups());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());
    }
    
    @SuppressWarnings({"unchecked"})
    @Test
    public void invalidDataGroupsRejected() throws Exception {
        TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"completelyInvalidGroup\"]}");
        try {
            controller.updateDataGroups();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("DataGroups is invalid"));
            verify(optionsService, never()).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), any(Set.class));
        }
    }

    @Test
    public void canGetDataGroups() throws Exception {
        Set<String> dataGroupsSet = Sets.newHashSet("group1","group2");
        
        Map<String,String> map = Maps.newHashMap();
        map.put(DATA_GROUPS.name(), "group1,group2");
        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(map);
        
        when(optionsService.getOptions("healthCode")).thenReturn(lookup);
        
        Result result = controller.getDataGroups();
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals("DataGroups", node.get("type").asText());
        ArrayNode array = (ArrayNode)node.get("dataGroups");
        assertEquals(2, array.size());
        for (int i=0; i < array.size(); i++) {
            dataGroupsSet.contains(array.get(i).asText());
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void evenEmptyJsonActsOK() throws Exception {
        TestUtils.mockPlayContextWithJson("{}");
        
        Result result = controller.updateDataGroups();
        
        verify(optionsService).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), stringSetCaptor.capture());
        
        assertEquals(Sets.newHashSet(), stringSetCaptor.getValue());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());
    }
    
    @Test
    public void updateUserProfile() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'firstName',"+
                "'lastName':'lastName','email':'email@email.com','status':'unverified',"+
                "'username':'email@email.com','phone':'123-456-7890'}"));
        
        Result result = controller.updateUserProfile();
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals(200, result.status());
        assertEquals("Profile updated.", node.get("message").asText());
        
        verify(userProfileService).updateProfile(eq(study), eq(user), userProfileCaptor.capture());
        
        UserProfile profile = userProfileCaptor.getValue();
        assertEquals("firstName", profile.getFirstName());
        assertEquals("lastName", profile.getLastName());
        assertEquals("123-456-7890", profile.getAttribute("phone"));
        // Users can't submit JSON that changes their status or email
        assertNull(profile.getStatus());
        assertNull(profile.getEmail());
        
        verify(cacheProvider).setUserSession(session);
        verify(viewCache).removeView(any());
    }

}
