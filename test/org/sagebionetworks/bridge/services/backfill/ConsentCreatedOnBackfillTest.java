package org.sagebionetworks.bridge.services.backfill;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class ConsentCreatedOnBackfillTest {
    
    private static final String API = "api";
    private static final String USER_ID = "user-id";
    private static final String HEALTH_CODE = "health-code";
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("subpop-guid");
    
    private static final long NOW = 1467742872087L;
    private static final long USER_CREATED_ON_TIMESTAMP = 1467741000000L;
    private static final long CONSENT_CREATED_ON_TIMESTAMP = 1467741848921L;
    private static final long SIGNED_ON_TIMESTAMP = 1467741906474L;

    @Mock
    private AccountDao accountDao;
    @Mock
    private BackfillDao backfillDao;
    @Mock
    private BackfillRecordFactory backfillRecordFactory;
    @Mock
    private DistributedLockDao lockDao;
    @Mock
    private StudyConsentDao studyConsentDao;
    @Mock
    private StudyService studyService;
    @Mock
    private UserConsentDao userConsentDao;
    
    @Mock
    private Study study;
    @Mock
    private Account account;
    @Mock
    private BackfillTask task;
    @Mock
    private BackfillCallback callback;
    @Mock
    private UserConsent userConsent;
    @Mock
    private StudyConsent studyConsent;
    
    @Captor
    private ArgumentCaptor<Account> accountCaptor;
    
    private Map<SubpopulationGuid,List<ConsentSignature>> map;

    private AccountSummary summary;
    
    @Spy
    ConsentCreatedOnBackfill backfill;

    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW);
        
        doReturn(Lists.newArrayList("api")).when(backfill).getStudies();
        backfill.setAccountDao(accountDao);
        backfill.setBackfillDao(backfillDao);
        backfill.setBackfillRecordFactory(backfillRecordFactory);
        backfill.setDistributedLockDao(lockDao);
        backfill.setStudyConsentDao(studyConsentDao);
        backfill.setStudyService(studyService);
        backfill.setUserConsentDao(userConsentDao);
        
        // Mock study service
        List<Study> studies = Lists.newArrayList(study);
        doReturn("id").when(study).getIdentifier();
        doReturn(studies).when(studyService).getStudies();
        doReturn(study).when(studyService).getStudy(API); // for variant doing one study at a time.
        
        // Mock account
        doReturn(HEALTH_CODE).when(account).getHealthCode();
        doReturn(USER_ID).when(account).getId();
        doReturn(new DateTime(USER_CREATED_ON_TIMESTAMP)).when(account).getCreatedOn();
        map = Maps.newHashMap();
        map.put(SUBPOP_GUID, Lists.newArrayList());
        doReturn(map).when(account).getAllConsentSignatureHistories();
        doReturn(map.get(SUBPOP_GUID)).when(account).getConsentSignatureHistory(SUBPOP_GUID);
        when(account.getActiveConsentSignature(SUBPOP_GUID)).thenAnswer(guid -> {
            return map.get(SUBPOP_GUID).get(0);
        });
        
        // Mock account DAO
        doReturn(account).when(accountDao).getAccount(study, USER_ID);
        
        // Mock account summaries
        summary = new AccountSummary(null, null, null, USER_ID, null, null, null);
        List<AccountSummary> summaries = Lists.newArrayList(summary);
        doReturn(summaries.iterator()).when(accountDao).getStudyAccounts(study);
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private void createUserConsent() {
        doReturn(SIGNED_ON_TIMESTAMP).when(userConsent).getSignedOn();
        doReturn(CONSENT_CREATED_ON_TIMESTAMP).when(userConsent).getConsentCreatedOn();
        when(userConsentDao.getUserConsent(eq(HEALTH_CODE), eq(SUBPOP_GUID), anyLong())).thenAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            if ((long)args[2] == SIGNED_ON_TIMESTAMP) {
                return userConsent;
            }
            throw new EntityNotFoundException(UserConsent.class);
        });
    }
    
    private void createStudyConsent() {
        doReturn(CONSENT_CREATED_ON_TIMESTAMP).when(studyConsent).getCreatedOn();
        doReturn(studyConsent).when(studyConsentDao).getActiveConsent(SUBPOP_GUID);
        doReturn(studyConsent).when(studyConsentDao).getConsent(SUBPOP_GUID, CONSENT_CREATED_ON_TIMESTAMP);
    }
    
    private void createSignatureWithNoTimestamps() {
        map.get(SUBPOP_GUID).add(new ConsentSignature.Builder().withBirthdate("2010-10-10").withName("Test User").build());
    }
    
    private void createSignatureWithTimestamps(long signedOn, long consentCreatedOn) {
        map.get(SUBPOP_GUID).add(new ConsentSignature.Builder().withSignedOn(signedOn)
                .withConsentCreatedOn(consentCreatedOn).withBirthdate("2010-10-10").withName("Test User").build());
    }
    
    private void assertSignature(long signedOn, long consentCreatedOn) {
        Account account = accountCaptor.getValue();
        ConsentSignature signature = account.getActiveConsentSignature(SUBPOP_GUID);
        
        assertEquals(signedOn, signature.getSignedOn());
        assertEquals(consentCreatedOn, signature.getConsentCreatedOn());
    }
    
    @Test
    public void updatesSignatureNoTimestamps() {
        createSignatureWithTimestamps(SIGNED_ON_TIMESTAMP, 0L);
        createUserConsent();
        createStudyConsent();
        
        backfill.doBackfill(task, callback);

        verify(accountDao).updateAccount(accountCaptor.capture());
        assertSignature(SIGNED_ON_TIMESTAMP, CONSENT_CREATED_ON_TIMESTAMP);
    }
    
    // If there's no user consent, fall back to the active study consent and the time the account was created
    @Test
    public void updatesSignatureNoTimestampsNoUserConsent() {
        createSignatureWithNoTimestamps();
        createStudyConsent();
        doReturn(null).when(userConsentDao).getActiveUserConsent(HEALTH_CODE, SUBPOP_GUID);
        when(userConsentDao.getUserConsent(HEALTH_CODE, SUBPOP_GUID, NOW)).thenAnswer((invocation) -> {
            throw new EntityNotFoundException(UserConsent.class);
        });

        backfill.doBackfill(task, callback);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        assertSignature(USER_CREATED_ON_TIMESTAMP, CONSENT_CREATED_ON_TIMESTAMP);
    }
    
    @Test
    public void signatureReferencesMissingStudyConsentButIsFixed() {
        createSignatureWithTimestamps(NOW, NOW);
        createUserConsent();
        createStudyConsent();
        
        backfill.doBackfill(task, callback);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        assertSignature(USER_CREATED_ON_TIMESTAMP, CONSENT_CREATED_ON_TIMESTAMP);
    }
    
    // In this case we do not need the study consent record because the user consent record has everything we need.
    @Test
    public void updatesSignatureNoTimestampsNoStudyConsent() {
        createSignatureWithTimestamps(SIGNED_ON_TIMESTAMP, 0L);
        createUserConsent();
        doThrow(new EntityNotFoundException(StudyConsent.class)).when(studyConsentDao).getActiveConsent(SUBPOP_GUID);

        backfill.doBackfill(task, callback);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        assertSignature(SIGNED_ON_TIMESTAMP, CONSENT_CREATED_ON_TIMESTAMP);
    }
    
    @Test
    public void updatesConsentCreatedOnWhilePreservingSignedOnValue() {
        createSignatureWithTimestamps(SIGNED_ON_TIMESTAMP, 0);
        createUserConsent();
        createStudyConsent();
        
        backfill.doBackfill(task, callback);

        verify(accountDao).updateAccount(accountCaptor.capture());
        assertSignature(SIGNED_ON_TIMESTAMP, CONSENT_CREATED_ON_TIMESTAMP);
    }
    
    @Test
    public void leavesValidSignaturesAlone() {
        createSignatureWithTimestamps(SIGNED_ON_TIMESTAMP, CONSENT_CREATED_ON_TIMESTAMP);
        createUserConsent();
        createStudyConsent();
        
        backfill.doBackfill(task, callback);
        
        verify(accountDao, never()).updateAccount(accountCaptor.capture());
    }
    
}
