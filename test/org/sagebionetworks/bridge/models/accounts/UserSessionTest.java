package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertEquals(session.getUser(), newSession.getUser());
    }
    
}
