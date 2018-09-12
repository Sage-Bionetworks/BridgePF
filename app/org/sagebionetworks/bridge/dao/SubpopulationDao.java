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
     * Logically delete a subpopulation. You cannot logically delete the default subpopulation for a study. 
     */
    void deleteSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid);
    
    /**
     * Delete a subpopulation permanently. 
     */
    void deleteSubpopulationPermanently(StudyIdentifier studyId, SubpopulationGuid subpopGuid);
    
}
