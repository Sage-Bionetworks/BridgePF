package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.ScheduleDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Schedule;

public class ScheduleServiceImpl implements ScheduleService {

    private ScheduleDao scheduleDao;
    
    public void setScheduleDao(ScheduleDao scheduleDao) {
        this.scheduleDao = scheduleDao;
    }
    
    @Override
    public List<Schedule> getSchedules(Study study, User user) {
        return scheduleDao.getSchedules(study, user);
    }

}
