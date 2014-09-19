package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

// Another plan will be the ABTest plan, which will have a structure of 
// schedules and an implementation of how to select randomly the correct
// number of users for each schedule.
public class SimpleSchedulePlan extends DynamoSchedulePlan {

    private static ObjectMapper mapper = new ObjectMapper();
    
    private Schedule schedule;
    
    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
    
    public Schedule getSchedule() {
        if (schedule == null) {
            schedule = JsonUtils.asSchedule(data, "schedule");
        }
        return schedule;
    }
    
    @Override
    public ObjectNode getData() {
        if (schedule != null) {
            data.put("schedule", mapper.valueToTree(schedule));
        }
        return super.getData();
    }

    @Override
    public List<Schedule> generateSchedules(Study study, List<User> users) {
        List<Schedule> schedules = Lists.newArrayListWithCapacity(users.size());
        for (User user : users) {
            Schedule sch = new Schedule(getSchedule());
            sch.setStudyUserCompoundKey(study.getKey()+":"+user.getId());
            schedules.add(sch);
        }
        return schedules;
    }
    
}
