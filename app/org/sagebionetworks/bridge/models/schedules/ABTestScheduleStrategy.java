package org.sagebionetworks.bridge.models.schedules;

import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

public class ABTestScheduleStrategy implements ScheduleStrategy {
    
    private static ObjectMapper mapper = new ObjectMapper();
    private static final String SCHEDULE_GROUPS = "scheduleGroups";
    
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
    public void initialize(ObjectNode node) {
        this.groups = JsonUtils.asEntityList(node, SCHEDULE_GROUPS, ScheduleGroup.class);
    }
    @Override
    public void persist(ObjectNode node) {
        node.put(SCHEDULE_GROUPS, mapper.valueToTree(groups));
    }
    /**
     * Will divide users into the groups by a percentage (randomly), with any rounding 
     * fractions dropped, so there may be a very few users who are not in the study. 
     * This API loads all the users; we expect there may be thousands, but not tens 
     * of thousands or more.
     */
    @Override
    public List<Schedule> generateSchedules(ScheduleContext context) {
        int size = context.getUsers().size();
        List<Schedule> list = Lists.newArrayListWithCapacity(size);
        Collections.shuffle(context.getUsers());

        int i = 0;
        for (ScheduleGroup group : groups) {
            int number = (int)Math.floor((group.getPercentage()*size)/100);
            for (int j=0; j < number; j++) {
                User user = context.getUsers().get(i++);
                Schedule schedule = new Schedule(group.getSchedule());
                schedule.setStudyAndUser(context.getStudy(), user);
                list.add(schedule);
            }
        }
        return list;
    }
    @Override
    public void validate(Messages messages) {
        int percentage = 0;
        for (ScheduleGroup group : groups) {
            percentage += group.getPercentage();
        }
        if (percentage != 100) {
            messages.add("groups in AB test plan add up to %s\u0025 and not 100\u0025 (give 20\u0025 as 20, for example)", percentage);
        }
        for (ScheduleGroup group : groups) {
            if (group.getSchedule() == null){
                messages.add("at least one AB test plan group is missing a schedule");
                return;
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
