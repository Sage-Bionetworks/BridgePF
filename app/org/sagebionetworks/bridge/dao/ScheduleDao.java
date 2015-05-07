package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.Study;

public interface ScheduleDao {

    public List<Schedule> getSchedules(Study study, User user);
    
    public List<Schedule> createSchedules(List<Schedule> schedules);
    
    public void deleteSchedules(SchedulePlan plan);
    
    public void deleteSchedules(Study study, User user);

}
