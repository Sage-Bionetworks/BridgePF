package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.validators.SubpopulationValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class SubpopulationService {

    private SubpopulationDao subpopDao;
    
    @Autowired
    final void setSubpopulationDao(SubpopulationDao subpopDao) {
        this.subpopDao = subpopDao;
    }
    
    /**
     * Create subpopulation. 
     * @param subpop
     * @return
     */
    public Subpopulation createSubpopulation(Study study, Subpopulation subpop) {
        checkNotNull(study);
        checkNotNull(subpop);
        
        subpop.setGuid(BridgeUtils.generateGuid());
        subpop.setStudyIdentifier(study.getIdentifier());
        Validator validator = new SubpopulationValidator(study.getDataGroups());
        Validate.entityThrowingException(validator, subpop);
        
        return subpopDao.createSubpopulation(subpop);
    }
    
    /**
     * Update a subpopulation.
     * @param subpop
     * @return
     */
    public Subpopulation updateSubpopulation(Study study, Subpopulation subpop) {
        checkNotNull(study);
        checkNotNull(subpop);
        
        subpop.setStudyIdentifier(study.getIdentifier());
        Validator validator = new SubpopulationValidator(study.getDataGroups());
        Validate.entityThrowingException(validator, subpop);
        
        return subpopDao.updateSubpopulation(subpop);
    }
    
    /**
     * Get all subpopulations defined for this study that have not been deleted. If 
     * there are no subpopulations, a default subpopulation will be created with a 
     * default consent.
     * @param studyId
     * @return
     */
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        return subpopDao.getSubpopulations(studyId, true, false);
    }
    
    /**
     * Get a specific subpopulation.
     * @return subpopulation
     */
    public Subpopulation getSubpopulation(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return subpopDao.getSubpopulation(studyId, guid);
    }
    
    /**
     * Get a subpopulation that matches the most specific criteria defined for a subpopulation. 
     * That is, the populations are sorted by the amount of criteria that are defined to match that 
     * population, and the first one that matches is returned.
     * @param context
     * @return
     */
    public Subpopulation getSubpopulationForUser(ScheduleContext context) {
        checkNotNull(context);
        
        return subpopDao.getSubpopulationForUser(context);
    }

    /**
     * Delete a subpopulation.
     * @param studyId
     * @param guid
     */
    public void deleteSubpopulation(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        subpopDao.deleteSubpopulation(studyId, guid);
    }

}
