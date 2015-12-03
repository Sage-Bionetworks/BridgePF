package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface SubpopulationDao {

    /**
     * Create subpopulation. 
     * @param subpop
     * @return
     */
    public Subpopulation createSubpopulation(Subpopulation subpop);
    
    /**
     * If no subpoulation exists, a default subpopulation can be created. This will be called
     * as part of creating a study, or when an existing study is found to have no subpopulations.
     * Subpopulations in turn create a default consent document. 
     * @param study
     * @return
     */
    public Subpopulation createDefaultSubpopulation(StudyIdentifier study);
    
    /**
     * Get all subpopulations defined for this study. It is possible to create a first default
     * subpopulation if none exists
     * @param studyId
     * @param createDefault
     *      if true and this study has no subpopulations, create and return a default subpopulation with a 
     *      default consent.
     * @param includeDeleted
     *      if true, return logically deleted subpopulations. If false, do not return them.
     * @return
     */
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean createDefault, boolean includeDeleted);
    
    /**
     * Get a specific subpopulation. This always returns the subpopulation whether it is logically deleted or not. 
     * @return subpopulation
     */
    public Subpopulation getSubpopulation(StudyIdentifier studyId, String guid);
    
    /**
     * Get a subpopulation that matches the most specific criteria defined for a subpopulation. 
     * That is, the populations are sorted by the amount of criteria that are defined to match that 
     * population, and the first one that matches is returned.
     * @param context
     * @return
     */
    public Subpopulation getSubpopulationForUser(ScheduleContext context);
    
    /**
     * Update a subpopulation.
     * @param subpop
     * @return
     */
    public Subpopulation updateSubpopulation(Subpopulation subpop);

    /**
     * Delete a subpopulation. This is a logical delete only, because we cannot be certain that no 
     * one has signed a consent for this subpopulation and need to keep the consent document around. 
     * @param studyId
     * @param guid
     */
    public void deleteSubpopulation(StudyIdentifier studyId, String guid);
    
    /**
     * Delete all subpopulations. This is a physical delete and not a logical delete, and is not exposed 
     * in the API. This is used when deleting a study, as part of a test, for example.
     * @param studyId
     */
    public void deleteAllSubpopulations(StudyIdentifier studyId);
    
}
