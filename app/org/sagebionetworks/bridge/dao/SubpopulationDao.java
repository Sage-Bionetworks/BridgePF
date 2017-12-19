package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public interface SubpopulationDao {

    /**
     * Create subpopulation. 
     */
    Subpopulation createSubpopulation(Subpopulation subpop);
    
    /**
     * If no subpoulation exists, a default subpopulation can be created. This will be called
     * as part of creating a study, or when an existing study is found to have no subpopulations.
     * Subpopulations in turn create a default consent document. 
     */
    Subpopulation createDefaultSubpopulation(StudyIdentifier study);
    
    /**
     * Get all subpopulations defined for this study. It is possible to create a first default
     * subpopulation if none exists
     *
     * @param createDefault
     *      if true and this study has no subpopulations, create and return a default subpopulation with a 
     *      default consent.
     * @param includeDeleted
     *      if true, return logically deleted subpopulations. If false, do not return them.
     */
    List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean createDefault, boolean includeDeleted);
    
    /**
     * Get a specific subpopulation. This always returns the subpopulation whether it is logically deleted or not. 
     * @return subpopulation
     */
    Subpopulation getSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid);
    
    /**
     * Update a subpopulation.
     */
    Subpopulation updateSubpopulation(Subpopulation subpop);

    /**
     * Delete a subpopulation. This is a logical delete only, because we cannot be certain that no 
     * one has signed a consent for this subpopulation and need to keep the consent document around.
     *
     * @param physicalDelete physically delete this subpopulation from the database. This is only done via an
     *      admin-api for the purposes of cleanup after integration tests.
     * @param allowDeleteOfDefault allow the service to delete the default subpopulation, as part of deleting
     *      a study. 
     */
    void deleteSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid, boolean physicalDelete,
            boolean allowDeleteOfDefault);
    
}
