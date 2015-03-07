package org.sagebionetworks.bridge;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StudyConsentBootstrapper {
    
    @Autowired
    public StudyConsentBootstrapper(StudyService studyService, StudyConsentDao studyConsentDao) {
        try {
            studyService.getStudy("api");
        } catch(EntityNotFoundException e) {
            Study study = new DynamoStudy();
            study.setName("Test Study");
            study.setIdentifier("api");
            study.setHostname("api-local.sagebridge.org");
            study.setMinAgeOfConsent(18);
            study.setResearcherRole("api_researcher");
            study.setConsentNotificationEmail("bridge-testing+consent@sagebridge.org");
            study.setStormpathHref("https://enterprise.stormpath.io/v1/directories/7fxheMcEARjm7X2XPBufSM");
            studyService.createStudy(study);
        }
        for (Study study : studyService.getStudies()) {
            StudyIdentifier studyIdentifier = study.getStudyIdentifier();
            String path = String.format("conf/email-templates/%s-consent.html", study.getIdentifier());
            int minAge = 18;
            StudyConsent consent = studyConsentDao.getConsent(studyIdentifier);
            if (consent == null) {
                consent = studyConsentDao.addConsent(studyIdentifier, path, minAge);
                studyConsentDao.setActive(consent, true);
            }
        }
    }

}
