package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class IntentServiceTest {

    private static final long TIMESTAMP = 1000L; 
    
    private static final String STUDY = "studyId";
    private static final String EMAIL = "email@email.com";
    private static final Phone PHONE = new Phone("4082588569", "US");
    private static final String SUBPOP_GUID = "subpopGuid";
    private static final SharingScope SCOPE = SharingScope.SPONSORS_AND_PARTNERS;
    private static final ConsentSignature SIGNATURE = new ConsentSignature.Builder()
            .withName("Test Name")
            .withBirthdate("1985-02-02")
            .build();
    
    private IntentToParticipate.Builder builder() {
        return new IntentToParticipate.Builder()
                .withStudy(STUDY)
                .withSubpopGuid(SUBPOP_GUID)
                .withScope(SCOPE)
                .withConsentSignature(SIGNATURE);
    }    

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
    Study mockStudy;
    
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
    }
    
    @Test
    public void submitIntentToParticipate() {
        IntentToParticipate itp = TestUtils.getIntentToParticipate(TIMESTAMP);
        
        when(mockStudy.getIdentifier()).thenReturn("testStudy");
        when(mockStudyService.getStudy(itp.getStudy())).thenReturn(mockStudy);
        
        service.submitIntentToParticipate(itp);
        
        verify(mockSubpopService).getSubpopulation(eq(mockStudy), subpopGuidCaptor.capture());
        assertEquals(itp.getSubpopGuid(), subpopGuidCaptor.getValue().getGuid());
        
        verify(mockCacheProvider).setObject(stringCaptor.capture(), eq(itp), eq(4 * 60 * 60));
        assertEquals("subpopGuid:email@email.com:testStudy:itp", stringCaptor.getValue());
    }
    
    @Test
    public void registerIntentToParticipateWithEmail() {
        IntentToParticipate intent = builder().withEmail(EMAIL).build();
        
        Subpopulation subpop1 = Subpopulation.create();
        SubpopulationGuid guidAAA = SubpopulationGuid.create("AAA");
        subpop1.setGuid(guidAAA);
        
        Subpopulation subpop2 = Subpopulation.create();
        SubpopulationGuid guidBBB = SubpopulationGuid.create("BBB");
        subpop2.setGuid(guidBBB);
        
        String key = "BBB:email@email.com:api:itp";
        
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY))
                .thenReturn(Lists.newArrayList(subpop1, subpop2));
        
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        Study study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL).build();
        
        service.registerIntentToParticipate(study, participant);
        
        // verify we iterate through all subpopulations
        verify(mockCacheProvider).getObject("AAA:email@email.com:api:itp", IntentToParticipate.class);
        verify(mockCacheProvider).getObject("BBB:email@email.com:api:itp", IntentToParticipate.class);
        
        verify(mockConsentService).consentToResearch(study, guidBBB, participant, SIGNATURE, SCOPE, true);
    }

    @Test
    public void registerIntentToParticipateWithPhone() {
        IntentToParticipate intent = builder().withEmail(EMAIL).build();
        
        Subpopulation subpop1 = Subpopulation.create();
        SubpopulationGuid guidAAA = SubpopulationGuid.create("AAA");
        subpop1.setGuid(guidAAA);
        
        Subpopulation subpop2 = Subpopulation.create();
        SubpopulationGuid guidBBB = SubpopulationGuid.create("BBB");
        subpop2.setGuid(guidBBB);
        
        String key = "BBB:+14082588569:api:itp";
        
        when(mockSubpopService.getSubpopulations(TestConstants.TEST_STUDY))
                .thenReturn(Lists.newArrayList(subpop1, subpop2));
        
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        Study study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(PHONE).withEmail(EMAIL).build();
        
        service.registerIntentToParticipate(study, participant);
        
        // verify we iterate through all subpopulations
        verify(mockCacheProvider).getObject("AAA:email@email.com:api:itp", IntentToParticipate.class);
        verify(mockCacheProvider).getObject("AAA:+14082588569:api:itp", IntentToParticipate.class);
        verify(mockCacheProvider).getObject("BBB:email@email.com:api:itp", IntentToParticipate.class);
        verify(mockCacheProvider).getObject("BBB:+14082588569:api:itp", IntentToParticipate.class);
        
        verify(mockConsentService).consentToResearch(study, guidBBB, participant, SIGNATURE, SCOPE, true);
    }
}
