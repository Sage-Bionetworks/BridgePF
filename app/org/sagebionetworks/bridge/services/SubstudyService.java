package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.SubstudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.SubstudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
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
    
    public Substudy getSubstudy(StudyIdentifier studyId, String id, boolean throwsException) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        Substudy substudy = substudyDao.getSubstudy(studyId, id);
        if (throwsException && substudy == null) {
            throw new EntityNotFoundException(Substudy.class);
        }
        return substudy;
    }
    
    /**
     * Get the list of active substudy IDs for this study (used to validate criteria 
     * objects throughout the system). Calling this method is preferred to getSubstudies() 
     * so we can provide a cache for these infrequently changing identifiers.
     */
    public Set<String> getSubstudyIds(StudyIdentifier studyId) {
        return getSubstudies(studyId, false).stream()
                .map(Substudy::getId).collect(BridgeCollectors.toImmutableSet());
    }
    
    public List<Substudy> getSubstudies(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        return substudyDao.getSubstudies(studyId, includeDeleted);
    }
    
    public VersionHolder createSubstudy(StudyIdentifier studyId, Substudy substudy) {
        checkNotNull(studyId);
        checkNotNull(substudy);
        
        substudy.setStudyId(studyId.getIdentifier());
        Validate.entityThrowingException(SubstudyValidator.INSTANCE, substudy);
        
        substudy.setVersion(null);
        substudy.setDeleted(false);
        DateTime timestamp = DateTime.now();
        substudy.setCreatedOn(timestamp);
        substudy.setModifiedOn(timestamp);
        
        Substudy existing = substudyDao.getSubstudy(studyId, substudy.getId());
        if (existing != null) {
            throw new EntityAlreadyExistsException(Substudy.class,
                    ImmutableMap.of("id", existing.getId()));
        }
        return substudyDao.createSubstudy(substudy);
    }

    public VersionHolder updateSubstudy(StudyIdentifier studyId, Substudy substudy) {
        checkNotNull(studyId);
        checkNotNull(substudy);

        substudy.setStudyId(studyId.getIdentifier());
        Validate.entityThrowingException(SubstudyValidator.INSTANCE, substudy);
        
        Substudy existing = getSubstudy(studyId, substudy.getId(), true);
        if (substudy.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(Substudy.class);
        }
        substudy.setCreatedOn(existing.getCreatedOn());
        substudy.setModifiedOn(DateTime.now());
        
        return substudyDao.updateSubstudy(substudy);
    }
    
    public void deleteSubstudy(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        Substudy existing = getSubstudy(studyId, id, true);
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now());
        substudyDao.updateSubstudy(existing);
    }
    
    public void deleteSubstudyPermanently(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        // Throws exception if the element does not exist.
        getSubstudy(studyId, id, true);
        substudyDao.deleteSubstudyPermanently(studyId, id);
    }
}
