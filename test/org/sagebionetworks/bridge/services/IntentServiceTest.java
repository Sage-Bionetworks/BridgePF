package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@RunWith(MockitoJUnitRunner.class)
public class IntentServiceTest {

    private static final long TIMESTAMP = 1000L; 
    
    IntentService service;

    @Mock
    SmsService mockSmsService;
    
    @Mock
    SendMailService mockSendMailService;

    @Mock
    StudyService mockStudyService;
    
    @Mock
    SubpopulationService mockSubpopService;
    
    @Mock
    ConsentService mockConsentService;
    
    @Mock
    CacheProvider mockCacheProvider;

    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    Study mockStudy;
    
    @Mock
    AccountDao accountDao;
    
    @Captor
    ArgumentCaptor<SubpopulationGuid> subpopGuidCaptor;

    @Captor
    ArgumentCaptor<CacheKey> keyCaptor;

    @Captor
    ArgumentCaptor<SmsMessageProvider> smsMessageProviderCaptor;
    
    @Captor
    ArgumentCaptor<MimeTypeEmailProvider> mimeTypeEmailProviderCaptor;
    
    @Before
    public void before() {
        service = new IntentService();
        service.setSmsService(mockSmsService);
        service.setSendMailService(mockSendMailService);
        service.setStudyService(mockStudyService);
        service.setSubpopulationService(mockSubpopService);
        service.setConsentService(mockConsentService);
        service.setCacheProvider(mockCacheProvider);
        service.setAccountDao(accountDao);
        service.setParticipantService(mockParticipantService);
    }
    
    @Test
    public void submitIntentToParticipateWithPhone() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
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

