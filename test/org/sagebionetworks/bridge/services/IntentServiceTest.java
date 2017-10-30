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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

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
    
}
