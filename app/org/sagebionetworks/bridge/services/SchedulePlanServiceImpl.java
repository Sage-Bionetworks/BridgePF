package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public class SchedulePlanServiceImpl implements SchedulePlanService {

    private SchedulePlanDao schedulePlanDao;
    
    public void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }

    @Override
    public List<SchedulePlan> getSchedulePlans(Study study) {
        return schedulePlanDao.getSchedulePlans(study);
    }

    @Override
    public SchedulePlan getSchedulePlan(Study study, String guid) {
        return schedulePlanDao.getSchedulePlan(study, guid);
    }

    @Override
    public SchedulePlan createSchedulePlan(SchedulePlan plan) {
        return schedulePlanDao.createSchedulePlan(plan);
    }

    @Override
    public SchedulePlan updateSchedulePlan(SchedulePlan plan) {
        return schedulePlanDao.updateSchedulePlan(plan);
    }

    @Override
    public void deleteSchedulePlan(Study study, String guid) {
        schedulePlanDao.deleteSchedulePlan(study, guid);
    }
    
}
