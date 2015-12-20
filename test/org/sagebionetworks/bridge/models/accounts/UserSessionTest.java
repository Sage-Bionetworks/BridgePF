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
        
        User user = new User();
        user.setId("id");
        user.setUsername("username");
        user.setFirstName("firstName");
        user.setLastName("lastName");
        user.setEmail("email");
        user.setHealthCode("healthCode");
        user.setStudyKey("study-key-2");
        user.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        user.setRoles(Sets.newHashSet(Roles.ADMIN));
        user.setDataGroups(Sets.newHashSet("group1", "group2"));
        user.setConsentStatuses(statuses);
        
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        session.setInternalSessionToken("BBB");
        session.setAuthenticated(true);
        session.setEnvironment(Environment.PROD);
        session.setStudyIdentifier(new StudyIdentifierImpl("study-key"));
        session.setUser(user);
        
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