        verify(mockSmsService).sendSmsMessage(isNull(), smsMessageProviderCaptor.capture());

        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        assertEquals(mockStudy, provider.getStudy());
        assertEquals(intent.getPhone(), provider.getPhone());
        assertEquals("this-is-a-link", provider.getSmsRequest().getMessage());
        assertEquals("Transactional", provider.getSmsType());
    }
    
    // In this case when there isn't an install link for the OS or that is marked
    // "Universal," we take anything.
    @Test
    public void submitIntentToParticipateWithMismatchedInstallLinks() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
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

        verify(mockSmsService).sendSmsMessage(isNull(), smsMessageProviderCaptor.capture());

        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        assertEquals(mockStudy, provider.getStudy());
        assertEquals(intent.getPhone(), provider.getPhone());
        assertEquals("this-is-a-link", provider.getSmsRequest().getMessage());
        assertEquals("Transactional", provider.getSmsType());        
    }
    
    @Test
    public void submitIntentToParticipateWithEmail() throws Exception {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).withPhone(null)
                .withOsName("iOS").withEmail("email@email.com").build();        
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("iOS", "this-is-a-link");
        installLinks.put("Android", "the-wrong-link");
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockStudy.getInstallLinks()).thenReturn(installLinks);
        when(mockStudy.getAppInstallLinkTemplate()).thenReturn(new EmailTemplate("subject", "body ${appInstallUrl}", MimeType.HTML));
        when(mockStudyService.getStudy(intent.getStudyId())).thenReturn(mockStudy);
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), 
                TestConstants.TEST_STUDY, "email@email.com");
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(cacheKey, keyCaptor.getValue());

        verify(mockSendMailService).sendEmail(mimeTypeEmailProviderCaptor.capture());

        BasicEmailProvider provider = (BasicEmailProvider)mimeTypeEmailProviderCaptor.getValue();
        assertEquals(mockStudy, provider.getStudy());
        assertEquals("email@email.com", Iterables.getFirst(provider.getRecipientEmails(), null));
        assertEquals(EmailType.APP_INSTALL, provider.getType());
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"null\" <null>", email.getSenderAddress());
        assertEquals("subject", email.getSubject());
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals("body this-is-a-link", body.getContent());
        assertEquals("email@email.com", email.getRecipientAddresses().get(0));
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EmailType.APP_INSTALL, email.getType());
    }    
    
    @Test(expected = InvalidEntityException.class)
    public void submitIntentToParticipateInvalid() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        IntentToParticipate invalid = new IntentToParticipate.Builder().copyOf(intent).withPhone(null).build();
        
        service.submitIntentToParticipate(invalid);
    }
    
    @Test
    public void submitIntentToParticipateExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), new StudyIdentifierImpl("testStudy"),
                TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("testStudy"));
        when(mockStudyService.getStudy(intent.getStudyId())).thenReturn(mockStudy);
        when(mockCacheProvider.getObject(cacheKey, IntentToParticipate.class))
                .thenReturn(intent);

        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(intent.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());

        // These are not called.
        verify(mockCacheProvider, never()).setObject(cacheKey, intent, (4 * 60 * 60));
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }
    
    @Test
    public void submitIntentToParticipateAccountExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        AccountId accountId = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, intent.getPhone()); 
        
        Account account = Account.create();
        when(accountDao.getAccount(accountId)).thenReturn(account);
        
        service.submitIntentToParticipate(intent);
        
        // None of this happens...
        verifyNoMoreInteractions(mockStudyService);
        verifyNoMoreInteractions(mockSubpopService);
        verifyNoMoreInteractions(mockCacheProvider);
        verifyNoMoreInteractions(mockSmsService);
    }

    @Test
    public void registerIntentToParticipateWithPhone() {
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
        
        Account account = Account.create();
        account.setPhone(TestConstants.PHONE);
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TestConstants.TEST_STUDY, TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        service.registerIntentToParticipate(mockStudy, account);
        
        verify(mockSubpopService).getSubpopulations(TestConstants.TEST_STUDY, false);
        verify(mockCacheProvider).removeObject(key);
        verify(mockConsentService).consentToResearch(eq(mockStudy), eq(SubpopulationGuid.create("BBB")), 
                any(), eq(intent.getConsentSignature()), eq(intent.getScope()), eq(true));
    }

    @Test
    public void registerIntentToParticipateWithEmail() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        IntentToParticipate intent = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withEmail(TestConstants.EMAIL)
                .withScope(SharingScope.NO_SHARING)
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withSubpopGuid("BBB")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        Account account = Account.create();
        account.setEmail(TestConstants.EMAIL);
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TestConstants.TEST_STUDY, TestConstants.EMAIL);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        service.registerIntentToParticipate(mockStudy, account);
        
        verify(mockSubpopService).getSubpopulations(TestConstants.TEST_STUDY, false);
        verify(mockCacheProvider).removeObject(key);
        verify(mockConsentService).consentToResearch(eq(mockStudy), eq(SubpopulationGuid.create("BBB")), 
                any(), eq(intent.getConsentSignature()), eq(intent.getScope()), eq(true));
    }
    
    @Test
    public void registerIntentToParticipateWithMultipleConsents() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        IntentToParticipate intentAAA = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withPhone(TestConstants.PHONE)
                .withScope(SharingScope.NO_SHARING)
                .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                .withSubpopGuid("AAA")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        IntentToParticipate intentBBB = new IntentToParticipate.Builder()
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
        
        Account account = Account.create();
        account.setId("id");
        account.setPhone(TestConstants.PHONE);
        
        StudyParticipant participant = new StudyParticipant.Builder().build(); 
        
        CacheKey keyAAA = CacheKey.itp(SubpopulationGuid.create("AAA"), TestConstants.TEST_STUDY, TestConstants.PHONE);
        CacheKey keyBBB = CacheKey.itp(SubpopulationGuid.create("BBB"), TestConstants.TEST_STUDY, TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(keyAAA, IntentToParticipate.class)).thenReturn(intentAAA);
        when(mockCacheProvider.getObject(keyBBB, IntentToParticipate.class)).thenReturn(intentBBB);
        when(mockParticipantService.getParticipant(mockStudy, "id", true)).thenReturn(participant);
        
        service.registerIntentToParticipate(mockStudy, account);
        
        verify(mockSubpopService).getSubpopulations(TestConstants.TEST_STUDY, false);
        verify(mockCacheProvider).removeObject(keyAAA);
        verify(mockCacheProvider).removeObject(keyBBB);
        verify(mockConsentService).consentToResearch(eq(mockStudy), eq(SubpopulationGuid.create("AAA")), 
                any(), eq(intentAAA.getConsentSignature()), eq(intentAAA.getScope()), eq(true));
        verify(mockConsentService).consentToResearch(eq(mockStudy), eq(SubpopulationGuid.create("BBB")), 
                any(), eq(intentBBB.getConsentSignature()), eq(intentBBB.getScope()), eq(true));
        // Only loaded the participant once...
        verify(mockParticipantService, times(1)).getParticipant(mockStudy, "id", true);
    }
    
    @Test
    public void noPhoneDoesNothing() {
        Account account = Account.create();
        
        service.registerIntentToParticipate(mockStudy, account);
        
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
        
        Account account = Account.create();
        account.setPhone(TestConstants.PHONE);
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TestConstants.TEST_STUDY, TestConstants.PHONE);
        
        when(mockStudy.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        
        service.registerIntentToParticipate(mockStudy, account);
        
        verify(mockSubpopService).getSubpopulations(TestConstants.TEST_STUDY, false);
        verify(mockCacheProvider, never()).removeObject(key);
        verifyNoMoreInteractions(mockConsentService); 
    }
    
    @Test
    public void submitIntentToParticipateWithoutInstallLinks() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
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
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
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
        
        Map<String,String> emptyInstallLinks = Maps.newHashMap();
        assertNull(service.getInstallLink("iPhone OS", emptyInstallLinks));
    }

}
