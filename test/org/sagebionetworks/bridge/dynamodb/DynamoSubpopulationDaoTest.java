package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

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
    
    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoSubpopulation.class);
    }
    
    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoSubpopulationDaoTest.class));
    }
    
    @After
    public void after() {
        dao.deleteAllSubpopulations(studyId);
        assertTrue(dao.getSubpopulations(studyId, false, true).isEmpty());
    }
    
    @Test
    public void crudSubpopulationOK() {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setGuid(BridgeUtils.generateGuid());
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setMinAppVersion(2);
        subpop.setMaxAppVersion(10);
        
        // CREATE
        Subpopulation savedSubpop = dao.createSubpopulation(subpop);
        assertNotNull(savedSubpop.getGuid());
        assertNotNull(savedSubpop.getVersion());
        assertFalse(savedSubpop.isDefaultGroup()); // was not set to true
        
        // READ
        Subpopulation retrievedSubpop = dao.getSubpopulation(studyId, savedSubpop.getGuid());
        assertEquals(savedSubpop, retrievedSubpop);
        
        // UPDATE
        retrievedSubpop.setName("Name 2");
        retrievedSubpop.setDescription("Description 2");
        Subpopulation finalSubpop = dao.updateSubpopulation(retrievedSubpop);
        
        // With this change, they should be equivalent using value equality
        retrievedSubpop.setVersion(finalSubpop.getVersion());
        assertEquals(retrievedSubpop, finalSubpop);

        // Some further things that should be true:
        // There's now only one subpopulation in the list
        List<Subpopulation> allSubpops = dao.getSubpopulations(studyId, false, true);
        assertEquals(1, allSubpops.size());
        
        // Logical delete works...
        dao.deleteSubpopulation(studyId, finalSubpop.getGuid());
        Subpopulation deletedSubpop = dao.getSubpopulation(studyId, finalSubpop.getGuid());
        assertTrue(deletedSubpop.isDeleted());
        
        // ... and it hides the subpop in the query used to find subpopulations for a user
        List<Subpopulation> subpopulations = dao.getSubpopulations(studyId, true, false);
        assertEquals(0, subpopulations.size());
    }
    
    @Test(expected = BadRequestException.class)
    public void cannotRecreateExistingObject() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        dao.createSubpopulation(subpop);
    }

    @Test
    public void getSubpopulationsWillNotCreateDefault() {
        List<Subpopulation> subpops = dao.getSubpopulations(studyId, false, true);
        assertTrue(subpops.isEmpty());
    }
    
    @Test
    public void getSubpopulationsWillCreateDefault() {
        List<Subpopulation> subpops = dao.getSubpopulations(studyId, true, false);
        assertEquals(1, subpops.size());

        Subpopulation subpop = subpops.get(0);
        assertEquals("Default Consent Group", subpop.getName());
        assertEquals(studyId.getIdentifier(), subpop.getGuid());
        assertTrue(subpop.isDefaultGroup());
        
        // Cannot set this group to be unrequired
        subpop.setDefaultGroup(false);
        subpop = dao.updateSubpopulation(subpop);
        assertTrue(subpop.isDefaultGroup());
        
        // Cannot delete a required subpopulation
        try {
            dao.deleteSubpopulation(studyId, subpop.getGuid());
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals("Cannot delete the default subpopulation for a study.", e.getMessage());
        }
    }
    
    @Test
    public void getSubpopulationsForUser() {
        Subpopulation subpop1 = createSubpop(SUBPOP_1, 0, 6, "group1"); // match up to version 6 and data group1, specificity 3
        Subpopulation subpop2 = createSubpop(SUBPOP_2, null, 6, null); // match version 0-6, specificity 2
        Subpopulation subpop3 = createSubpop(SUBPOP_3, null, null, "group1"); // match group1, specificity 1
        Subpopulation subpop4 = createSubpop(SUBPOP_4, null, null, null); // match anything, specificity 0
        
        // version 12, no tags == Subpop 4
        List<Subpopulation> results = dao.getSubpopulationsForUser(scheduleContext(12, null));
        assertEquals(Sets.newHashSet(subpop4), Sets.newHashSet(results));
        
        // version 12, tag group1 == Subpops 3, 4
        results = dao.getSubpopulationsForUser(scheduleContext(12, "group1"));
        assertEquals(Sets.newHashSet(subpop3, subpop4), Sets.newHashSet(results));
        
        // version 4, no tag == Subpops 2, 4
        results = dao.getSubpopulationsForUser(scheduleContext(4, null));
        assertEquals(Sets.newHashSet(subpop2, subpop4), Sets.newHashSet(results));
        
        // version 4, tag group1 == Subpops 1,2,3,4, returns 1 in this case (most specific)
        results = dao.getSubpopulationsForUser(scheduleContext(4, "group1"));
        assertEquals(Sets.newHashSet(subpop1, subpop2, subpop3, subpop4), Sets.newHashSet(results));
    }
    
    @Test
    public void cannotDeleteOrRequireSubpopOnCreate() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(BridgeUtils.generateGuid());
        subpop.setStudyIdentifier("AAA");
        subpop.setDeleted(true);
        subpop.setDefaultGroup(true);
        
        subpop = dao.createSubpopulation(subpop);
        assertFalse(subpop.isDeleted());
        assertFalse(subpop.isDefaultGroup());
    }
    
    @Test
    public void cannotDeleteOrRequireSubpopOnUpdate() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        subpop.setDeleted(true);
        subpop.setDefaultGroup(true);
        
        subpop = dao.updateSubpopulation(subpop);
        assertFalse(subpop.isDeleted());
        assertFalse(subpop.isDefaultGroup());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void cannotUpdateASubpopThatDoesNotExist() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        subpop.setGuid(BridgeUtils.generateGuid());
        dao.updateSubpopulation(subpop);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void cannotUpdateASubpopThatIsDeleted() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        dao.deleteSubpopulation(studyId, subpop.getGuid());
        
        // This should now throw ENFE
        dao.updateSubpopulation(subpop);
    }
    
    @Test
    public void createDefaultSubpopulation() {
        // This is not currently called outside of the DAO but it will be when creating studies.
        Subpopulation subpop = dao.createDefaultSubpopulation(studyId);
        assertEquals("Default Consent Group", subpop.getName());
        assertEquals(studyId.getIdentifier(), subpop.getGuid());
        assertEquals(studyId.getIdentifier(), subpop.getStudyIdentifier());
        assertNotNull(subpop.getGuid());
        assertNotNull(subpop.getVersion());
    }
    
    @Test
    public void createDefaultSubpopulationThroughGetMethod() {
        List<Subpopulation> results = dao.getSubpopulations(studyId, true, false);
        assertEquals(1, results.size());
        assertEquals("Default Consent Group", results.get(0).getName());
    }
    
    @Test(expected=EntityNotFoundException.class)
    public void deleteNotExistingSubpopulationThrowsException() {
        dao.deleteSubpopulation(studyId, "guidDoesNotExist");
    }
    
    /**
     * When there are no subpopulations at all, then we create and return a default 
     * subpopulation. This is necessary for migration and for bootstrapping initial 
     * studies.
     */
    @Test
    public void getSubpopulationsForUserReturnsNoSubpopulations() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(studyId.getIdentifier())
                .build();
        List<Subpopulation> results = dao.getSubpopulationsForUser(context);
        
        assertEquals(1, results.size());
        assertEquals("Default Consent Group", results.get(0).getName());
    }
    
    /**
     * Here the research designer has created an error when creating subpopulations 
     * such that there's no match for this user... in this case, we want to return null.
     */
    @Test
    public void getSubpopulationsForUserDoesNotMatchSubpopulationReturnsNull() {
        createSubpop("Name of unmatcheable subpopulation", null, null, "unmatcheableGroup");
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(studyId.getIdentifier())
                .build();
        List<Subpopulation> results = dao.getSubpopulationsForUser(context);
        assertTrue(results.isEmpty());
    }
    
    private Subpopulation createSubpop(String name, Integer min, Integer max, String group) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setName(name);
        subpop.setGuid(BridgeUtils.generateGuid());
        if (min != null) {
            subpop.setMinAppVersion(min);
        }
        if (max != null) {
            subpop.setMaxAppVersion(max);
        }
        if (group != null) {
            subpop.setAllOfGroups(Sets.newHashSet(group));
        }
        return dao.createSubpopulation(subpop);
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
