package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Subpopulation;

@RunWith(MockitoJUnitRunner.class)
public class SubpopulationServiceTest {

    SubpopulationService service;
    
    @Mock
    SubpopulationDao dao;
    
    Study study;
    
    Subpopulation subpop;
    
    @Before
    public void before() {
        service = new SubpopulationService();
        service.setSubpopulationDao(dao);
        
        study = mock(Study.class);
        
        subpop = new DynamoSubpopulation();
        
        when(dao.createSubpopulation(any())).thenReturn(subpop);
    }
    
    @Test
    public void creation() {
        Subpopulation subpop = new DynamoSubpopulation();
        service.createSubpopulation(study, subpop);
    }
    
    @Test
    public void update() {
        
    }
}
