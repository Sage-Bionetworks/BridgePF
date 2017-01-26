package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.CompoundActivityDefinitionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.CompoundActivityDefinitionValidator;
import org.sagebionetworks.bridge.validators.Validate;

/** Compound Activity Definition Service. */
@Component
public class CompoundActivityDefinitionService {
    private CompoundActivityDefinitionDao compoundActivityDefDao;

    /** DAO, autowired by Spring. */
    @Autowired
    public final void setCompoundActivityDefDao(CompoundActivityDefinitionDao compoundActivityDefDao) {
        this.compoundActivityDefDao = compoundActivityDefDao;
    }

    /** Creates a compound activity definition. */
    public CompoundActivityDefinition createCompoundActivityDefinition(Study study,
            CompoundActivityDefinition compoundActivityDefinition) {
        // Set study to prevent people from creating defs in other studies.
        compoundActivityDefinition.setStudyId(study.getIdentifier());

        // validate def
        CompoundActivityDefinitionValidator validator = new CompoundActivityDefinitionValidator(
                study.getTaskIdentifiers());
        Validate.entityThrowingException(validator, compoundActivityDefinition);

        // call through to dao
        return compoundActivityDefDao.createCompoundActivityDefinition(compoundActivityDefinition);
    }

    /** Deletes a compound activity definition. */
    public void deleteCompoundActivityDefinition(StudyIdentifier studyId, String taskId) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }

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
    public CompoundActivityDefinition updateCompoundActivityDefinition(Study study, String taskId,
            CompoundActivityDefinition compoundActivityDefinition) {
        // validate user input (taskId)
        if (StringUtils.isBlank(taskId)) {
            throw new BadRequestException("taskId must be specified");
        }

        // Set the studyId and taskId. This prevents people from updating the wrong def or updating a def in another
        // study.
        compoundActivityDefinition.setStudyId(study.getIdentifier());
        compoundActivityDefinition.setTaskId(taskId);

        // validate def
        CompoundActivityDefinitionValidator validator = new CompoundActivityDefinitionValidator(
                study.getTaskIdentifiers());
        Validate.entityThrowingException(validator, compoundActivityDefinition);

        // call through to dao
        return compoundActivityDefDao.updateCompoundActivityDefinition(compoundActivityDefinition);
    }
}
