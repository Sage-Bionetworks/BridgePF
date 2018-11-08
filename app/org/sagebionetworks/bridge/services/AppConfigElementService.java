package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.AppConfigElementValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

@Component
public class AppConfigElementService {
    
    private AppConfigElementDao appConfigElementDao;
    
    @Autowired
    final void setAppConfigElementDao(AppConfigElementDao appConfigElementDao) {
        this.appConfigElementDao = appConfigElementDao;
    }
    
    public List<AppConfigElement> getMostRecentElements(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        return appConfigElementDao.getMostRecentElements(studyId, includeDeleted);
    }
    
    public VersionHolder createElement(StudyIdentifier studyId, AppConfigElement element) {
        checkNotNull(studyId);
        checkNotNull(element);
        
        if (element.getRevision() == null) {
            element.setRevision(1L);
        }
        // Validate that ID exists before you try and use it to set the key
        Validate.entityThrowingException(AppConfigElementValidator.INSTANCE, element);
        
        element.setStudyId(studyId.getIdentifier());
        element.setId(element.getId());
        element.setVersion(null);
        element.setDeleted(false);
        element.setCreatedOn(DateTime.now().getMillis());
        element.setModifiedOn(element.getCreatedOn());
        
        AppConfigElement existing = appConfigElementDao.getElementRevision(studyId, element.getId(),
                element.getRevision());
        if (existing != null) {
            throw new EntityAlreadyExistsException(AppConfigElement.class,
                    ImmutableMap.of("id", existing.getId(), "revision", existing.getRevision()));
        }
        return appConfigElementDao.saveElementRevision(element);
    }

    public List<AppConfigElement> getElementRevisions(StudyIdentifier studyId, String id, boolean includeDeleted) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        return appConfigElementDao.getElementRevisions(studyId, id, includeDeleted);
    }
    
    public AppConfigElement getMostRecentElement(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        AppConfigElement element = appConfigElementDao.getMostRecentElement(studyId, id);
        if (element == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        return element;
    }

    public AppConfigElement getElementRevision(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        AppConfigElement element = appConfigElementDao.getElementRevision(studyId, id, revision);
        if (element == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        return element;
    }

    public VersionHolder updateElementRevision(StudyIdentifier studyId, AppConfigElement element) {
        checkNotNull(studyId);
        checkNotNull(element);
        
        Validate.entityThrowingException(AppConfigElementValidator.INSTANCE, element);
        
        AppConfigElement existing = getElementRevision(studyId, element.getId(), element.getRevision());
        if (element.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        element.setStudyId(studyId.getIdentifier());
        element.setId(element.getId());
        element.setModifiedOn(DateTime.now().getMillis());
        // cannot change the creation timestamp
        element.setCreatedOn(existing.getCreatedOn());
        return appConfigElementDao.saveElementRevision(element);
    }
    
    public void deleteElementRevision(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        AppConfigElement existing = getElementRevision(studyId, id, revision);
        existing.setDeleted(true);
        existing.setModifiedOn(DateTime.now().getMillis());
        appConfigElementDao.saveElementRevision(existing);
    }
    
    public void deleteElementAllRevisions(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        List<AppConfigElement> elements = appConfigElementDao.getElementRevisions(studyId, id, false);
        long modifiedOn = DateTime.now().getMillis();
        for (AppConfigElement oneElement : elements) {
            oneElement.setDeleted(true);
            oneElement.setModifiedOn(modifiedOn);
            appConfigElementDao.saveElementRevision(oneElement);
        }
    }
    
    public void deleteElementRevisionPermanently(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        // Throws exception if the element does not exist.
        getElementRevision(studyId, id, revision);
        appConfigElementDao.deleteElementRevisionPermanently(studyId, id, revision);
    }
    
    public void deleteElementAllRevisionsPermanently(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        List<AppConfigElement> elements = appConfigElementDao.getElementRevisions(studyId, id, true);
        for (AppConfigElement oneElement : elements) {
            appConfigElementDao.deleteElementRevisionPermanently(studyId, oneElement.getId(), oneElement.getRevision());
        }
    }
}
