package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;

public interface ScheduleService {

    public List<Schedule> getSchedules(Study study, User user);
    
}
