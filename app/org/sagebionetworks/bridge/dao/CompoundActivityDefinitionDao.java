package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/** DAO for basic CRUD and list operations for compound activity definitions. */
public interface CompoundActivityDefinitionDao {
    /** Creates a compound activity definition. */
    CompoundActivityDefinition createCompoundActivityDefinition(CompoundActivityDefinition compoundActivityDefinition);

    /** Deletes a compound activity definition. */
    void deleteCompoundActivityDefinition(StudyIdentifier studyId, String taskId);

    /** Deletes all compound activity definitions in the specified study. Used when we physically delete a study. */
    void deleteAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId);

    /** List all compound activity definitions in a study. */
    List<CompoundActivityDefinition> getAllCompoundActivityDefinitionsInStudy(StudyIdentifier studyId);

    /** Get a compound activity definition by ID. */
    CompoundActivityDefinition getCompoundActivityDefinition(StudyIdentifier studyId, String taskId);

    /** Update a compound activity definition. */
    CompoundActivityDefinition updateCompoundActivityDefinition(CompoundActivityDefinition compoundActivityDefinition);
}
