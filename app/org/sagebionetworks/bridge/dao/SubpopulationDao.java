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
     * Get all subpopulations defined for this study. It is possible to create a first default
     * subpopulation if none exists
     * @param studyId
     * @param createDefault
     * @return
     */
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean createDefault);
    
    /**
     * Get a specific subpopulation.
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
     * Delete a subpopulation.
     * @param studyId
     * @param guid
     */
    public void deleteSubpopulation(StudyIdentifier studyId, String guid);
    
}
