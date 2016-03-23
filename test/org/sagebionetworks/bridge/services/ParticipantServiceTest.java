package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantServiceTest {

    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setIdentifier("test-study");
        STUDY.setHealthCodeExportEnabled(true);
        STUDY.setUserProfileAttributes(Sets.newHashSet("attr1","attr2"));
        STUDY.setDataGroups(Sets.newHashSet("group1","group2"));
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
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Before
    public void before() {
        participantService = new ParticipantService();
        participantService.setAccountDao(accountDao);
        participantService.setParticipantOptionsService(optionsService);
        participantService.setSubpopulationService(subpopService);
        participantService.setHealthCodeService(healthCodeService);
        participantService.setUserConsent(consentService);
        participantService.setCacheProvider(cacheProvider);
    }
    
    @Test
    public void getPagedAccountSummaries() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50, "foo");
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50, "foo"); 
    }
    
    @Test
    public void getPagedAccountSummariesWithoutEmailFilterOK() {
        participantService.getPagedAccountSummaries(STUDY, 1100, 50, null);
        
        verify(accountDao).getPagedAccountSummaries(STUDY, 1100, 50, null); 
    }
    
    @Test(expected = NullPointerException.class)
    public void badStudyRejected() {
        participantService.getPagedAccountSummaries(null, 0, 100, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void offsetByCannotBeNegative() {
        participantService.getPagedAccountSummaries(STUDY, -1, 100, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void limitToCannotBeNegative() {
        participantService.getPagedAccountSummaries(STUDY, 0, -100, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void limitToCannotBeGreaterThan250() {
        participantService.getPagedAccountSummaries(STUDY, 0, 251, null);
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
        when(lookup.getOrderedStringSet(LANGUAGES)).thenReturn(TestUtils.newLinkedHashSet("fr","de"));
        when(optionsService.getOptions("healthCode")).thenReturn(lookup);
        
        // Get the participant
        StudyParticipant participant = participantService.getParticipant(STUDY, email);
        
        assertEquals("firstName", participant.getFirstName());
        assertEquals("lastName", participant.getLastName());
        assertTrue(participant.isNotifyByEmail());
        assertEquals(Sets.newHashSet("group1","group2"), participant.getDataGroups());
        assertEquals("externalId", participant.getExternalId());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, participant.getSharingScope());
        assertEquals("healthCode", participant.getHealthCode());
        assertEquals("email@email.com", participant.getEmail());
        assertEquals(TestUtils.newLinkedHashSet("fr","de"), participant.getLanguages());
        
        assertNull(participant.getAttributes().get("attr1"));
        assertEquals("anAttribute2", participant.getAttributes().get("attr2"));
        
        List<UserConsentHistory> retrievedHistory1 = participant.getConsentHistories().get(subpop1.getGuidString());
        assertEquals(2, retrievedHistory1.size());
        assertEquals(history1, retrievedHistory1.get(0));
        assertEquals(history2, retrievedHistory1.get(1));
        
        List<UserConsentHistory> retrievedHistory2 = participant.getConsentHistories().get(subpop2.getGuidString());
        assertTrue(retrievedHistory2.isEmpty());
    }

    @Test(expected = EntityNotFoundException.class)
    public void getParticipantEmailDoesNotExist() {
        when(accountDao.getAccount(STUDY, "email@email.com")).thenReturn(null);
        
        participantService.getParticipant(STUDY, "email@email.com");
    }
    
    @Test
    public void updateParticipantOptions() {
        String email = "email@email.com";
        when(account.getHealthId()).thenReturn("healthId");
        when(healthId.getCode()).thenReturn("healthCode");
        when(accountDao.getAccount(STUDY, email)).thenReturn(account);
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);
        
        Map<ParticipantOption,String> options = Maps.newHashMap();
        
        participantService.updateParticipantOptions(STUDY, email, options);
        
        verify(optionsService).setAllOptions(STUDY, "healthCode", options);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateParticipantOptionsInvalidDataGroup() {
        String email = "email@email.com";
        when(accountDao.getAccount(STUDY, email)).thenReturn(account);
        when(account.getHealthId()).thenReturn("healthId");
        when(healthId.getCode()).thenReturn("healthCode");
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);

        Map<ParticipantOption,String> options = Maps.newHashMap();
        options.put(ParticipantOption.DATA_GROUPS, "group1,group3");
        
        participantService.updateParticipantOptions(STUDY, email, options);
    }
    
    @Test(expected = BadRequestException.class)
    public void cannotUpdateParticipantOptionsYet() {
        String email = "email@email.com";
        when(account.getHealthId()).thenReturn(null);
        when(healthId.getCode()).thenReturn(null);
        when(accountDao.getAccount(STUDY, email)).thenReturn(account);
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);
        
        Map<ParticipantOption,String> options = Maps.newHashMap();
        participantService.updateParticipantOptions(STUDY, email, options);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void accountDoesNotExist() {
        String email = "email@email.com";
        when(accountDao.getAccount(STUDY, email)).thenReturn(null);
        
        Map<ParticipantOption,String> options = Maps.newHashMap();
        participantService.updateParticipantOptions(STUDY, email, options);
    }
    
    @Test
    public void updateUserProfile() {
        UserProfile profile = new UserProfile();
        profile.setFirstName("first name");
        profile.setLastName("last name");
        profile.setAttribute("attr1", "new attr1");
        profile.setAttribute("attr2", "new attr2");

        // Need an account object on which we can actually set the values...
        Account acct = new SimpleAccount();
        when(accountDao.getAccount(STUDY, "email@email.com")).thenReturn(acct);
        
        participantService.updateProfile(STUDY, "email@email.com", profile);
        
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountDao).updateAccount(eq(STUDY), captor.capture());
        
        Account capturedAccount = captor.getValue();
        assertEquals("first name", capturedAccount.getFirstName());
        assertEquals("last name", capturedAccount.getLastName());
        assertEquals("new attr1", capturedAccount.getAttribute("attr1"));
        assertEquals("new attr2", capturedAccount.getAttribute("attr2"));
    }

    @Test(expected = EntityNotFoundException.class)
    public void updateUserProfileUserDoesNotExist() {
        when(accountDao.getAccount(STUDY, "email@email.com")).thenReturn(null);
        
        UserProfile profile = new UserProfile();
        participantService.updateProfile(STUDY, "email@email.com", profile);
    }
    
    @Test
    public void signUserOut() {
        when(accountDao.getAccount(STUDY, "email@email.com")).thenReturn(account);
        when(account.getId()).thenReturn("userId");
        
        participantService.signUserOut(STUDY, "email@email.com");
        
        verify(accountDao).getAccount(STUDY, "email@email.com");
        verify(cacheProvider).removeSessionByUserId("userId");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void signOutUserWhoDoesNotExist() {
        when(accountDao.getAccount(STUDY, "email@email.com")).thenReturn(null);
        
        participantService.signUserOut(STUDY, "email@email.com");
    }
    
}
