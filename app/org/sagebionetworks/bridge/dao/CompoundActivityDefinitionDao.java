package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/** DAO for basic CRUD and list operations for compound activity definitions. */
public interface CompoundActivityDefinitionDao {
    /** Creates a compound activity definition. */
    CompoundActivityDefinition createCompoundActivityDefinition(StudyIdentifier studyId,
            CompoundActivityDefinition compoundActivityDefinition);

    /**
     * Deletes a compound activity definition. This is intended to be used by admin accounts to clean up after tests or
     * other administrative tasks.
     */
    void deleteCompoundActivityDefinition(StudyIdentifier studyId, String taskId);

    /** List all compound activity definitions in a study. */
    List<CompoundActivityDefinition> getAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId);

    /** Get a compound activity definition by ID. */
    CompoundActivityDefinition getCompoundActivityDefinition(StudyIdentifier studyId, String taskId);

    /** Update a compound activity definition. */
    CompoundActivityDefinition updateCompoundActivityDefinition(StudyIdentifier studyId, String taskId,
            CompoundActivityDefinition compoundActivityDefinition);
}
