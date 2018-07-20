package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.CompoundActivityDefinitionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.CompoundActivityDefinitionValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Compound Activity Definition Service. */
@Component
public class CompoundActivityDefinitionService {
    private SchedulePlanService schedulePlanService;
    
    private CompoundActivityDefinitionDao compoundActivityDefDao;

    @Autowired
    public final void setSchedulePlanService(SchedulePlanService schedulePlanService) {
        this.schedulePlanService = schedulePlanService;
    }
    
    /** DAO, autowired by Spring. */
    @Autowired
    public final void setCompoundActivityDefDao(CompoundActivityDefinitionDao compoundActivityDefDao) {
        this.compoundActivityDefDao = compoundActivityDefDao;
    }

    /** Creates a compound activity definition. */
    public CompoundActivityDefinition createCompoundActivityDefinition(StudyIdentifier studyId,
            CompoundActivityDefinition compoundActivityDefinition) {
        // Set study to prevent people from creating defs in other studies.
        compoundActivityDefinition.setStudyId(studyId.getIdentifier());

        // validate def
        Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, compoundActivityDefinition);

        // call through to dao
        return compoundActivityDefDao.createCompoundActivityDefinition(compoundActivityDefinition);
    }

    /** Deletes a compound activity definition. */
    public void deleteCompoundActivityDefinition(StudyIdentifier studyId, String taskId) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }
        checkConstraintViolations(studyId, taskId);
        
        // call through to dao
        compoundActivityDefDao.deleteCompoundActivityDefinition(studyId, taskId);
    }

    /** Deletes all compound activity definitions in the specified study. Used when we physically delete a study. */
    public void deleteAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId) {
        // no user input - study comes from controller

        // call through to dao
        compoundActivityDefDao.deleteAllCompoundActivityDefinitionsInStudy(studyId);
    }

    /** List all compound activity definitions in a study. */
    public List<CompoundActivityDefinition> getAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId) {
        // no user input - study comes from controller

        // call through to dao
        return compoundActivityDefDao.getAllCompoundActivityDefinitionsInStudy(studyId);
    }

    /** Get a compound activity definition by ID. */
    public CompoundActivityDefinition getCompoundActivityDefinition(StudyIdentifier studyId, String taskId) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }

        // call through to dao
        return compoundActivityDefDao.getCompoundActivityDefinition(studyId, taskId);
    }

    /** Update a compound activity definition. */
    public CompoundActivityDefinition updateCompoundActivityDefinition(StudyIdentifier studyId, String taskId,
            CompoundActivityDefinition compoundActivityDefinition) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }

        // Set the studyId and taskId. This prevents people from updating the wrong def or updating a def in another
        // study.
        compoundActivityDefinition.setStudyId(studyId.getIdentifier());
        compoundActivityDefinition.setTaskId(taskId);

        // validate def
        Validate.entityThrowingException(CompoundActivityDefinitionValidator.INSTANCE, compoundActivityDefinition);

        // call through to dao
        return compoundActivityDefDao.updateCompoundActivityDefinition(compoundActivityDefinition);
    }
    
    private void checkConstraintViolations(StudyIdentifier studyId, String taskId) {
        // You cannot physically delete a compound activity if it is referenced by a logically deleted schedule plan. 
        // It's possible the schedule plan could be restored. All you can do is logically delete the compound activity.
        List<SchedulePlan> plans = schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, studyId, true);
        SchedulePlan match = findFirstMatchingPlan(plans, taskId);
        if (match != null) {
            throw new ConstraintViolationException.Builder().withMessage(
                    "Cannot delete compound activity definition: it is referenced by a schedule plan that is still accessible through the API")
                    .withEntityKey("taskId", taskId).withEntityKey("type", "CompoundActivityDefinition")
                    .withReferrerKey("guid", match.getGuid()).withReferrerKey("type", "SchedulePlan").build();
        }
    }

    private SchedulePlan findFirstMatchingPlan(List<SchedulePlan> plans, String taskId) {
        for (SchedulePlan plan : plans) {
            List<Schedule> schedules = plan.getStrategy().getAllPossibleSchedules();
            for (Schedule schedule : schedules) {
                for (Activity activity : schedule.getActivities()) {
                    CompoundActivity compoundActivity = activity.getCompoundActivity();
                    if (compoundActivity != null) {
                        if (compoundActivity.getTaskIdentifier().equals(taskId)) {
                            return plan;
                        }
                    }
                }
            }
        }
        return null;
    }    
}
