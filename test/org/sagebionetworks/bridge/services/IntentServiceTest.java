package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class IntentServiceTest {

    private static final long TIMESTAMP = 1000L; 
    
    IntentService service;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    SubpopulationService mockSubpopService;
    
    @Mock
    ConsentService mockConsentService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    NotificationsService mockNotificationsService;
    
    @Mock
    Study mockStudy;
    
    @Mock
    AccountDao accountDao;
    
    @Captor
    ArgumentCaptor<SubpopulationGuid> subpopGuidCaptor;

    @Captor
    ArgumentCaptor<CacheKey> keyCaptor;

    @Captor
    ArgumentCaptor<IntentToParticipate> intentCaptor;
    
    @Captor
    ArgumentCaptor<SmsMessageProvider> smsMessageProviderCaptor;
    
    @Before
    public void before() {
        service = new IntentService();
        service.setStudyService(mockStudyService);
        service.setSubpopulationService(mockSubpopService);
        service.setConsentService(mockConsentService);
        service.setCacheProvider(mockCacheProvider);
        service.setNotificationsService(mockNotificationsService);
        service.setAccountDao(accountDao);
    }
    
    @Test
    public void submitIntentToParticipate() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockStudy.getInstallLinks()).thenReturn(installLinks);
        when(mockStudy.getAppInstallLinkSmsTemplate()).thenReturn(new SmsTemplate("this-is-a-link"));
        when(mockStudyService.getStudy(intent.getStudyId())).thenReturn(mockStudy);
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), TestConstants.TEST_STUDY,
                TestConstants.PHONE);
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(cacheKey, keyCaptor.getValue());
        
        verify(mockNotificationsService).sendSmsMessage(smsMessageProviderCaptor.capture());
        
        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        assertEquals(mockStudy, provider.getStudy());
        assertEquals(intent.getPhone(), provider.getPhone());
        assertEquals("this-is-a-link", provider.getSmsRequest().getMessage());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void submitIntentToParticipateInvalid() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        IntentToParticipate invalid = new IntentToParticipate.Builder().copyOf(intent).withPhone(null).build();
        
        service.submitIntentToParticipate(invalid);
    }
    
    @Test
    public void submitIntentToParticipateWithoutInstallLinks() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockStudyService.getStudy(intent.getStudyId())).thenReturn(mockStudy);
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), TestConstants.TEST_STUDY,
                TestConstants.PHONE);
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        // We do store the intent
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(cacheKey, keyCaptor.getValue());
        
        // But we don't send a message because installLinks map is empty
        verify(mockNotificationsService, never()).sendSmsMessage(any());
    }
    
    @Test
    public void submitIntentToParticipateExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), new StudyIdentifierImpl("testStudy"),
                TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("testStudy"));
        when(mockStudy.getInstallLinks()).thenReturn(installLinks);
        when(mockStudyService.getStudy(intent.getStudyId())).thenReturn(mockStudy);
        when(mockCacheProvider.getObject(cacheKey, IntentToParticipate.class))
                .thenReturn(intent);

        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        // These are not called.
        verify(mockCacheProvider, never()).setObject(cacheKey, intent, (4 * 60 * 60));
        verify(mockNotificationsService, never()).sendSmsMessage(any());
    }
    
    @Test
    public void submitIntentToParticipateAccountExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        AccountId accountId = AccountId.forPhone(intent.getStudyId(), intent.getPhone()); 
        
        Account account = new GenericAccount();
        when(accountDao.getAccount(accountId)).thenReturn(account);
        
        service.submitIntentToParticipate(intent);
        
        // None of this happens...
        verifyNoMoreInteractions(mockStudyService);
        verifyNoMoreInteractions(mockSubpopService);
        verifyNoMoreInteractions(mockCacheProvider);
        verifyNoMoreInteractions(mockNotificationsService);
    }

    @Test
    public void registerIntentToParticipate() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        IntentToParticipate intent = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withPhone(TestConstants.PHONE)
                .withScope(SharingScope.NO_SHARING)
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withSubpopGuid("BBB")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withPhone(TestConstants.PHONE).build();
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TestConstants.TEST_STUDY, TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockStudy.getIdentifier()).thenReturn(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        service.registerIntentToParticipate(mockStudy, participant);
        
        verify(mockSubpopService).getSubpopulations(TestConstants.TEST_STUDY);
        verify(mockCacheProvider).removeObject(key);
        verify(mockConsentService).consentToResearch(mockStudy, SubpopulationGuid.create("BBB"), 
                participant, intent.getConsentSignature(), intent.getScope(), true);
    }
    
    @Test
    public void noPhoneDoesNothing() {
        StudyParticipant participant = new StudyParticipant.Builder().build();
        
        service.registerIntentToParticipate(mockStudy, participant);
        
        verifyNoMoreInteractions(mockSubpopService);
        verifyNoMoreInteractions(mockCacheProvider);
        verifyNoMoreInteractions(mockConsentService); 
    }
    
    @Test
    public void noIntentDoesNothing() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withPhone(TestConstants.PHONE).build();
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TestConstants.TEST_STUDY, TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockStudy.getIdentifier()).thenReturn(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        
        service.registerIntentToParticipate(mockStudy, participant);
        
        verify(mockSubpopService).getSubpopulations(TestConstants.TEST_STUDY);
        verify(mockCacheProvider, never()).removeObject(key);
        verifyNoMoreInteractions(mockConsentService); 
    }
    
    @Test
    public void installLinkCorrectlySelected() {
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("iPhone OS", "iphone-os-link");
        
        // Lacking android or universal, find the only link that's there.
        assertEquals("iphone-os-link", service.getInstallLink("Android", installLinks));
        
        installLinks.put("Universal", "universal-link");
        assertEquals("iphone-os-link", service.getInstallLink("iPhone OS", installLinks));
        assertEquals("universal-link", service.getInstallLink("Android", installLinks));
    }
}
