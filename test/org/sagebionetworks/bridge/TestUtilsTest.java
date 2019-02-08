package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.HashSet;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;

import com.google.common.collect.Sets;

public class TestUtilsTest {

    private static final HashSet<String> ALL_OF_GROUPS = Sets.newHashSet("a","b");
    private static final HashSet<String> NONE_OF_GROUPS = Sets.newHashSet("c","d");
    
    @Test
    public void testAssertExceptionThrowsTheExceptionWithRightMessage() {
        TestUtils.assertException(EntityNotFoundException.class, "Account not found.", () -> {
            throw new EntityNotFoundException(Account.class);});
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void testAssertExceptionThrowsTheExceptionWithWrongMessage() {
        TestUtils.assertException(EntityNotFoundException.class, "Account not found", () -> {
            throw new EntityNotFoundException(AppConfig.class);});
    }
    
    @Test(expected = ConstraintViolationException.class)
    public void testAssertThrowsExceptionUnrelatedException() {
        TestUtils.assertException(EntityNotFoundException.class, "Account not found", () -> {
            throw new ConstraintViolationException.Builder().build();});
    }
    
    @Test
    public void testAssertThrowsExceptionNoException() {
        try {
            TestUtils.assertException(Exception.class, "Any message at all", () -> {});
            fail("Should have thrown exception");
        } catch(Throwable e) {
            assertEquals("Should have thrown exception: java.lang.Exception, message: 'Any message at all'", e.getMessage());
        }
    }
    
    @Test
    public void createCriteriaWithArguments() {
        Criteria criteria = TestUtils.createCriteria(5, 15, ALL_OF_GROUPS, NONE_OF_GROUPS);

        assertEquals(new Integer(5), criteria.getMinAppVersion(IOS));
        assertEquals(new Integer(15), criteria.getMaxAppVersion(IOS));
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
        assertEquals(new Integer(5), newCriteria.getMinAppVersion(IOS));
        assertEquals(new Integer(15), newCriteria.getMaxAppVersion(IOS));
        assertEquals(new Integer(12), newCriteria.getMaxAppVersion(ANDROID));
        assertEquals(ALL_OF_GROUPS, newCriteria.getAllOfGroups());
        assertEquals(NONE_OF_GROUPS, newCriteria.getNoneOfGroups());
        assertEquals(Sets.newHashSet(IOS, ANDROID), newCriteria.getAppVersionOperatingSystems());
        assertFalse( criteria == newCriteria);
    }
    
    private Criteria newCriteria() {
        // Don't use an interface method, that's what we're testing here.
        Criteria criteria = new DynamoCriteria();
        criteria.setMinAppVersion(IOS, 5);
        criteria.setMaxAppVersion(IOS, 15);
        criteria.setMaxAppVersion(ANDROID, 12);
        criteria.setAllOfGroups(ALL_OF_GROUPS);
        criteria.setNoneOfGroups(NONE_OF_GROUPS);
        return criteria;
    }
}
