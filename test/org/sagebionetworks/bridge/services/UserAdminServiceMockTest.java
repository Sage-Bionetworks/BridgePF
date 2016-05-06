package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class UserAdminServiceMockTest {
    
    @Mock
    private AuthenticationService authenticationService;
    
    @Mock
    private ParticipantService participantService;
    
    @Mock
    private ConsentService consentService;

    private UserAdminService service;
    
    private User user; 
    
    @Before
    public void before() {
        service = new UserAdminService();
        service.setAuthenticationService(authenticationService);
        service.setConsentService(consentService);
        service.setParticipantService(participantService);

        // Make a user with multiple consent statuses, and just verify that we call the 
        // consent service that many times.
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        addConsentStatus(statuses, "subpop1");
        addConsentStatus(statuses, "subpop2");
        addConsentStatus(statuses, "subpop3");
        
        user = new User();
        user.setConsentStatuses(statuses);
        
        UserSession session = new UserSession();
        session.setUser(user);
        
        when(authenticationService.signIn(any(), any(), any())).thenReturn(session);
    }
    
    private void addConsentStatus(Map<SubpopulationGuid,ConsentStatus> statuses, String guid) {
        SubpopulationGuid subpopGuid = SubpopulationGuid.create("subpop1");
        ConsentStatus status = new ConsentStatus.Builder().withConsented(false).withGuid(subpopGuid).withName("subpop1").withRequired(true).build();
        statuses.put(subpopGuid, status);
    }
    
    @Test
    public void creatingUserConsentsToAllRequiredConsents() {
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        
        UserSession session = service.createUser(study, participant, null, true, true);
        
        verify(participantService).createParticipant(study, Sets.newHashSet(Roles.ADMIN), participant, false);
        
        for (SubpopulationGuid guid : session.getUser().getConsentStatuses().keySet()) {
            verify(consentService).consentToResearch(eq(study), eq(guid), eq(user), any(), eq(SharingScope.NO_SHARING), eq(false));
        }
    }
    
    @Test
    public void creatingUserWithSubpopulationOnlyConsentsToThatSubpopulation() {
        Study study = TestUtils.getValidStudy(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        SubpopulationGuid consentedGuid = Iterables.getFirst(user.getConsentStatuses().keySet(), null);
        
        UserSession session = service.createUser(study, participant, consentedGuid, true, true);
        
        verify(participantService).createParticipant(study, Sets.newHashSet(Roles.ADMIN), participant, false);
        
        // consented to the indicated subpopulation
        verify(consentService).consentToResearch(eq(study), eq(consentedGuid), eq(user), any(), eq(SharingScope.NO_SHARING), eq(false));
        // but not to the other two
        for (SubpopulationGuid guid : session.getUser().getConsentStatuses().keySet()) {
            if (guid != consentedGuid) {
                verify(consentService, never()).consentToResearch(eq(study), eq(guid), eq(user), any(), eq(SharingScope.NO_SHARING), eq(false));    
            }
        }
    }
    
}
