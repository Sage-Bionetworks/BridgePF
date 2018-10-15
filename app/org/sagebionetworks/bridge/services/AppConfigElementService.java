package org.sagebionetworks.bridge.services;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.stereotype.Component;

@Component
public class AppConfigElementService {

    public List<AppConfigElement> getMostRecentElements(StudyIdentifier studyId, boolean includeDeleted) {
        return null;
    }
    
    public VersionHolder createElement(StudyIdentifier studyId, AppConfigElement element) {
        return null;
    }

    public List<AppConfigElement> getElementRevisions(StudyIdentifier studyId, String id, boolean includeDeleted) {
        return null;
    }
    
    public VersionHolder createElementRevision(StudyIdentifier studyId, AppConfigElement element) {
        return null;
    }

    public AppConfigElement getMostRecentlyPublishedElement(StudyIdentifier studyId, String id) {
        return null;
    }

    public AppConfigElement getElementRevision(StudyIdentifier studyId, String id, long revision) {
        return null;
    }

    public VersionHolder updateElementRevision(StudyIdentifier studyId, AppConfigElement element) {
        return null;
    }
    
    public VersionHolder publishElementRevision(StudyIdentifier studyId, String id, long revision) {
        return null;
    }
    
    public void deleteElementAllRevisions(StudyIdentifier studyId, String id) {
    }
    
    public void deleteElementAllRevisionsPermanently(StudyIdentifier studyId, String id) {
    }
    
    public void deleteElementRevision(StudyIdentifier studyId, String id, long revision) {
    }
    
    public void deleteElementRevisionPermanently(StudyIdentifier studyId, String id, long revision) {
    }
}
