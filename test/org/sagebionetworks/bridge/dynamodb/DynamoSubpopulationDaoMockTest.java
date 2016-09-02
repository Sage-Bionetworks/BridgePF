package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class DynamoSubpopulationDaoMockTest {
    
    private static final Criteria CRITERIA = TestUtils.createCriteria(2, 10, 
            Sets.newHashSet("a", "b"), Sets.newHashSet("c", "d"));
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("AAA");

    @Spy
    private DynamoSubpopulationDao dao;
    
    @Mock
    private DynamoDBMapper mapper;
    
    @Mock
    private StudyConsentDao studyConsentDao;
    
    @Mock
    private CriteriaDao criteriaDao;

    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        dao.setMapper(mapper);
        dao.setStudyConsentDao(studyConsentDao);
        dao.setCriteriaDao(criteriaDao);
        
        List<DynamoSubpopulation> list = Lists.newArrayList((DynamoSubpopulation)createSubpopulation());

        PaginatedQueryList<DynamoSubpopulation> page = mock(PaginatedQueryList.class);
        when(page.stream()).thenReturn(list.stream());

        doReturn(createSubpopulation()).when(mapper).load(any());
        doReturn(page).when(mapper).query(eq(DynamoSubpopulation.class), any());
        
        when(criteriaDao.getCriteria(any())).thenReturn(CRITERIA);
        when(criteriaDao.createOrUpdateCriteria(any())).thenAnswer(invocation -> {
            return invocation.getArgumentAt(0, Criteria.class);
        });
    }
    
    @Test
    public void createSubpopulationWritesCriteria() {
        Subpopulation subpop = createSubpopulation();
        
        dao.createSubpopulation(subpop);
        
        Criteria criteria = subpop.getCriteria();
        assertEquals(CRITERIA, criteria);
        
        verify(criteriaDao).createOrUpdateCriteria(criteria);
    }
    
    @Test
    public void createDefaultSubpopulationWritesCriteria() {
        Subpopulation subpop = dao.createDefaultSubpopulation(TEST_STUDY);
        
        Criteria criteria = subpop.getCriteria();
        assertEquals(new Integer(0), criteria.getMinAppVersion(OperatingSystem.IOS));
        assertEquals(new Integer(0), criteria.getMinAppVersion(OperatingSystem.ANDROID));
        
        verify(criteriaDao).createOrUpdateCriteria(criteria);
    }
    
    @Test
    public void getSubpopulationRetrievesCriteria() {
        Subpopulation subpop = dao.getSubpopulation(TEST_STUDY, SUBPOP_GUID);
        
        Criteria criteria = subpop.getCriteria();
        assertEquals(CRITERIA, criteria);
        
        verify(criteriaDao).getCriteria(criteria.getKey());
        verifyNoMoreInteractions(criteriaDao);
    }
    
    @Test
    public void getSubpopulationConstructsCriteriaIfNotSaved() {
        when(criteriaDao.getCriteria(any())).thenReturn(null);
        
        Subpopulation subpop = dao.getSubpopulation(TEST_STUDY, SUBPOP_GUID);
        Criteria criteria = subpop.getCriteria();
        assertNotNull(criteria);
        
        verify(criteriaDao).getCriteria(criteria.getKey());
    }
    
    @Test
    public void getSubpopulationsForUserRetrievesCriteria() {
        CriteriaContext context = createContext();
        
        List<Subpopulation> subpops = dao.getSubpopulationsForUser(context);
        Subpopulation subpop = subpops.get(0);
        Criteria criteria = subpop.getCriteria();
        assertEquals(CRITERIA, criteria);
        
        verify(criteriaDao).getCriteria(criteria.getKey());
        verifyNoMoreInteractions(criteriaDao);
    }

    @Test
    public void getSubpopulationsForUserConstructsCriteriaIfNotSaved() {
        when(criteriaDao.getCriteria(any())).thenReturn(null);
        CriteriaContext context = createContext();
        
        List<Subpopulation> subpops = dao.getSubpopulationsForUser(context);
        Subpopulation subpop = subpops.get(0);
        Criteria criteria = subpop.getCriteria();
        assertNotNull(criteria);
        
        verify(criteriaDao).getCriteria(criteria.getKey());
    }

    @Test
    public void physicalDeleteSubpopulationDeletesCriteria() {
        dao.deleteSubpopulation(TEST_STUDY, SUBPOP_GUID, true);
        
        verify(criteriaDao).deleteCriteria(createSubpopulation().getCriteria().getKey());
    }
    
    @Test
    public void logicalDeleteSubpopulationDoesNotDeleteCriteria() {
        dao.deleteSubpopulation(TEST_STUDY, SUBPOP_GUID, false);
        
        verify(criteriaDao, never()).deleteCriteria(createSubpopulation().getCriteria().getKey());
    }
    
    @Test
    public void updateSubpopulationUpdatesCriteriaFromSubpop() {
        // This subpopulation has the criteria fields, but no object
        Subpopulation subpop = createSubpopulation();
        subpop.setVersion(1L);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpop);
        assertEquals(CRITERIA, updatedSubpop.getCriteria());
        
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria savedCriteria = criteriaCaptor.getValue();
        assertEquals(CRITERIA, savedCriteria);
    }
    
    @Test
    public void updateSubpopulationUpdatesCriteriaFromObject() {
        Subpopulation subpopWithCritObject = Subpopulation.create();
        subpopWithCritObject.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        subpopWithCritObject.setGuidString(BridgeUtils.generateGuid());
        subpopWithCritObject.setVersion(1L);
        subpopWithCritObject.setCriteria(CRITERIA);
        
        reset(mapper);
        doReturn(subpopWithCritObject).when(mapper).load(any());
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpopWithCritObject);
        assertEquals(CRITERIA, updatedSubpop.getCriteria());
        
        verify(criteriaDao).getCriteria(subpopWithCritObject.getCriteria().getKey());
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria savedCriteria = criteriaCaptor.getValue();
        assertEquals(CRITERIA, savedCriteria);
    }
    
    @Test
    public void getSubpopulationsCreatesCriteria() {
        // The test subpopulation in the list that is returned from the mock mapper does not have
        // a criteria object. So it will be created as part of loading.
        List<Subpopulation> list = dao.getSubpopulations(TEST_STUDY, false, true);
        assertEquals(CRITERIA, list.get(0).getCriteria());
        
        // Making a point of the fact that there is no criteria object
        doReturn(null).when(criteriaDao).getCriteria(any());
        
        verify(criteriaDao).getCriteria(list.get(0).getCriteria().getKey());
    }
    
    @Test
    public void getSubpopulationsRetrievesCriteria() {
        // The test subpopulation in the list that is returned from the mock mapper does not have
        // a criteria object. So it will be created as part of loading.
        List<Subpopulation> list = dao.getSubpopulations(TEST_STUDY, false, true);
        assertEquals(CRITERIA, list.get(0).getCriteria());
        
        // In this case it actually returns a criteria object.
        verify(criteriaDao).getCriteria(list.get(0).getCriteria().getKey());
    }
    
    @Test
    public void deleteAllSubpopulationsDeletesCriteria() {
        // There's one subpopulation
        dao.deleteAllSubpopulations(TEST_STUDY);
        
        verify(criteriaDao).deleteCriteria(createSubpopulation().getCriteria().getKey());
    }
    
    @Test
    public void criteriaTableTakesPrecedenceOnGet() {
        reset(criteriaDao);
        doReturn(CRITERIA).when(criteriaDao).getCriteria(any());
        
        Subpopulation subpop = dao.getSubpopulation(TEST_STUDY, SUBPOP_GUID);
        Criteria retrievedCriteria = subpop.getCriteria();
        assertEquals(CRITERIA, retrievedCriteria);
    }
    
    @Test
    public void criteriaTableTakesPrecedenceOnGetList() {
        reset(criteriaDao);
        doReturn(CRITERIA).when(criteriaDao).getCriteria(any());
        
        List<Subpopulation> subpops = dao.getSubpopulations(TEST_STUDY, false, true);
        Criteria retrievedCriteria = subpops.get(0).getCriteria();
        assertEquals(CRITERIA, retrievedCriteria);
    }
    
    private CriteriaContext createContext() {
        return new CriteriaContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withUserDataGroups(CRITERIA.getAllOfGroups())
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .build();
    }
    
    private Subpopulation createSubpopulation() {
        Criteria criteria = TestUtils.copyCriteria(CRITERIA);
        criteria.setKey("subpopulation:"+SUBPOP_GUID);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        subpop.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        subpop.setCriteria(criteria);
        return subpop;
    }
}
