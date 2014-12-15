package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.UUID;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.ScheduleValidator;
import org.springframework.validation.Errors;

import com.google.common.collect.Lists;

@BridgeTypeName("ABTestScheduleStrategy")
public class ABTestScheduleStrategy implements ScheduleStrategy {
    
    public static class ScheduleGroup {
        private int percentage;
        private Schedule schedule;
        public ScheduleGroup() { }
        public ScheduleGroup(int percentage, Schedule schedule) {
            this.percentage = percentage;
            this.schedule = schedule;
        }
        public int getPercentage() {
            return percentage;
        }
        public void setPercentage(int perc) {
            this.percentage = perc;
        }
        public Schedule getSchedule() {
            return schedule;
        }
        public void setSchedule(Schedule schedule) {
            this.schedule = schedule;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + percentage;
            result = prime * result + ((schedule == null) ? 0 : schedule.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ScheduleGroup other = (ScheduleGroup) obj;
            if (percentage != other.percentage)
                return false;
            if (schedule == null) {
                if (other.schedule != null)
                    return false;
            } else if (!schedule.equals(other.schedule))
                return false;
            return true;
        }
        @Override
        public String toString() {
            return "ScheduleGroup [percentage=" + percentage + ", schedule=" + schedule + "]";
        }
    }
    
    private List<ScheduleGroup> groups = Lists.newArrayList();
    
    public List<ScheduleGroup> getScheduleGroups() {
        return groups;
    }
    public void setScheduleGroups(List<ScheduleGroup> groups) {
        this.groups = groups; 
    }
    public void addGroup(int percent, Schedule schedule) {
        this.groups.add(new ScheduleGroup(percent, schedule)); 
    }
    
    @Override
    public Schedule getScheduleForUser(Study study, SchedulePlan plan, User user) {
        if (groups.isEmpty()) {
            return null;
        }
        // Randomly assign to a group, weighted based on the percentage representation of the group.
        ScheduleGroup group = null;
        long seed = UUID.fromString(plan.getGuid()).getLeastSignificantBits()
                + UUID.fromString(user.getHealthCode()).getLeastSignificantBits();        
        
        int i = 0;
        int perc = (int)(seed % 100.0) + 1;
        while (perc > 0) {
            group = groups.get(i++);
            perc -= group.getPercentage();
        }
        return group.getSchedule();
    }
    @Override
    public boolean doesScheduleSurvey(GuidCreatedOnVersionHolder keys) {
        for (ScheduleGroup group : groups) {
            if (group.getSchedule().isScheduleFor(keys)) {
                return true;
            }
        }
        return false;
    }
    @Override
    public void validate(Errors errors) {
        int percentage = 0;
        for (ScheduleGroup group : groups) {
            percentage += group.getPercentage();
        }
        if (percentage != 100) {
            errors.reject(String.format("groups in AB test plan add up to %s\u0025 and not 100\u0025 (give 20\u0025 as 20, for example)", percentage));
        }
        for (int i=0; i < groups.size(); i++) {
            ScheduleGroup group = groups.get(i);
            errors.pushNestedPath("group[" + i + "]");
            if (group.getSchedule() == null){
                errors.reject("at least one AB test plan group is missing a schedule");
                errors.popNestedPath();
                return;
            } else {
                errors.pushNestedPath("schedule");
                new ScheduleValidator().validate(group.getSchedule(), errors);
                errors.popNestedPath();
            }
            errors.popNestedPath();
        }
        
        for (ScheduleGroup group : groups) {
            if (group.getSchedule() == null){
                errors.reject("at least one AB test plan group is missing a schedule");
                return;
            } else {
                errors.pushNestedPath("schedule");
                new ScheduleValidator().validate(group.getSchedule(), errors);
                errors.popNestedPath();
            }
        }
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groups == null) ? 0 : groups.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ABTestScheduleStrategy other = (ABTestScheduleStrategy) obj;
        if (groups == null) {
            if (other.groups != null)
                return false;
        } else if (!groups.equals(other.groups))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "ABTestScheduleStrategy [groups=" + groups + "]";
    }

}
