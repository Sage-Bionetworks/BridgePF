package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Subpopulation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class SubpopulationServiceTest {

    SubpopulationService service;
    
    @Mock
    SubpopulationDao dao;
    
    @Mock
    Study study;
    
    Subpopulation subpop;
    
    @Before
    public void before() {
        service = new SubpopulationService();
        service.setSubpopulationDao(dao);
        
        subpop = Subpopulation.create();
        
        Set<String> dataGroups = Sets.newHashSet("group1","group2");
        when(study.getDataGroups()).thenReturn(dataGroups);
        when(study.getIdentifier()).thenReturn(TEST_STUDY_IDENTIFIER);
        
        when(dao.createSubpopulation(any())).thenAnswer(returnsFirstArg());
        when(dao.updateSubpopulation(any())).thenAnswer(returnsFirstArg());
    }
    
    // The contents of this exception are tested in the validator tests.
    @Test(expected = InvalidEntityException.class)
    public void creationIsValidated() {
        Subpopulation subpop = Subpopulation.create();
        service.createSubpopulation(study, subpop);
    }
    
    // The contents of this exception are tested in the validator tests.
    @Test(expected = InvalidEntityException.class)
    public void updateIsValidated() {
        Subpopulation subpop = Subpopulation.create();
        service.createSubpopulation(study, subpop);
    }
    
    @Test
    public void createSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setStudyIdentifier("junk-you-cannot-set");
        subpop.setGuid("cannot-set-guid");
        subpop.setDefaultGroup(false);
        
        Subpopulation result = service.createSubpopulation(study, subpop);
        assertEquals("Name", result.getName());
        assertNotNull(result.getGuid());
        assertNotEquals("cannot-set-guid", result.getGuid());
        assertFalse(result.isDeleted());
        assertEquals(TEST_STUDY_IDENTIFIER, result.getStudyIdentifier());
        
        verify(dao).createSubpopulation(subpop);
    }
    @Test
    public void updateSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setStudyIdentifier("junk-you-cannot-set");
        subpop.setGuid("guid");
        subpop.setDefaultGroup(false);
        subpop.setDeleted(true);
        
        when(dao.getSubpopulation(any(), any())).thenReturn(Subpopulation.create());
        
        Subpopulation result = service.updateSubpopulation(study, subpop);
        assertEquals("Name", result.getName());
        assertEquals("guid", result.getGuid());
        assertEquals(TEST_STUDY_IDENTIFIER, result.getStudyIdentifier());
        
        verify(dao).updateSubpopulation(subpop);
    }
    @Test
    public void getSubpopulations() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Name 1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Name 2");

        List<Subpopulation> list = Lists.newArrayList(subpop1, subpop2); 
        when(dao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(list);
        
        List<Subpopulation> results = service.getSubpopulations(TEST_STUDY);
        assertEquals(2, results.size());
        assertEquals(subpop1, results.get(0));
        assertEquals(subpop2, results.get(1));
        verify(dao).getSubpopulations(TEST_STUDY, true, false);
    }
    @Test
    public void getSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        when(dao.getSubpopulation(TEST_STUDY, "AAA")).thenReturn(subpop);

        Subpopulation result = service.getSubpopulation(TEST_STUDY, "AAA");
        assertEquals(subpop, result);
        verify(dao).getSubpopulation(TEST_STUDY, "AAA");
    }
    @Test
    public void getSubpopulationForUser() {
        List<Subpopulation> subpops = ImmutableList.of(Subpopulation.create());
        // We test the matching logic in CriteriaUtilsTest as well as in the DAO. Here we just want
        // to verify it is being carried through.
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/4")).build();
        
        when(dao.getSubpopulationsForUser(context)).thenReturn(subpops);
        
        List<Subpopulation> results = service.getSubpopulationForUser(context);
        
        assertEquals(subpops, results);
        verify(dao).getSubpopulationsForUser(context);
    }
    @Test
    public void deleteSubpopulation() {
        service.deleteSubpopulation(TEST_STUDY, "AAA");
        
        verify(dao).deleteSubpopulation(TEST_STUDY, "AAA");
    }
}
