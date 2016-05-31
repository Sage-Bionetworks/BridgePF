package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.*;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class UserSessionInfoTest {

    @Test
    public void userSessionInfoSerializesCorrectly() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("test@test.com")
                .withFirstName("first name")
                .withLastName("last name")
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE)
                .withId("user-identifier")
                .withRoles(Sets.newHashSet(RESEARCHER))
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withDataGroups(Sets.newHashSet("foo")).build();
        
        Map<SubpopulationGuid, ConsentStatus> map = TestUtils
                .toMap(new ConsentStatus("Consent", "AAA", true, true, false));
        
        UserSession session = new UserSession(participant);
        session.setConsentStatuses(map);
        session.setAuthenticated(true);
        session.setEnvironment(Environment.UAT);
        session.setInternalSessionToken("internal");
        session.setSessionToken("external");
        session.setStudyIdentifier(new StudyIdentifierImpl("study-identifier"));
        
        JsonNode node = UserSessionInfo.toJSON(session);
        assertEquals(session.isAuthenticated(), node.get("authenticated").asBoolean());
        assertEquals(ConsentStatus.isConsentCurrent(map), node.get("signedMostRecentConsent").asBoolean());
        assertEquals(ConsentStatus.isUserConsented(map), node.get("consented").asBoolean());
        assertEquals(participant.getSharingScope().name(), node.get("sharingScope").asText().toUpperCase());
        assertEquals(session.getSessionToken(), node.get("sessionToken").asText());
        assertEquals(participant.getEmail(), node.get("username").asText());
        assertEquals(participant.getEmail(), node.get("email").asText());
        assertEquals("researcher", node.get("roles").get(0).asText());
        assertEquals("foo", node.get("dataGroups").get(0).asText());
        assertEquals("staging", node.get("environment").asText());
        assertEquals(participant.getId(), node.get("id").asText());
        assertNull(node.get("healthCode"));
        assertNull(node.get("encryptedHealthCode"));
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
        assertEquals(19, node.size());
    }
    
}
