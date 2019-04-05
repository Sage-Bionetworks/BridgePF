package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.models.substudies.SubstudyId;

import com.google.common.collect.ImmutableList;

@RunWith(MockitoJUnitRunner.class)
public class HibernateSubstudyDaoTest {
    private static final List<HibernateSubstudy> SUBSTUDIES = ImmutableList.of(new HibernateSubstudy(),
            new HibernateSubstudy());
    
    @Mock
    private HibernateHelper hibernateHelper;
    
    private HibernateSubstudyDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;

    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<SubstudyId> substudyIdCaptor;
    
    @Captor
    ArgumentCaptor<HibernateSubstudy> substudyCaptor;
    
    @Before
    public void before() {
        dao = new HibernateSubstudyDao();
        dao.setHibernateHelper(hibernateHelper);
    }
    
    @Test
    public void getSubstudiesIncludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateSubstudy.class)))
                .thenReturn(SUBSTUDIES);
        
        List<Substudy> list = dao.getSubstudies(TestConstants.TEST_STUDY, true);
        assertEquals(2, list.size());
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(null), eq(null), eq(HibernateSubstudy.class));
        
        assertEquals("from HibernateSubstudy as substudy where studyId=:studyId", 
                queryCaptor.getValue());
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, parameters.get("studyId"));
    }

    @Test
    public void getSubstudiesExcludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateSubstudy.class)))
            .thenReturn(SUBSTUDIES);

        List<Substudy> list = dao.getSubstudies(TestConstants.TEST_STUDY, false);
        assertEquals(2, list.size());
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(null), eq(null), eq(HibernateSubstudy.class));
        
        assertEquals("from HibernateSubstudy as substudy where studyId=:studyId and deleted != 1", 
                queryCaptor.getValue());
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, parameters.get("studyId"));
    }
    
    @Test
    public void getSubstudy() {
        HibernateSubstudy substudy = new HibernateSubstudy();
        when(hibernateHelper.getById(eq(HibernateSubstudy.class), any())).thenReturn(substudy);
        
        Substudy returnedValue = dao.getSubstudy(TestConstants.TEST_STUDY, "id");
        assertEquals(substudy, returnedValue);
        
        verify(hibernateHelper).getById(eq(HibernateSubstudy.class), substudyIdCaptor.capture());
        
        SubstudyId substudyId = substudyIdCaptor.getValue();
        assertEquals("id", substudyId.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, substudyId.getStudyId());
    }
    
    @Test
    public void createSubstudy() {
        Substudy substudy = Substudy.create();
        substudy.setVersion(2L);
        
        VersionHolder holder = dao.createSubstudy(substudy);
        assertEquals(new Long(2), holder.getVersion());
        
        verify(hibernateHelper).create(substudyCaptor.capture(), eq(null));
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals(new Long(2), persisted.getVersion());
    }

    @Test
    public void updateSubstudy() {
        Substudy substudy = Substudy.create();
        substudy.setVersion(2L);
        
        VersionHolder holder = dao.updateSubstudy(substudy);
        assertEquals(new Long(2), holder.getVersion());
        
        verify(hibernateHelper).update(substudyCaptor.capture(), eq(null));
        
        Substudy persisted = substudyCaptor.getValue();
        assertEquals(new Long(2), persisted.getVersion());
    }

    @Test
    public void deleteSubstudyPermanently() {
        dao.deleteSubstudyPermanently(TestConstants.TEST_STUDY, "oneId");
        
        verify(hibernateHelper).deleteById(eq(HibernateSubstudy.class), substudyIdCaptor.capture());
        SubstudyId substudyId = substudyIdCaptor.getValue();
        assertEquals("oneId", substudyId.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, substudyId.getStudyId());
    }    

    @Test(expected = PersistenceException.class)
    public void deleteSubstudyPermanentlyNotFound() {
        doThrow(new PersistenceException()).when(hibernateHelper).deleteById(eq(HibernateSubstudy.class), any());
            
        dao.deleteSubstudyPermanently(TestConstants.TEST_STUDY, "oneId");
    }    
}
