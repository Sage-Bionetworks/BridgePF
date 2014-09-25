package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.Schedule;

public interface ScheduleDao {

    public List<Schedule> getSchedules(String userId);
    
    public List<Schedule> createSchedules(List<Schedule> schedules);
    
    public void deleteSchedules(List<Schedule> schedules);
    
}
