package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.sagebionetworks.bridge.validators.ScheduleValidator;
import org.springframework.validation.Errors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public final class ABTestScheduleStrategy implements ScheduleStrategy {
    
    private List<ABTestGroup> groups = Lists.newArrayList();
    
    public List<ABTestGroup> getScheduleGroups() {
        return groups;
    }
    public void setScheduleGroups(List<ABTestGroup> groups) {
        this.groups = groups; 
    }
    public void addGroup(int percent, Schedule schedule) {
        this.groups.add(new ABTestGroup(percent, schedule)); 
    }
    
    @Override
    public Schedule getScheduleForUser(SchedulePlan plan, ScheduleContext context) {
        if (groups.isEmpty()) {
            return null;
        }
        // Randomly assign to a group, weighted based on the percentage representation of the group.
        ABTestGroup group = null;
        long seed = UUID.fromString(plan.getGuid()).getLeastSignificantBits()
                + UUID.fromString(context.getCriteriaContext().getHealthCode()).getLeastSignificantBits();        
        
        int i = 0;
        int perc = (int)(seed % 100.0) + 1;
        while (perc > 0) {
            group = groups.get(i++);
            perc -= group.getPercentage();
        }
        return group.getSchedule();
    }
    @Override
    public void validate(Set<String> dataGroups, Set<String> substudyIds, Set<String> taskIdentifiers, Errors errors) {
        int percentage = 0;
        for (ABTestGroup group : groups) {
            percentage += group.getPercentage();
        }
        if (percentage != 100) {
        	errors.rejectValue("scheduleGroups", "groups must add up to 100%");
        }
        for (int i=0; i < groups.size(); i++) {
            ABTestGroup group = groups.get(i);
            errors.pushNestedPath("scheduleGroups["+i+"]");
            if (group.getSchedule() == null){
                errors.rejectValue("schedule", "is required");
            } else {
                errors.pushNestedPath("schedule");
                new ScheduleValidator(taskIdentifiers).validate(group.getSchedule(), errors);
                errors.popNestedPath();
            }
            errors.popNestedPath();
        }
    }

    @Override
    public List<Schedule> getAllPossibleSchedules() {
        List<Schedule> lists = Lists.newArrayListWithCapacity(groups.size());
        for (ABTestGroup group : groups) {
            lists.add(group.getSchedule());
        }
        // The list is immutable, the contents are not. We use this to fix up activities, for example.
        return ImmutableList.copyOf(lists);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(groups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ABTestScheduleStrategy other = (ABTestScheduleStrategy) obj;
        return Objects.equals(groups, other.groups);
    }

    @Override
    public String toString() {
        return "ABTestScheduleStrategy [groups=" + groups + "]";
    }

}
