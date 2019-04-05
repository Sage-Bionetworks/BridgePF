package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.*;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Iterator;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class UserSessionInfoTest {

    @Test
    public void userSessionInfoSerializesCorrectly() throws Exception {
        DateTime timestamp = DateTime.now(DateTimeZone.UTC);
                
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("test@test.com")
                .withFirstName("first name")
                .withLastName("last name")
                .withNotifyByEmail(false)
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE)
                .withId("user-identifier")
                .withRoles(ImmutableSet.of(RESEARCHER))
                .withSubstudyIds(ImmutableSet.of("substudyA"))
                .withExternalIds(ImmutableMap.of("substudyA", "externalIdA"))
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withDataGroups(Sets.newHashSet("foo")).build();
        
        Map<SubpopulationGuid, ConsentStatus> map = TestUtils
                .toMap(new ConsentStatus("Consent", "AAA", true, true, false, timestamp.getMillis()));
        
        UserSession session = new UserSession(participant);
        session.setConsentStatuses(map);
        session.setAuthenticated(true);
        session.setEnvironment(Environment.UAT);
        session.setInternalSessionToken("internal");
        session.setSessionToken("external");
        session.setReauthToken("reauthToken");
        session.setStudyIdentifier(new StudyIdentifierImpl("study-identifier"));
        
        JsonNode node = UserSessionInfo.toJSON(session);
        assertEquals("first name", node.get("firstName").textValue());
        assertEquals("last name", node.get("lastName").textValue());
        assertEquals(session.isAuthenticated(), node.get("authenticated").booleanValue());
        assertEquals(ConsentStatus.isConsentCurrent(map), node.get("signedMostRecentConsent").booleanValue());
        assertEquals(ConsentStatus.isUserConsented(map), node.get("consented").booleanValue());
        assertEquals(participant.getSharingScope().name(), node.get("sharingScope").textValue().toUpperCase());
        assertEquals(session.getSessionToken(), node.get("sessionToken").textValue());
        assertEquals(participant.getEmail(), node.get("username").textValue());
        assertEquals(participant.getEmail(), node.get("email").textValue());
        assertEquals("researcher", node.get("roles").get(0).textValue());
        assertEquals("foo", node.get("dataGroups").get(0).textValue());
        assertEquals("staging", node.get("environment").textValue());
        assertEquals("reauthToken", node.get("reauthToken").textValue());
        assertEquals(participant.getId(), node.get("id").textValue());
        assertEquals(1, node.get("substudyIds").size());
        assertEquals("substudyA", node.get("substudyIds").get(0).textValue());
        assertFalse(node.get("notifyByEmail").booleanValue());
        assertNull(node.get("healthCode"));
        assertNull(node.get("encryptedHealthCode"));
        assertEquals("externalIdA", node.get("externalIds").get("substudyA").textValue());
        assertEquals("UserSessionInfo", node.get("type").asText());
        
        JsonNode consentMap = node.get("consentStatuses");
        
        JsonNode consentStatus = consentMap.get("AAA");
        assertEquals("Consent", consentStatus.get("name").textValue());
        assertEquals("AAA", consentStatus.get("subpopulationGuid").textValue());
        assertTrue(consentStatus.get("required").booleanValue());
        assertTrue(consentStatus.get("consented").booleanValue());
        assertFalse(consentStatus.get("signedMostRecentConsent").booleanValue());
        assertEquals(timestamp.toString(), consentStatus.get("signedOn").textValue());
        assertEquals("ConsentStatus", consentStatus.get("type").textValue());
        assertEquals(7, consentStatus.size());
        
        // ... and no things that shouldn't be there
        assertEquals(22, node.size());
    }
    
    @Test
    public void assertNoNullFields() {
        JsonNode node = UserSessionInfo.toJSON(new UserSession());
        for (Iterator<String> i = node.fieldNames(); i.hasNext();) {
            String fieldName = i.next();
            assertFalse(fieldName + " should not be null", node.get(fieldName).isNull());
        }
    }
    
}
