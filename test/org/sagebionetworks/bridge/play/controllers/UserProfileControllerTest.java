package org.sagebionetworks.bridge.play.controllers;

import static play.test.Helpers.contentAsString;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.play.controllers.UserProfileController;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
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
    private UserSession session;
    
    private Study study;
    
    private UserProfileController controller;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setDataGroups(Sets.newHashSet("group1", "group2"));

        when(consentService.getConsentStatuses(any())).thenReturn(CONSENT_STATUSES_MAP);
        
        when(studyService.getStudy((StudyIdentifier)any())).thenReturn(study);
        
        controller = spy(new UserProfileController());
        controller.setStudyService(studyService);
        controller.setParticipantOptionsService(optionsService);
        controller.setCacheProvider(cacheProvider);
        controller.setExternalIdService(externalIdService);
        controller.setConsentService(consentService);
        
        User user = new User();
        user.setStudyKey(TEST_STUDY.getIdentifier());
        user.setHealthCode("healthCode");
        
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
        
        verify(externalIdService).assignExternalId(study, "ABC-123-XYZ", "healthCode");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void validDataGroupsCanBeAdded() throws Exception {
        Set<String> dataGroupSet = Sets.newHashSet("group1");
        TestUtils.mockPlayContextWithJson("{\"dataGroups\":[\"group1\"]}");
        
        Result result = controller.updateDataGroups();
        
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<CriteriaContext> contextCaptor = ArgumentCaptor.forClass(CriteriaContext.class);
        
        verify(optionsService).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), captor.capture());
        verify(consentService).getConsentStatuses(contextCaptor.capture());
        
        Set<String> dataGroups = (Set<String>)captor.getValue();
        assertEquals(dataGroupSet, dataGroups);
        
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
        
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(optionsService).setStringSet(eq(TEST_STUDY), eq("healthCode"), eq(DATA_GROUPS), captor.capture());
        
        Set<String> dataGroups = (Set<String>)captor.getValue();
        assertEquals(Sets.newHashSet(), dataGroups);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("Data groups updated.", node.get("message").asText());
    }
}
