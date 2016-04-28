package org.sagebionetworks.bridge.play.controllers;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.play.controllers.UserProfileController;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class UserProfileControllerTest {
    
    private static final Map<SubpopulationGuid,ConsentStatus> CONSENT_STATUSES_MAP = Maps.newHashMap();
    private static final Set<String> TEST_STUDY_DATA_GROUPS = Sets.newHashSet("group1", "group2");
    private static final Set<String> TEST_STUDY_ATTRIBUTES = Sets.newHashSet("foo","bar"); 
    private static final Set<Roles> NO_ROLES = Sets.newHashSet();
    private static final String ID = "ABC";
    private static final String HEALTH_CODE = "healthCode";
    
    @Mock
    private ParticipantOptionsService optionsService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private ExternalIdService externalIdService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private ParticipantService participantService;
    
    @Mock
    private UserSession session;
    
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    private ArgumentCaptor<Set<String>> stringSetCaptor;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    private Study study;
    
    private UserProfileController controller;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setDataGroups(TEST_STUDY_DATA_GROUPS);
        study.setUserProfileAttributes(TEST_STUDY_ATTRIBUTES);

        when(consentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUSES_MAP);
        
        when(studyService.getStudy((StudyIdentifier)any())).thenReturn(study);
        
        ViewCache viewCache = new ViewCache();
        viewCache.setCacheProvider(cacheProvider);
        
        controller = spy(new UserProfileController());
        controller.setStudyService(studyService);
        controller.setParticipantOptionsService(optionsService);
        controller.setCacheProvider(cacheProvider);
        controller.setExternalIdService(externalIdService);
        controller.setConsentService(consentService);
        controller.setParticipantService(participantService);
        controller.setViewCache(viewCache);
        
        User user = new User();
        user.setStudyKey(TEST_STUDY.getIdentifier());
        user.setHealthCode(HEALTH_CODE);
        user.setId(ID);
        
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession();
    }
    
    @Test
    public void getUserProfile() throws Exception {
        Map<String,String> attributes = Maps.newHashMap();
        attributes.put("bar","baz");
        StudyParticipant participant = new StudyParticipant.Builder().withLastName("Last")
                .withFirstName("First").withEmail("email@email.com").withAttributes(attributes).build();
        
        doReturn(participant).when(participantService).getParticipant(study, Sets.newHashSet(), ID);
        
        Result result = controller.getUserProfile();
        assertEquals(200, result.status());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        verify(participantService).getParticipant(study, Sets.newHashSet(), ID);
        
        assertEquals("First", node.get("firstName").asText());
        assertEquals("Last", node.get("lastName").asText());
        assertEquals("email@email.com", node.get("email").asText());
        assertEquals("email@email.com", node.get("username").asText());
        assertEquals("baz", node.get("bar").asText());
        assertEquals("UserProfile", node.get("type").asText());
    }
    
    @Test
    public void updateUserProfile() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("originalId").build();
        doReturn(participant).when(participantService).getParticipant(study, Sets.newHashSet(), ID);
        
        // This has a field that should not be passed to the StudyParticipant, because it didn't exist before
        // (externalId)
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'firstName':'First','lastName':'Last',"+
                "'username':'email@email.com','foo':'belgium','externalId':'updatedId','type':'UserProfile'}"));
        
        Result result = controller.updateUserProfile();
        TestUtils.assertResult(result, 200, "Profile updated.");

        verify(participantService).updateParticipant(eq(study), eq(Sets.newHashSet()), eq(ID), participantCaptor.capture());
        
        StudyParticipant persisted = participantCaptor.getValue();
        assertEquals("First", persisted.getFirstName());
        assertEquals("Last", persisted.getLastName());
        assertEquals("originalId", persisted.getExternalId()); // not changed by the JSON submitted
        assertEquals("belgium", persisted.getAttributes().get("foo"));
    }
    
    @Test
    public void canSubmitExternalIdentifier() throws Exception {
        TestUtils.mockPlayContextWithJson("{\"identifier\":\"ABC-123-XYZ\"}");
                
        Result result = controller.createExternalIdentifier();
        assertResult(result, 200, "External identifier added to user profile.");
        
        verify(externalIdService).assignExternalId(study, "ABC-123-XYZ", HEALTH_CODE);
    }

    @Test
    public void validDataGroupsCanBeAdded() throws Exception {
        StudyParticipant existing = new StudyParticipant.Builder().withFirstName("First").build();
        doReturn(existing).when(participantService).getParticipant(study, NO_ROLES, ID);
        
        Set<String> dataGroupSet = Sets.newHashSet("group1");
        TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"group1\"]}");
        
        Result result = controller.updateDataGroups();
        assertResult(result, 200, "Data groups updated.");
        
        verify(participantService).updateParticipant(eq(study), eq(NO_ROLES), eq(ID), participantCaptor.capture());
        verify(consentService).getConsentStatuses(contextCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(dataGroupSet, participant.getDataGroups());
        assertEquals("First", participant.getFirstName());
        
        assertEquals(dataGroupSet, contextCaptor.getValue().getUserDataGroups());
        assertEquals(dataGroupSet, session.getUser().getDataGroups());
    }
    
    // Validation is no longer done in the controller, but verify that user is not changed
    // when the service throws an InvalidEntityException.
    @Test
    public void invalidDataGroupsRejected() throws Exception {
        StudyParticipant existing = new StudyParticipant.Builder().withFirstName("First").build();
        doReturn(existing).when(participantService).getParticipant(study, NO_ROLES, ID);
        doThrow(new InvalidEntityException("Invalid data groups")).when(participantService).updateParticipant(eq(study), eq(NO_ROLES), eq(ID), any());
        
        TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"completelyInvalidGroup\"]}");
        try {
            controller.updateDataGroups();
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(Sets.newHashSet(), session.getUser().getDataGroups());
        }
    }

    @Test
    public void canGetDataGroups() throws Exception {
        Map<String,String> map = Maps.newHashMap();
        map.put(DATA_GROUPS.name(), "group1,group2");
        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(map);
        
        when(optionsService.getOptions(HEALTH_CODE)).thenReturn(lookup);
        
        Result result = controller.getDataGroups();
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        
        assertEquals("DataGroups", node.get("type").asText());
        ArrayNode array = (ArrayNode)node.get("dataGroups");
        assertEquals(2, array.size());
        for (int i=0; i < array.size(); i++) {
            TEST_STUDY_DATA_GROUPS.contains(array.get(i).asText());
        }
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void evenEmptyJsonActsOK() throws Exception {
        StudyParticipant existing = new StudyParticipant.Builder().withFirstName("First").build();
        doReturn(existing).when(participantService).getParticipant(study, NO_ROLES, ID);
        TestUtils.mockPlayContextWithJson("{}");
        
        Result result = controller.updateDataGroups();
        assertResult(result, 200, "Data groups updated.");
        
        verify(participantService).updateParticipant(eq(study), eq(NO_ROLES), eq(ID), participantCaptor.capture());
        
        StudyParticipant updated = participantCaptor.getValue();
        assertTrue(updated.getDataGroups().isEmpty());
        assertEquals("First", updated.getFirstName());
    }
}
