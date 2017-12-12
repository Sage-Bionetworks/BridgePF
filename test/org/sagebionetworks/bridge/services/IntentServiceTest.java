package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
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
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

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
    ArgumentCaptor<String> stringCaptor;

    @Captor
    ArgumentCaptor<IntentToParticipate> intentCaptor;
    
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
        
        when(mockStudy.getIdentifier()).thenReturn("testStudy");
        when(mockStudy.getInstallLinks()).thenReturn(installLinks);
        when(mockStudyService.getStudy(intent.getStudy())).thenReturn(mockStudy);
        
        String cacheKey = "subpopGuid:"+TestConstants.PHONE.getNumber()+":testStudy:itp";
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        verify(mockCacheProvider).setObject(stringCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(cacheKey, stringCaptor.getValue());
        
        verify(mockNotificationsService).sendSMSMessage(mockStudy, intent.getPhone(), "this-is-a-link");
    }
    
    @Test
    public void submitIntentToParticipateWithoutInstallLinks() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        when(mockStudy.getIdentifier()).thenReturn("testStudy");
        when(mockStudyService.getStudy(intent.getStudy())).thenReturn(mockStudy);
        
        String cacheKey = "subpopGuid:"+TestConstants.PHONE.getNumber()+":testStudy:itp";
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        // We do store the intent
        verify(mockCacheProvider).setObject(stringCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(cacheKey, stringCaptor.getValue());
        
        // But we don't send a message because installLinks map is empty
        verify(mockNotificationsService, never()).sendSMSMessage(mockStudy, intent.getPhone(), "this-is-a-link");
    }
    
    @Test
    public void submitIntentToParticipateExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        String cacheKey = "subpopGuid:"+TestConstants.PHONE.getNumber()+":testStudy:itp";
        
        when(mockStudy.getIdentifier()).thenReturn("testStudy");
        when(mockStudy.getInstallLinks()).thenReturn(installLinks);
        when(mockStudyService.getStudy(intent.getStudy())).thenReturn(mockStudy);
        when(mockCacheProvider.getObject(cacheKey, IntentToParticipate.class))
                .thenReturn(intent);

        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        // These are not called.
        verify(mockCacheProvider, never()).setObject(cacheKey, intent, (4 * 60 * 60));
        verify(mockNotificationsService, never()).sendSMSMessage(mockStudy, intent.getPhone(), "this-is-a-link");
    }
    
    @Test
    public void submitIntentToParticipateAccountExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        AccountId accountId = AccountId.forPhone(intent.getStudy(), intent.getPhone()); 
        
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
                .withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withSubpopGuid("BBB")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withPhone(TestConstants.PHONE).build();
        String key = "BBB:"+TestConstants.PHONE.getNumber()+":api:itp";
        
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
        String key = "BBB:"+TestConstants.PHONE.getNumber()+":api:itp";
        
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
