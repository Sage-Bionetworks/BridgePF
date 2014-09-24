package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;

public interface ScheduleDao {

    public List<Schedule> getSchedules(Study study, User user);

    public List<Schedule> createSchedules(List<Schedule> schedules);

    public void deleteSchedules(String schedulePlanGuid);
    
}
