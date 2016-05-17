package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.*;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class UserSessionInfoTest {

    @Test
    public void userSessionInfoSerializesCorrectly() throws Exception {
        Map<SubpopulationGuid, ConsentStatus> map = TestUtils
                .toMap(new ConsentStatus("Consent", "AAA", true, true, false));
        
        User user = new User();
        user.setConsentStatuses(map);
        user.setEmail("test@test.com");
        user.setFirstName("first name");
        user.setLastName("last name");
        user.setHealthCode("healthCode");
        user.setId("user-identifier");
        user.setRoles(Sets.newHashSet(RESEARCHER));
        user.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        user.setStudyKey("study-identifier");
        user.setDataGroups(Sets.newHashSet("foo"));
        
        UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(Environment.UAT);
        session.setInternalSessionToken("internal");
        session.setSessionToken("external");
        session.setStudyIdentifier(new StudyIdentifierImpl("study-identifier"));
        session.setUser(user);
        
        UserSessionInfo info = new UserSessionInfo(session);
        
        String json = BridgeObjectMapper.get().writeValueAsString(info);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(session.isAuthenticated(), node.get("authenticated").asBoolean());
        assertEquals(user.hasSignedMostRecentConsent(), node.get("signedMostRecentConsent").asBoolean());
        assertEquals(user.doesConsent(), node.get("consented").asBoolean());
        assertEquals(user.getSharingScope().name(), node.get("sharingScope").asText().toUpperCase());
        assertEquals(session.getSessionToken(), node.get("sessionToken").asText());
        assertEquals(user.getEmail(), node.get("username").asText());
        assertEquals(user.getEmail(), node.get("email").asText());
        assertEquals("researcher", node.get("roles").get(0).asText());
        assertEquals("foo", node.get("dataGroups").get(0).asText());
        assertEquals("staging", node.get("environment").asText());
        assertEquals(user.getId(), node.get("id").asText());
        assertEquals("UserSessionInfo", node.get("type").asText());
        JsonNode consentMap = node.get("consentStatuses");
        
        JsonNode consentStatus = consentMap.get("AAA");
        assertEquals("Consent", consentStatus.get("name").asText());
        assertEquals("AAA", consentStatus.get("subpopulationGuid").asText());
        assertTrue(consentStatus.get("required").asBoolean());
        assertTrue(consentStatus.get("consented").asBoolean());
        assertFalse(consentStatus.get("signedMostRecentConsent").asBoolean());
        assertEquals("ConsentStatus", consentStatus.get("type").asText());
        assertEquals(6, consentStatus.size());
        
        // ... and no things that shouldn't be there
        assertEquals(14, node.size());
    }
    
}
