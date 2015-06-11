package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class UserSessionInfoTest {

    @Test
    public void userSessionInfoSerializesCorrectly() throws Exception {
        User user = new User();
        user.setConsent(false);
        user.setEmail("test@test.com");
        user.setFirstName("first name");
        user.setLastName("last name");
        user.setHealthCode("healthCode");
        user.setId("user-identifier");
        user.setRoles(Sets.newHashSet("test_role"));
        user.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        user.setSignedMostRecentConsent(false);
        user.setStudyKey("study-identifier");
        user.setUsername("username");
        
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
        assertEquals(user.getUsername(), node.get("username").asText());
        assertEquals("staging", node.get("environment").asText());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        // ... and no things that shouldn't be there
        assertEquals(9, node.size());
    }
    
}
