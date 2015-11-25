package org.sagebionetworks.bridge;

import javax.annotation.PostConstruct;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("defaultStudyBootstrapper")
public class DefaultStudyBootstrapper {

    private StudyService studyService;

    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @PostConstruct
    public void initializeDatabase() {
        try {
            studyService.getStudy("api");
        } catch (EntityNotFoundException e) {
            Study study = new DynamoStudy();
            study.setName("Test Study");
            study.setIdentifier("api");
            study.setSponsorName("Sage Bionetworks");
            study.setMinAgeOfConsent(18);
            study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
            study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
            study.setSupportEmail("support@sagebridge.org");
            // This is stormpath api (dev) directory.
            study.setStormpathHref("https://enterprise.stormpath.io/v1/directories/7fxheMcEARjm7X2XPBufSM");
            study.getUserProfileAttributes().add("phone");
            study.getUserProfileAttributes().add("can_be_recontacted");
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            studyService.createStudy(study);
        }
    }

}
