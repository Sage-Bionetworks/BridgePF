package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.AppConfigElementDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.EntityPublishedException;
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
    
    DateTime getDateTime() {
        return DateTime.now();
    }

    public List<AppConfigElement> getMostRecentElements(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        return appConfigElementDao.getMostRecentElements(studyId, includeDeleted);
    }
    
    public VersionHolder createElement(StudyIdentifier studyId, AppConfigElement element) {
        checkNotNull(studyId);
        checkNotNull(element);
        
        Validate.entityThrowingException(AppConfigElementValidator.CREATE_VALIDATOR, element);
        
        AppConfigElement existing = getElementRevision(studyId, element.getId(), 1L);
        if (existing != null) {
            throw new EntityAlreadyExistsException(AppConfigElement.class,
                    ImmutableMap.of("id", element.getId(), "revision", element.getRevision()));
        }
        element.setKey(studyId.getIdentifier() + ":" + element.getId());
        element.setStudyId(studyId.getIdentifier());
        element.setRevision(1L);
        element.setVersion(null);
        element.setDeleted(false);
        element.setCreatedOn(getDateTime().getMillis());
        element.setModifiedOn(element.getCreatedOn());
        
        return appConfigElementDao.saveElementRevision(element);
    }

    public List<AppConfigElement> getElementRevisions(StudyIdentifier studyId, String id, boolean includeDeleted) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        return appConfigElementDao.getElementRevisions(studyId, id, includeDeleted);
    }
    
    public VersionHolder createElementRevision(StudyIdentifier studyId, AppConfigElement element) {
        checkNotNull(studyId);
        checkNotNull(element);
        
        Validate.entityThrowingException(AppConfigElementValidator.CREATE_VALIDATOR, element);
        
        AppConfigElement existing = getElementRevision(studyId, element.getId(), element.getRevision());
        if (existing != null) {
            throw new EntityAlreadyExistsException(AppConfigElement.class,
                    ImmutableMap.of("id", element.getId(), "revision", element.getRevision()));
        }
        // We do not set the revision, it should have been supplied by the user
        element.setKey(studyId.getIdentifier() + ":" + element.getId());
        element.setStudyId(studyId.getIdentifier());
        element.setDeleted(false);
        element.setCreatedOn(getDateTime().getMillis());
        element.setModifiedOn(element.getCreatedOn());

        return appConfigElementDao.saveElementRevision(element);
    }

    public AppConfigElement getMostRecentlyPublishedElement(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        return appConfigElementDao.getMostRecentlyPublishedElement(studyId, id);
    }

    public AppConfigElement getElementRevision(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        return appConfigElementDao.getElementRevision(studyId, id, revision);
    }

    public VersionHolder updateElementRevision(StudyIdentifier studyId, AppConfigElement element) {
        checkNotNull(studyId);
        checkNotNull(element);
        
        Validate.entityThrowingException(AppConfigElementValidator.UPDATE_VALIDATOR, element);
        
        AppConfigElement existing = getElementRevision(studyId, element.getId(), element.getRevision());
        if (existing == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        if (element.isDeleted() && existing.isDeleted()) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        element.setKey(studyId.getIdentifier() + ":" + element.getId());
        element.setStudyId(studyId.getIdentifier());
        element.setModifiedOn(getDateTime().getMillis());
        return appConfigElementDao.saveElementRevision(element);
    }
    
    public VersionHolder publishElementRevision(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        AppConfigElement existing = getElementRevision(studyId, id, revision);
        if (existing == null || existing.isDeleted()) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        if (existing.isPublished()) {
            throw new EntityPublishedException("App config element is already published.");
        }
        existing.setModifiedOn(getDateTime().getMillis());
        existing.setPublished(true);
        return appConfigElementDao.saveElementRevision(existing);
    }
    
    public void deleteElementRevision(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        AppConfigElement existing = getElementRevision(studyId, id, revision);
        if (existing == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
        existing.setDeleted(true);
        existing.setModifiedOn(getDateTime().getMillis());
        appConfigElementDao.saveElementRevision(existing);
    }
    
    public void deleteElementAllRevisions(StudyIdentifier studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        List<AppConfigElement> elements = appConfigElementDao.getElementRevisions(studyId, id, false);
        long modifiedOn = getDateTime().getMillis();
        for (AppConfigElement oneElement : elements) {
            oneElement.setDeleted(true);
            oneElement.setModifiedOn(modifiedOn);
            appConfigElementDao.saveElementRevision(oneElement);
        }
    }
    
    public void deleteElementRevisionPermanently(StudyIdentifier studyId, String id, long revision) {
        checkNotNull(studyId);
        checkNotNull(id);
        
        AppConfigElement existing = getElementRevision(studyId, id, revision);
        if (existing == null) {
            throw new EntityNotFoundException(AppConfigElement.class);
        }
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
