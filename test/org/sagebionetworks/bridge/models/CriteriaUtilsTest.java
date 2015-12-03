package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.Sets;

public class CriteriaUtilsTest {
    
    private static Set<String> EMPTY_SET = Sets.newHashSet();
    
    private class SimpleCriteria implements Criteria {
        private final Set<String> required;
        private final Set<String> prohibited;
        private final Integer min;
        private final Integer max;
        
        public SimpleCriteria(Set<String> required, Set<String> prohibited, Integer min, Integer max) {
            this.required = required;
            this.prohibited = prohibited;
            this.min = min;
            this.max = max;
        }
        @Override
        public Integer getMinAppVersion() {
            return min;
        }
        @Override
        public Integer getMaxAppVersion() {
            return max;
        }
        @Override
        public Set<String> getAllOfGroups() {
            return required;
        }
        @Override
        public Set<String> getNoneOfGroups() {
            return prohibited;
        }
    }
    
    @Test
    public void matchesAgainstNothing() {
        ScheduleContext context = getContext();
        
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, null, null)));
    }
    
    @Test
    public void matchesAppRange() {
        ScheduleContext context = getContext();
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, null, 4)));
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, 1, null)));
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, 1, 4)));
    }
    
    @Test
    public void filtersAppRange() {
        ScheduleContext context = getContext();
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, null, 2)));
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, 5, null)));
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, 6, 11)));
    }
    
    @Test
    public void allOfGroupsMatch() {
        ScheduleContext context = getContext(); // has group1, and group2
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1"), EMPTY_SET, null, null)));
        // Two groups are required, that still matches
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1", "group2"), EMPTY_SET, null, null)));
        // but this doesn't
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1", "group3"), EMPTY_SET, null, null)));
    }
    
    @Test
    public void noneOfGroupsMatch() {
        ScheduleContext context = getContext(); // has group1, and group2
        // Here, any group at all prevents a match.
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, Sets.newHashSet("group3", "group1"), null, null)));
    }

    @Test
    public void noneOfGroupsDefinedButDontPreventMatch() {
        ScheduleContext context = getContext(); // does not have group3, so it is matched
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, Sets.newHashSet("group3"), null, null)));
    }
    
    @Test
    public void matchingWithMiminalContextDoesNotCrash() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT).build();
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(EMPTY_SET, EMPTY_SET, null, null)));
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1"), EMPTY_SET, null, null)));
    }
    
    @Test
    public void validateMinMaxSameVersionOK() {
        SimpleCriteria criteria = new SimpleCriteria(EMPTY_SET, EMPTY_SET, 1, 1);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, errors);
        assertFalse(errors.hasErrors());
    }

    @Test
    public void validateCannotSetMaxUnderMinAppVersion() {
        SimpleCriteria criteria = new SimpleCriteria(EMPTY_SET, EMPTY_SET, 2, 1);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, errors);
        assertEquals("cannot be less than minAppVersion", errors.getFieldErrors("maxAppVersion").get(0).getCode());
    }
    
    @Test
    public void validateCannotSetMinLessThanZero() {
        SimpleCriteria criteria = new SimpleCriteria(EMPTY_SET, EMPTY_SET, -2, null);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, errors);
        assertEquals("cannot be negative", errors.getFieldErrors("minAppVersion").get(0).getCode());
    }
    
    @Test
    public void validateDataGroupSetsCannotBeNull() { 
        SimpleCriteria criteria = new SimpleCriteria(null, null, null, null);
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, errors);
        assertEquals("cannot be null", errors.getFieldErrors("allOfGroups").get(0).getCode());
        assertEquals("cannot be null", errors.getFieldErrors("noneOfGroups").get(0).getCode());
    }
    
    @Test
    public void validateDataGroupCannotBeWrong() {
        SimpleCriteria criteria = new SimpleCriteria(Sets.newHashSet("group1"), Sets.newHashSet("group2"), null, null);
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, Sets.newHashSet("group3"), errors);
        assertEquals("'group1' is not in enumeration: group3", errors.getFieldErrors("allOfGroups").get(0).getCode());
        assertEquals("'group2' is not in enumeration: group3", errors.getFieldErrors("noneOfGroups").get(0).getCode());
        
    }
    
    private ScheduleContext getContext() {
        return new ScheduleContext.Builder()
            .withClientInfo(ClientInfo.fromUserAgentCache("app/4"))
            .withUserDataGroups(Sets.newHashSet("group1", "group2")).build();
    }

}
