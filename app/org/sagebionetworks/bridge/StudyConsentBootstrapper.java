package org.sagebionetworks.bridge;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyConsentBootstrapper {
    public StudyConsentBootstrapper(StudyService studyService, StudyConsentDao studyConsentDao) {
        try {
            studyService.getStudy("api");    
        } catch(EntityNotFoundException e) {
            Study study = new DynamoStudy();
            study.setName("Test Study");
            study.setIdentifier("api");
            study.getTrackers().add("pb-tracker");
            study.getTrackers().add("med-tracker");
            study.setHostname("api-local.sagebridge.org");
            study.setMinAgeOfConsent(18);
            study.setResearcherRole("api_researcher");
            study.setConsentNotificationEmail("bridge-testing+consent@sagebridge.org");
            study.setStormpathHref("https://api.stormpath.com/v1/directories/shHutmsq4TcjyJQ5ayMoQ");
            studyService.createStudy(study);
        }
        for (Study study : studyService.getStudies()) {
            String path = String.format("conf/email-templates/%s-consent.html", study.getIdentifier());
            int minAge = 17;
            StudyConsent consent = studyConsentDao.getConsent(study.getIdentifier());
            if (consent == null) {
                consent = studyConsentDao.addConsent(study.getIdentifier(), path, minAge);
                studyConsentDao.setActive(consent, true);
            }
        }
    }

}
