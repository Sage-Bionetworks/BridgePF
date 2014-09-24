package org.sagebionetworks.bridge.models.schedules;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.validators.Messages;

import com.google.common.collect.Lists;

public class ABTestScheduleStrategy implements ScheduleStrategy {
    
    private static final Random rand = new Random();
    
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
    public Schedule scheduleNewUser(ScheduleContext context, User user) {
        // Randomly assign to a group, weighted based on the percentage representation of the group.
        ScheduleGroup group = null;
        int i = 0;
        int perc = rand.nextInt(100)+1; // 1-100
        while (perc > 0) {
            group = groups.get(i++);
            perc -= group.getPercentage();
        }
        Schedule schedule = new Schedule(group.getSchedule());
        schedule.setStudyAndUser(context.getStudy(), user);
        return schedule;
    }
    
    /**
     * This API loads all the users; we expect there may be thousands, but not tens 
     * of thousands or more, but this may have to change eventually.
     */
    @Override
    public List<Schedule> scheduleExistingUsers(ScheduleContext context) {
        // linear time, as you'd expect
        Collections.shuffle(context.getUsers());
        int size = context.getUsers().size();
        List<Schedule> list = Lists.newArrayListWithCapacity(size);
        Schedule schedule = null;
        
        // Again iterate through list of users, swapping group by proportion
        int i = 0;
        for (ScheduleGroup group : groups) {
            int number = (int)Math.floor((group.getPercentage()*size)/100);
            for (int j=0; j < number; j++) {
                User user = context.getUsers().get(i++);
                schedule = new Schedule(group.getSchedule());
                schedule.setStudyAndUser(context.getStudy(), user);
                list.add(schedule);
            }
        }
        // Assign remainders. They are assigned as new users.
        for (int j=i; j < size; j++) {
            schedule = scheduleNewUser(context, context.getUsers().get(j));
            list.add(schedule);
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
