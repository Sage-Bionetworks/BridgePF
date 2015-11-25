package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSubpopulationDaoTest {
    
    private static final String SUBPOP_1 = "Subpop 1";
    private static final String SUBPOP_2 = "Subpop 2";
    private static final String SUBPOP_3 = "Subpop 3";
    private static final String SUBPOP_4 = "Subpop 4";

    StudyIdentifier studyId;
    
    @Resource
    DynamoSubpopulationDao dao;
    
    private Set<Subpopulation> subpopsToDelete;
    
    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoSubpopulation.class);
    }
    
    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoSubpopulationDaoTest.class));
        subpopsToDelete = Sets.newHashSet();
    }
    
    @After
    public void after() {
        for (Subpopulation subpop: subpopsToDelete) {
            dao.deleteSubpopulation(studyId, subpop.getGuid());
        }
        // testing delete after every test...
        assertTrue(dao.getSubpopulations(studyId, false).isEmpty());
    }
    
    @Test
    public void crudSubpopulationOK() {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setMinAppVersion(2);
        subpop.setMaxAppVersion(10);
        
        // CREATE
        Subpopulation savedSubpop = dao.createSubpopulation(subpop);
        assertNotNull(savedSubpop.getGuid());
        assertNotNull(savedSubpop.getVersion());
        
        // READ
        Subpopulation retrievedSubpop = dao.getSubpopulation(studyId, savedSubpop.getGuid());
        assertEquals(savedSubpop, retrievedSubpop);
        
        // UPDATE
        retrievedSubpop.setName("Name 2");
        retrievedSubpop.setDescription("Description 2");
        Subpopulation finalSubpop = dao.updateSubpopulation(retrievedSubpop);
        
        // With this change, they should be equivalent using value equality
        retrievedSubpop.setVersion(finalSubpop.getVersion());
        
        // And delete we handle after every test.
        assertEquals(retrievedSubpop, finalSubpop);
        
        dao.deleteSubpopulation(studyId, finalSubpop.getGuid());
        try {
            dao.getSubpopulation(studyId, finalSubpop.getGuid());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            assertEquals("Subpopulation not found.", e.getMessage());
        }
    }

    @Test
    public void getSubpopulationsWillNotCreateDefault() {
        List<Subpopulation> subpops = dao.getSubpopulations(studyId, false);
        assertTrue(subpops.isEmpty());
    }
    
    @Test
    public void getSubpopulationsWillCreateDefault() {
        List<Subpopulation> subpops = dao.getSubpopulations(studyId, true);
        assertEquals(1, subpops.size());

        Subpopulation subpop = subpops.get(0);
        assertEquals("Default Consent Group", subpop.getName());
        assertEquals(studyId.getIdentifier(), subpop.getGuid());
        assertTrue(subpop.isRequired());
        subpopsToDelete.add(subpop);
    }

    @Test
    public void getSubpopulationsForUser() {
        subpop(SUBPOP_1, 0, 6, "group1"); // match up to version 6 and data group1, specificity 3
        
        subpop(SUBPOP_2, null, 6, null); // match version 0-6, specificity 2
        
        subpop(SUBPOP_3, null, null, "group1"); // match group1, specificity 1
        
        subpop(SUBPOP_4, null, null, null); // match anything, specificity 0
        
        // version 12, no tags == Subpop 4
        Subpopulation subpop = dao.getSubpopulationForUser(scheduleContext(12, null));
        assertEquals(SUBPOP_4, subpop.getName());
        
        // version 12, tag group1 == Subpops 3, 4
        subpop = dao.getSubpopulationForUser(scheduleContext(12, "group1"));
        assertEquals(SUBPOP_3, subpop.getName());
        
        // version 4, no tag == Subpops 2, 4
        subpop = dao.getSubpopulationForUser(scheduleContext(4, null));
        assertEquals(SUBPOP_2, subpop.getName());
        
        // version 4, tag group1 == Subpops 1,2,3,4, returns 2 in this case (most specific)
        subpop = dao.getSubpopulationForUser(scheduleContext(4, "group1"));
        assertEquals(SUBPOP_1, subpop.getName());
    }

    private Subpopulation subpop(String name, Integer min, Integer max, String group) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setName(name);
        if (min != null) {
            subpop.setMinAppVersion(min);
        }
        if (max != null) {
            subpop.setMaxAppVersion(max);
        }
        if (group != null) {
            subpop.setAllOfGroups(Sets.newHashSet(group));
        }
        dao.createSubpopulation(subpop);
        subpopsToDelete.add(subpop);
        return subpop;
    }
    
    private ScheduleContext scheduleContext(int version, String tag) {
        ScheduleContext.Builder builder = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/"+version))
                .withStudyIdentifier(studyId.getIdentifier());
        if (tag != null) {
            builder.withUserDataGroups(Sets.newHashSet(tag));    
        }
        return builder.build();
    }
}
