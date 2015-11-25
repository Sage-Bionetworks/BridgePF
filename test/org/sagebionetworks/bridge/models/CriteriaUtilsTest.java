package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.models.schedules.ScheduleContext;

import com.google.common.collect.Sets;

public class CriteriaUtilsTest {
    
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
        
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), null, null)));
    }
    
    @Test
    public void matchesAppRange() {
        ScheduleContext context = getContext();
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), null, 4)));
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), 1, null)));
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), 1, 4)));
    }
    
    @Test
    public void filtesAppRange() {
        ScheduleContext context = getContext();
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), null, 2)));
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), 5, null)));
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet(), 6, 11)));
    }
    
    @Test
    public void allOfGroupsMatch() {
        ScheduleContext context = getContext(); // has group1, and group2
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1"), Sets.newHashSet(), null, null)));
        // Two groups are required, that still matches
        assertTrue(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1", "group2"), Sets.newHashSet(), null, null)));
        // but this doesn't
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet("group1", "group3"), Sets.newHashSet(), null, null)));
    }
    
    @Test
    public void noneOfGroupsMatch() {
        ScheduleContext context = getContext(); // has group1, and group2
        // Here, any group at all prevents a match.
        assertFalse(CriteriaUtils.matchCriteria(context, new SimpleCriteria(Sets.newHashSet(), Sets.newHashSet("group3", "group1"), null, null)));
    }

    private ScheduleContext getContext() {
        return new ScheduleContext.Builder()
            .withClientInfo(ClientInfo.fromUserAgentCache("app/4"))
            .withUserDataGroups(Sets.newHashSet("group1", "group2")).build();
    }

}
