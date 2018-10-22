package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface AppConfigElementDao {
    
    List<AppConfigElement> getMostRecentElements(StudyIdentifier studyId, boolean includeDeleted);
    
    AppConfigElement getMostRecentElement(StudyIdentifier studyId, String id);

    List<AppConfigElement> getElementRevisions(StudyIdentifier studyId, String id, boolean includeDeleted);
    
    AppConfigElement getElementRevision(StudyIdentifier studyId, String id, long revision);
    
    VersionHolder saveElementRevision(AppConfigElement element);
    
    void deleteElementRevisionPermanently(StudyIdentifier studyId, String id, long revision);

}
