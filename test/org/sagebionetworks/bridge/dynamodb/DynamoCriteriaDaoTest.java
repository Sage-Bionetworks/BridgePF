package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoCriteriaDaoTest {

    private static final HashSet<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    private static final HashSet<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    @Resource
    DynamoCriteriaDao criteriaDao;

    @Test
    public void deleteAndGetAreQuiet() {
        String key = BridgeUtils.generateGuid();
        // Before creating an object, create and delete do not throw errors and do something sensible
        criteriaDao.deleteCriteria(key); // no exception
        Criteria retrieved = criteriaDao.getCriteria(key); // just return null
        assertNull(retrieved);
    }
    
    @Test
    public void canCrudCriteria() {
        Criteria criteria = Criteria.create();
        criteria.setKey("key");
        criteria.setMinAppVersion(2);
        criteria.setMaxAppVersion(8);
        criteria.setAllOfGroups(ALL_OF_GROUPS);
        criteria.setNoneOfGroups(NONE_OF_GROUPS);
        
        Criteria result = criteriaDao.createOrUpdateCriteria(criteria);
        assertEquals("key", result.getKey());
        assertEquals(new Integer(2), result.getMinAppVersion());
        assertEquals(new Integer(8), result.getMaxAppVersion());
        assertEquals(ALL_OF_GROUPS, result.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, result.getNoneOfGroups());
        
        Criteria retrieved = criteriaDao.getCriteria("key");
        assertEquals("key", retrieved.getKey());
        assertEquals(new Integer(2), retrieved.getMinAppVersion());
        assertEquals(new Integer(8), retrieved.getMaxAppVersion());
        assertEquals(ALL_OF_GROUPS, retrieved.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, retrieved.getNoneOfGroups());
        
        // Try nullifying this, setting a property
        criteria.setAllOfGroups(null);
        criteria.setMinAppVersion(4);
        criteriaDao.createOrUpdateCriteria(criteria);
        
        retrieved = criteriaDao.getCriteria("key");
        assertEquals(new Integer(4), retrieved.getMinAppVersion());
        assertTrue(retrieved.getAllOfGroups().isEmpty());
        
        criteriaDao.deleteCriteria("key");
        
        // Now that this doesn't exist, we should get an empty object back.
        retrieved = criteriaDao.getCriteria("key");
        assertNull(retrieved);
    }
    
    @Test
    public void canCopy() {
        Criteria criteria = Criteria.create();
        criteria.setKey("key1");
        criteria.setMinAppVersion(12);
        
        Criteria newCriteria = Criteria.copy(criteria);
        assertEquals(criteria.getMinAppVersion(), newCriteria.getMinAppVersion());
    }
    
    @Test
    public void savingNonDynamoCriteriaImplementationWorks() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Test subpopulation");
        subpop.setMinAppVersion(2);
        subpop.setMaxAppVersion(10);
        subpop.setAllOfGroups(ALL_OF_GROUPS);
        subpop.setNoneOfGroups(NONE_OF_GROUPS);
        subpop.setStudyIdentifier("test-key");
        subpop.setGuid(SubpopulationGuid.create(BridgeUtils.generateGuid()));
        
        try {
            Criteria loaded = criteriaDao.createOrUpdateCriteria(subpop);
            
            // It should have been set as an object on the subpopulation
            assertEquals(subpop.getKey(), loaded.getKey());
            assertEquals(new Integer(2), loaded.getMinAppVersion());
            assertEquals(new Integer(10), loaded.getMaxAppVersion());
            assertEquals(ALL_OF_GROUPS, loaded.getAllOfGroups());
            assertEquals(NONE_OF_GROUPS, loaded.getNoneOfGroups());
            
            // It should also have been saved
            Criteria saved = criteriaDao.getCriteria(subpop.getKey());
            assertEquals(subpop.getKey(), saved.getKey());
            assertEquals(new Integer(2), saved.getMinAppVersion());
            assertEquals(new Integer(10), saved.getMaxAppVersion());
            assertEquals(ALL_OF_GROUPS, saved.getAllOfGroups());
            assertEquals(NONE_OF_GROUPS, saved.getNoneOfGroups());
        } finally {
            criteriaDao.deleteCriteria(subpop.getKey());
        }
    }

}
