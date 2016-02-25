package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.HashSet;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.models.Criteria;

import com.google.common.collect.Sets;

public class TestUtilsTest {

    private static final HashSet<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    private static final HashSet<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    
    @Test
    public void createCriteriaWithArguments() {
        Criteria criteria = TestUtils.createCriteria(5, 15, ALL_OF_GROUPS, NONE_OF_GROUPS);
        
        assertEquals(new Integer(5), criteria.getMinAppVersion());
        assertEquals(new Integer(15), criteria.getMaxAppVersion());
        assertEquals(ALL_OF_GROUPS, criteria.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, criteria.getNoneOfGroups());
    }
    
    @Test
    public void copyWithNullObject() {
        Criteria newCriteria = TestUtils.copyCriteria(null);
        assertNotNull(newCriteria);
    }

    @Test
    public void copyWithCriteriaObject() {
        Criteria criteria = newCriteria();
        
        Criteria newCriteria = TestUtils.copyCriteria(criteria);
        assertEquals(new Integer(5), newCriteria.getMinAppVersion());
        assertEquals(new Integer(15), newCriteria.getMaxAppVersion());
        assertEquals(ALL_OF_GROUPS, newCriteria.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, newCriteria.getNoneOfGroups());
        
        assertFalse( criteria == newCriteria);
    }
    
    private Criteria newCriteria() {
        // Don't use an interface method, that's what we're testing here.
        Criteria criteria = new DynamoCriteria();
        criteria.setMinAppVersion(5);
        criteria.setMaxAppVersion(15);
        criteria.setAllOfGroups(ALL_OF_GROUPS);
        criteria.setNoneOfGroups(NONE_OF_GROUPS);
        return criteria;
    }
}
