package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.models.Criteria;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoCriteriaDaoTest {

    @Resource
    DynamoCriteriaDao criteriaDao;

    @Test
    public void deleteAndGetAreQuiet() {
        // Before creating an object, create and delete do not throw errors and do something sensible
        criteriaDao.deleteCriteria("key"); // no exception
        Criteria retrieved = criteriaDao.getCriteria("key"); // just return null
        assertNull(retrieved);
    }
    
    @Test
    public void canCrudCriteria() {
        Criteria criteria = Criteria.create();
        criteria.setKey("key");
        criteria.setMinAppVersion(2);
        criteria.setMaxAppVersion(8);
        criteria.setAllOfGroups(Sets.newHashSet("a","b"));
        criteria.setNoneOfGroups(Sets.newHashSet("c","d"));
        
        criteriaDao.createOrUpdateCriteria(criteria);
        
        Criteria retrieved = criteriaDao.getCriteria("key");
        assertEquals("key", retrieved.getKey());
        assertEquals(new Integer(2), retrieved.getMinAppVersion());
        assertEquals(new Integer(8), retrieved.getMaxAppVersion());
        assertEquals(Sets.newHashSet("a","b"), retrieved.getAllOfGroups());
        assertEquals(Sets.newHashSet("c","d"), retrieved.getNoneOfGroups());
        
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

}
