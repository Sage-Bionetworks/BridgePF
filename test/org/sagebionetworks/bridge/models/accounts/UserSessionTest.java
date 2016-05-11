package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class UserSessionTest {
    
    @Test
    public void canSerialize() throws Exception {
        SubpopulationGuid guid = SubpopulationGuid.create("subpop-guid");
        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(guid).withRequired(true).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(guid, status);
        
        StudyParticipant participant = new StudyParticipant.Builder()
            .withId("id")
            .withFirstName("firstName")
            .withLastName("lastName")
            .withEmail("email")
            .withHealthCode("healthCode")
            .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
            .withRoles(Sets.newHashSet(Roles.ADMIN))
            .withDataGroups(Sets.newHashSet("group1", "group2")).build();
        
        UserSession session = new UserSession(participant);
        session.setSessionToken("ABC");
        session.setInternalSessionToken("BBB");
        session.setAuthenticated(true);
        session.setEnvironment(Environment.PROD);
        session.setStudyIdentifier(new StudyIdentifierImpl("study-key"));
        session.setConsentStatuses(statuses);
        
        String json = new ObjectMapper().writeValueAsString(session);
        UserSession newSession = new ObjectMapper().readValue(json, UserSession.class);

        assertEquals(session.getSessionToken(), newSession.getSessionToken());
        assertEquals(session.getInternalSessionToken(), newSession.getInternalSessionToken());
        assertTrue(newSession.isAuthenticated());
        assertEquals(session.getEnvironment(), newSession.getEnvironment());
        assertEquals(session.getStudyIdentifier(), newSession.getStudyIdentifier());
        assertEquals(session.getParticipant(), newSession.getParticipant());
    }
    
    @Test
    public void cannotExposeHealthCode() throws Exception {
        UserSession session = new UserSession(new StudyParticipant.Builder().withHealthCode("123abc").build());
        
        String json = BridgeObjectMapper.get().writeValueAsString(session);
        assertFalse(json.contains("123abc"));
    }
    
    @Test
    public void testHealthCodeEncryption() throws IOException {
        
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withId("userId")
                .withHealthCode("123abc").build());
        String sessionSer = BridgeObjectMapper.get().writeValueAsString(session);
        assertNotNull(sessionSer);
        assertFalse("Health code should have been encrypted in the serialized string.",
                sessionSer.toLowerCase().contains("123abc"));
        
        UserSession sessionDe = BridgeObjectMapper.get().readValue(sessionSer, UserSession.class);
        assertNotNull(sessionDe);
        assertEquals("123abc", sessionDe.getHealthCode());
    }
    
    @Test
    public void userIsInRole() {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withRoles(Sets.newHashSet(Roles.ADMIN, Roles.DEVELOPER)).build());

        assertTrue(session.isInRole(Roles.DEVELOPER));
        assertFalse(session.isInRole((Roles)null));
    }
    
    @Test
    public void immutableConsentStatuses() {
        UserSession session = new UserSession();
        assertTrue(session.getConsentStatuses() instanceof ImmutableMap);
        
        session.setConsentStatuses(new HashMap<>());
        assertTrue(session.getConsentStatuses() instanceof ImmutableMap);
    }
    
    @Test
    public void userIsInRoleSet() {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withRoles(Sets.newHashSet(Roles.ADMIN, Roles.DEVELOPER)).build());
                
        assertTrue(session.isInRole(Roles.ADMINISTRATIVE_ROLES));
        assertFalse(session.isInRole((Set<Roles>)null));
        
        session = new UserSession();
        assertFalse(session.isInRole(Roles.ADMINISTRATIVE_ROLES));
    }
    
    @Test
    public void noConsentsProperlySetsBooleans() {
        UserSession session = new UserSession();
        assertFalse(session.doesConsent());
        assertFalse(session.hasSignedMostRecentConsent());
    }
    
    @Test
    public void hasUserConsentedWorks() {
        // Empty consent list... you are not considered consented
        UserSession session = new UserSession();
        session.setConsentStatuses(new HashMap<>());
        assertFalse(session.doesConsent());
        
        // All required consents are consented, even one that's not up-to-date
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertTrue(session.doesConsent());
        
        // A required consent is not consented
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, false, false),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(session.doesConsent());
    }
    
    @Test
    public void areConsentsUpToDateWorks() {
        // Empty consent list... you are not considered consented
        UserSession session = new UserSession();
        session.setConsentStatuses(new HashMap<>());
        assertFalse(session.hasSignedMostRecentConsent());
        
        // All required consents are consented, even one that's not up-to-date
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(session.hasSignedMostRecentConsent());
        
        // A required consent is not consented
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false),
            new ConsentStatus("Name", "guid2", true, false, false),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        assertFalse(session.hasSignedMostRecentConsent());
        
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, true),
            new ConsentStatus("Name", "guid3", false, false, false)
        ));
        // Again, we don't count optional consents, only required consents.
        assertTrue(session.hasSignedMostRecentConsent());
    }    
}
