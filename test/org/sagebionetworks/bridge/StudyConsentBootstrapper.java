package org.sagebionetworks.bridge;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyConsentBootstrapper {
    public StudyConsentBootstrapper(StudyService studyService, StudyConsentDao studyConsentDao) {
        for (Study study : studyService.getStudies()) {
            String path = "";
            int minAge = 17;
            StudyConsent consent = studyConsentDao.getConsent(study.getKey());
            if (consent == null) {
                studyConsentDao.addConsent(study.getKey(), path, minAge);
            }
        }
    }
}
