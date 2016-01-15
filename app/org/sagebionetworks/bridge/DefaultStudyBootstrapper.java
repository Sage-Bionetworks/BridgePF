package org.sagebionetworks.bridge;

import javax.annotation.PostConstruct;

import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.Sets;

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
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
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
            study.setStormpathHref("https://enterprise.stormpath.io/v1/directories/3OBNJsxNxvaaK5nSFwv8RD");
            study.setDataGroups(TestConstants.TEST_DATA_GROUPS);
            study.setTaskIdentifiers(TestConstants.TEST_TASK_IDENTIFIERS);
            study.setUserProfileAttributes(Sets.newHashSet("phone","can_be_recontacted"));
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            studyService.createStudy(study);
        }
    }

}
