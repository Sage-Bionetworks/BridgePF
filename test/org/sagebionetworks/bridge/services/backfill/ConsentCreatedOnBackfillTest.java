package org.sagebionetworks.bridge.services.backfill;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
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
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.BackfillDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
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
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("subpop");
    
    private static final long NOW = 1467742872087L;
    private static final long CONSENT_CREATED_ON_TIMESTAMP = 1467741848921L;
    private static final long SIGNED_ON_TIMESTAMP = 1467741906474L;

    private ConsentCreatedOnBackfill backfill;

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

    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW);
        backfill = new ConsentCreatedOnBackfill();
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
        doReturn(study).when(studyService).getStudy("api"); // for variant doing one study at a time.
        
        // Mock account
        doReturn("healthCode").when(account).getHealthCode();
        doReturn("userId").when(account).getId();
        doReturn(new DateTime(SIGNED_ON_TIMESTAMP)).when(account).getCreatedOn();
        map = Maps.newHashMap();
        map.put(SUBPOP_GUID, Lists.newArrayList());
        doReturn(map).when(account).getAllConsentSignatureHistories();
        doReturn(map.get(SUBPOP_GUID)).when(account).getConsentSignatureHistory(SUBPOP_GUID);
        when(account.getActiveConsentSignature(SUBPOP_GUID)).thenAnswer(guid -> {
            return map.get(SUBPOP_GUID).get(0);
        });
        
        // Mock account DAO
        doReturn(account).when(accountDao).getAccount(study, "userId");
        
        // Mock account summaries
        summary = new AccountSummary(null, null, null, "userId", null, null, null);
        List<AccountSummary> summaries = Lists.newArrayList(summary);
        doReturn(summaries.iterator()).when(accountDao).getStudyAccounts(study);
        
        // Mock study consent
        doReturn(CONSENT_CREATED_ON_TIMESTAMP).when(studyConsent).getCreatedOn();
        doReturn(studyConsent).when(studyConsentDao).getMostRecentConsent(SUBPOP_GUID);

        // Mock user consent
        doReturn(NOW).when(userConsent).getSignedOn();
        doReturn(CONSENT_CREATED_ON_TIMESTAMP).when(userConsent).getConsentCreatedOn();
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    // This works, but it uses the "now" timestamp which we don't really want, do we? We can at least do better if this 
    // comes up.
    @Test
    public void updatesVeryBrokenSignature() {
        map.get(SUBPOP_GUID).add(new ConsentSignature.Builder().withBirthdate("2010-10-10").withName("Test User").build());
        doReturn(userConsent).when(userConsentDao).getUserConsent("healthCode", SUBPOP_GUID, NOW);

        backfill.doBackfill(task, callback);

        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        ConsentSignature signature = account.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals(CONSENT_CREATED_ON_TIMESTAMP, signature.getConsentCreatedOn());
        assertEquals(NOW, signature.getSignedOn());
    }
    
    @Test
    public void updatePreservesSignedOnValue() {
        map.get(SUBPOP_GUID).add(new ConsentSignature.Builder().withBirthdate("2010-10-10")
                .withSignedOn(SIGNED_ON_TIMESTAMP).withName("Test User").build());
        doReturn(userConsent).when(userConsentDao).getUserConsent("healthCode", SUBPOP_GUID, SIGNED_ON_TIMESTAMP);
        
        backfill.doBackfill(task, callback);

        verify(accountDao).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        ConsentSignature signature = account.getActiveConsentSignature(SUBPOP_GUID);
        assertEquals(CONSENT_CREATED_ON_TIMESTAMP, signature.getConsentCreatedOn());
        assertEquals(SIGNED_ON_TIMESTAMP, signature.getSignedOn());
    }
    
    @Test
    public void leavesValidSignaturesAlone() {
        map.get(SUBPOP_GUID).add(new ConsentSignature.Builder().withBirthdate("2010-10-10")
                .withConsentCreatedOn(CONSENT_CREATED_ON_TIMESTAMP)
                .withSignedOn(SIGNED_ON_TIMESTAMP).withName("Test User").build());
        
        backfill.doBackfill(task, callback);
        verify(accountDao, never()).updateAccount(accountCaptor.capture());
    }
    
}
