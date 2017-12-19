package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.StudyConsentService;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoSubpopulationDaoTest {

    StudyIdentifier studyId;
    
    @Resource
    DynamoSubpopulationDao dao;
    
    @Resource
    StudyConsentService studyConsentService;
    
    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoSubpopulationDaoTest.class));
    }
    
    @After
    public void after() {
        List<Subpopulation> subpopulations = dao.getSubpopulations(studyId, false, true);
        for (Subpopulation subpop : subpopulations) {
            dao.deleteSubpopulation(studyId, subpop.getGuid(), true, true);
        }
        assertTrue(dao.getSubpopulations(studyId, false, true).isEmpty());
    }
    
    @Test
    public void crudSubpopulationOK() {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setRequired(true);
        
        Criteria criteria = TestUtils.createCriteria(2, 10, null, null);
        subpop.setCriteria(criteria);
        
        // CREATE
        Subpopulation savedSubpop = dao.createSubpopulation(subpop);
        assertNotNull(savedSubpop.getGuidString());
        assertNotNull(savedSubpop.getVersion());
        assertFalse(savedSubpop.isDefaultGroup()); // was not set to true
        assertTrue(savedSubpop.isRequired());
        
        Criteria savedCriteria = subpop.getCriteria();
        assertNotNull(savedCriteria);
        assertEquals(subpop.getCriteria().getKey(), savedCriteria.getKey());
        
        // READ
        Subpopulation retrievedSubpop = dao.getSubpopulation(studyId, savedSubpop.getGuid());
        assertEquals(savedSubpop, retrievedSubpop);
        assertNotNull(retrievedSubpop.getCriteria());
        
        // UPDATE
        retrievedSubpop.setName("Name 2");
        retrievedSubpop.setDescription("Description 2");
        retrievedSubpop.setRequired(false);
        retrievedSubpop.getCriteria().setMinAppVersion(OperatingSystem.IOS, 3);
        Subpopulation finalSubpop = dao.updateSubpopulation(retrievedSubpop);
        
        // With this change, they should be equivalent using value equality
        retrievedSubpop.setVersion(finalSubpop.getVersion());
        assertEquals("Name 2", retrievedSubpop.getName());
        assertEquals("Description 2", retrievedSubpop.getDescription());
        assertFalse(retrievedSubpop.isRequired());
        assertEquals(retrievedSubpop, finalSubpop);
        assertEquals(new Integer(3), finalSubpop.getCriteria().getMinAppVersion(OperatingSystem.IOS));

        // Some further things that should be true:
        // There's now only one subpopulation in the list
        List<Subpopulation> allSubpops = dao.getSubpopulations(studyId, false, true);
        assertEquals(1, allSubpops.size());
        
        // Logical delete works...
        dao.deleteSubpopulation(studyId, finalSubpop.getGuid(), false, false);
        Subpopulation deletedSubpop = dao.getSubpopulation(studyId, finalSubpop.getGuid());
        assertTrue(deletedSubpop.isDeleted());
        
        // ... and it hides the subpop in the query used to find subpopulations for a user
        List<Subpopulation> subpopulations = dao.getSubpopulations(studyId, false, false);
        assertEquals(0, subpopulations.size());
        
        // However, the subpopulation has not been physically deleted and can be retrieved as part of the list
        allSubpops = dao.getSubpopulations(studyId, false, true);
        assertEquals(1, allSubpops.size());
    }
    
    @Test
    public void copySubpopulation() throws Exception {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setRequired(true);
        
        Criteria criteria = TestUtils.createCriteria(2, 10, null, null);
        subpop.setCriteria(criteria);
        
        // CREATE
        Subpopulation savedSubpop = dao.createSubpopulation(subpop);

        String json = BridgeObjectMapper.get().writeValueAsString(savedSubpop);
        Subpopulation subpop2 = BridgeObjectMapper.get().readValue(json, Subpopulation.class);
        // This is JsonIgnored, so add it back.
        subpop2.setStudyIdentifier(savedSubpop.getStudyIdentifier());
        // And mess with the fields
        subpop2.setDeleted(true);
        subpop2.setDefaultGroup(true);
        subpop2.setPublishedConsentCreatedOn(100L);
        
        Subpopulation copy = dao.createSubpopulation(subpop2);
        assertNotEquals(savedSubpop.getGuid(), copy.getGuid());
        assertFalse(copy.isDeleted());
        assertFalse(copy.isDefaultGroup());
        assertEquals((Long)1L, savedSubpop.getVersion());
        assertEquals((Long)1L, copy.getVersion());
        assertEquals(0L, copy.getPublishedConsentCreatedOn());
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
        assertTrue(subpops.get(0).isDefaultGroup());
        assertTrue(subpops.get(0).isRequired());

        Subpopulation subpop = subpops.get(0);
        assertEquals("Default Consent Group", subpop.getName());
        assertEquals(studyId.getIdentifier(), subpop.getGuidString());
        assertTrue(subpop.isDefaultGroup());
        
        // Cannot set this group to be unrequired
        subpop.setDefaultGroup(false);
        subpop = dao.updateSubpopulation(subpop);
        assertTrue(subpop.isDefaultGroup());
        
        // Cannot delete a required subpopulation
        try {
            dao.deleteSubpopulation(studyId, subpop.getGuid(), false, false);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals("Cannot delete the default subpopulation for a study.", e.getMessage());
        }
    }
    
    @Test
    public void cannotDeleteOrRequireSubpopOnCreate() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setStudyIdentifier(studyId.getIdentifier());
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
        subpop.setGuidString(BridgeUtils.generateGuid());
        dao.updateSubpopulation(subpop);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void cannotUpdateASubpopThatIsDeleted() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        dao.deleteSubpopulation(studyId, subpop.getGuid(), false, false);
        
        // This should now throw ENFE
        dao.updateSubpopulation(subpop);
    }
    
    @Test
    public void createDefaultSubpopulation() {
        // This is not currently called outside of the DAO but it will be when creating studies.
        Subpopulation subpop = dao.createDefaultSubpopulation(studyId);
        assertEquals("Default Consent Group", subpop.getName());
        assertEquals(studyId.getIdentifier(), subpop.getGuidString());
        assertEquals(studyId.getIdentifier(), subpop.getStudyIdentifier());
        assertNotNull(subpop.getGuidString());
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
        dao.deleteSubpopulation(studyId, SubpopulationGuid.create("guidDoesNotExist"), false, false);
    }
    
    @Test
    public void canPermanentlyDeleteOneSubpopulation() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        
        dao.deleteSubpopulation(studyId, subpop.getGuid(), true, false);
        
        // This requests all subpopulations including the just-deleted subpopulation
        List<Subpopulation> subpops = dao.getSubpopulations(studyId, false, true);
        assertEquals(0, subpops.size());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void logicallyDeletingLogicallyDeletedSubpopThrowsNotFoundException() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        dao.deleteSubpopulation(studyId, subpop.getGuid(), false, false);
        
        // This should just appear to not exist and throw a 404 exception
        dao.deleteSubpopulation(studyId, subpop.getGuid(), false, false);
    }
    
    @Test
    public void physicallyDeletingLogicallyDeletedSubpopWorks() {
        Subpopulation subpop = createSubpop("Name", null, null, null);
        dao.deleteSubpopulation(studyId, subpop.getGuid(), false, false);
        
        dao.deleteSubpopulation(studyId, subpop.getGuid(), true, false);
        
        List<Subpopulation> allSubpops = dao.getSubpopulations(studyId, false, true);
        assertTrue(allSubpops.isEmpty());
    }
    
    private Subpopulation createSubpop(String name, Integer min, Integer max, String group) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setName(name);
        subpop.setGuidString(BridgeUtils.generateGuid());
        
        Criteria criteria = Criteria.create();
        if (min != null) {
            criteria.setMinAppVersion(OperatingSystem.IOS, min);
        }
        if (max != null) {
            criteria.setMaxAppVersion(OperatingSystem.IOS, max);
        }
        if (group != null) {
            criteria.setAllOfGroups(Sets.newHashSet(group));
        }
        subpop.setCriteria(criteria);
        
        return dao.createSubpopulation(subpop);
    }
}
