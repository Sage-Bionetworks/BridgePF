package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.SubstudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class SubstudyService {
    
    private SubstudyDao substudyDao;
    
    @Autowired
    final void setSubstudyDao(SubstudyDao substudyDao) {
        this.substudyDao = substudyDao;
    }
    
    public Substudy getSubstudy(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        Substudy substudy = substudyDao.getSubstudy(studyId, id);
        if (substudy == null) {
            throw new EntityNotFoundException(Substudy.class);
        }
        return substudy;
    }
    
    public List<Substudy> getSubstudies(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        return substudyDao.getSubstudies(studyId, includeDeleted);
    }
    
    public VersionHolder createSubstudy(StudyIdentifier studyId, Substudy substudy) {
        checkNotNull(studyId);
        checkNotNull(substudy);
        
        //Validate.entityThrowingException(AppConfigElementValidator.INSTANCE, substudy);
        
        substudy.setStudyId(studyId.getIdentifier());
        substudy.setVersion(null);
        substudy.setDeleted(false);
        substudy.setCreatedOn(DateTime.now());
        substudy.setModifiedOn(substudy.getCreatedOn());
        
        Substudy existing = substudyDao.getSubstudy(studyId, substudy.getId());
        if (existing != null) {
            throw new EntityAlreadyExistsException(AppConfigElement.class,
                    ImmutableMap.of("id", existing.getId()));
        }
        return substudyDao.createSubstudy(substudy);
    }

    public List<Substudy> getSubstudies(StudyIdentifier studyId, String id, boolean includeDeleted) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        return substudyDao.getSubstudies(studyId, includeDeleted);
    }
    
    public VersionHolder updateSubstudy(StudyIdentifier studyId, Substudy substudy) {
        checkNotNull(studyId);
        checkNotNull(substudy);
        
        //Validate.entityThrowingException(AppConfigElementValidator.INSTANCE, element);
        
        Substudy existing = getSubstudy(studyId, substudy.getId());
        if (substudy.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        substudy.setStudyId(studyId.getIdentifier());
        substudy.setId(substudy.getId());
        substudy.setModifiedOn(DateTime.now());
        // cannot change the creation timestamp
        substudy.setCreatedOn(existing.getCreatedOn());
        return substudyDao.updateSubstudy(substudy);
    }
    
    public void deleteSubstudy(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        Substudy existing = getSubstudy(studyId, id);
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now());
        substudyDao.updateSubstudy(existing);
    }
    
    public void deleteSubstudyPermanently(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        // Throws exception if the element does not exist.
        getSubstudy(studyId, id);
        substudyDao.deleteSubstudyPermanently(studyId, id);
    }
    
}
