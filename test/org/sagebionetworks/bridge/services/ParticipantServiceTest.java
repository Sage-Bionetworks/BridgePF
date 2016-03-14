package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant2;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {

    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setIdentifier("test-study");
        STUDY.setHealthCodeExportEnabled(true);
        STUDY.setUserProfileAttributes(Sets.newHashSet("attr1","attr2"));        
    }
    
    private ParticipantService participantService;
    
    @Mock
    private AccountDao accountDao;
    
    @Mock
    private ParticipantOptionsService optionsService;
    
    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private HealthCodeService healthCodeService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private Account account;
    
    @Mock
    private HealthId healthId;
    
    @Mock
    private ParticipantOptionsLookup lookup;
    
    @Before
    public void before() {
        participantService = new ParticipantService();
        participantService.setAccountDao(accountDao);
        participantService.setParticipantOptionsService(optionsService);
        participantService.setSubpopulationService(subpopService);
        participantService.setHealthCodeService(healthCodeService);
        participantService.setUserConsent(consentService);
    }
    
    @Test
    public void getPagedAccountSummaries() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50); 
    }
    
    @Test(expected = NullPointerException.class)
    public void badStudyRejected() {
        participantService.getPagedAccountSummaries(null, 0, 100);
    }
    
    @Test(expected = BadRequestException.class)
    public void offsetByCannotBeNegative() {
        participantService.getPagedAccountSummaries(STUDY, -1, 100);
    }
    
    @Test(expected = BadRequestException.class)
    public void limitToCannotBeNegative() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100);
    }
    
    @Test(expected = BadRequestException.class)
    public void limitToCannotBeGreaterThan250() {
        participantService.getPagedAccountSummaries(STUDY, 0, 251);
    }
    
    @Test
    public void getStudyParticipant() {
        // A lot of mocks have to be set up first, this call aggregates almost everything we know about the user
        String email = "email@email.com";
        
        when(account.getHealthId()).thenReturn("healthId");
        when(account.getFirstName()).thenReturn("firstName");
        when(account.getLastName()).thenReturn("lastName");
        when(account.getEmail()).thenReturn("email@email.com");
        when(account.getAttribute("attr2")).thenReturn("anAttribute2");
        
        when(healthId.getCode()).thenReturn("healthCode");
        when(accountDao.getAccount(STUDY, email)).thenReturn(account);
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);
        
        List<Subpopulation> subpopulations = Lists.newArrayList();
        // Two subpopulations for mocking.
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setGuidString("guid1");
        subpopulations.add(subpop1);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setGuidString("guid2");
        subpopulations.add(subpop2);
        when(subpopService.getSubpopulations(STUDY.getStudyIdentifier())).thenReturn(subpopulations);
        
        List<UserConsentHistory> histories1 = Lists.newArrayList();
        UserConsentHistory history1 = new UserConsentHistory.Builder()
                .withBirthdate("2002-02-02")
                .withConsentCreatedOn(1L)
                .withName("Test User")
                .withSubpopulationGuid(subpop1.getGuid())
                .withWithdrewOn(2L).build();
        histories1.add(history1);
        
        // Add another one, we don't need to test that it is the same.
        UserConsentHistory history2 = new UserConsentHistory.Builder().build();
        histories1.add(history2);
        
        List<UserConsentHistory> histories2 = Lists.newArrayList();
        
        when(consentService.getUserConsentHistory(STUDY, subpop1.getGuid(), "healthCode", "email@email.com")).thenReturn(histories1);
        when(consentService.getUserConsentHistory(STUDY, subpop2.getGuid(), "healthCode", "email@email.com")).thenReturn(histories2);
        
        when(lookup.getEnum(SHARING_SCOPE, SharingScope.class)).thenReturn(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        when(lookup.getBoolean(EMAIL_NOTIFICATIONS)).thenReturn(true);
        when(lookup.getString(EXTERNAL_IDENTIFIER)).thenReturn("externalId");
        when(lookup.getStringSet(DATA_GROUPS)).thenReturn(TestUtils.newLinkedHashSet("group1","group2"));
        when(optionsService.getOptions("healthCode")).thenReturn(lookup);
        
        // Get the participant
        StudyParticipant2 participant = participantService.getParticipant(STUDY, email);
        
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), participant.getDataGroups());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, participant.getSharingScope());
        assertEquals("healthCode", participant.getHealthCode());
        assertEquals("email@email.com", participant.getEmail());
        
        assertNull(participant.getAttributes().get("attr1"));
        assertEquals("anAttribute2", participant.getAttributes().get("attr2"));
        
        List<UserConsentHistory> retrievedHistory1 = participant.getConsentHistories().get(subpop1.getGuidString());
        assertEquals(2, retrievedHistory1.size());
        assertEquals(history1, retrievedHistory1.get(0));
        assertEquals(history2, retrievedHistory1.get(1));
        
        List<UserConsentHistory> retrievedHistory2 = participant.getConsentHistories().get(subpop2.getGuidString());
        assertTrue(retrievedHistory2.isEmpty());
    }
}
