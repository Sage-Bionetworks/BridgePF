package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;

import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;

public class SchedulePlanServiceImpl implements SchedulePlanService {
    
    private SchedulePlanDao schedulePlanDao;
    private Validator validator;
    
    public void setSchedulePlanDao(SchedulePlanDao schedulePlanDao) {
        this.schedulePlanDao = schedulePlanDao;
    }
    
    public void setValidator(Validator validator) {
        this.validator = validator;
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
        Validate.entityThrowingException(validator, plan);
        checkNewEntity(plan, plan.getGuid(), "Schedule plan has a GUID; it may already exist");
        checkNewEntity(plan, plan.getVersion(), "Schedule plan has a version value; it may already exist");
        
        return schedulePlanDao.createSchedulePlan(plan);
    }

    @Override
    public SchedulePlan updateSchedulePlan(SchedulePlan plan) {
        Validate.entityThrowingException(validator, plan);
        return schedulePlanDao.updateSchedulePlan(plan);
    }

    @Override
    public void deleteSchedulePlan(Study study, String guid) {
        schedulePlanDao.deleteSchedulePlan(study, guid);
    }
    
}
