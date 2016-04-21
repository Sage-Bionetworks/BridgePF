package org.sagebionetworks.bridge;

import java.util.Set;

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
    
    /**
     * The data group set in the test (api) study. This includes groups that are required for the SDK integration tests.
     */
    public static final Set<String> TEST_DATA_GROUPS = Sets.newHashSet("sdk-int-1","sdk-int-2", "group1");
    
    /**
     * The task identifiers set in the test (api) study. This includes task identifiers that are required for the SDK integration tests.
     */
    public static final Set<String> TEST_TASK_IDENTIFIERS = Sets.newHashSet("task:AAA", "task:BBB", "task:CCC", "CCC", "task1");

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
            study.setDataGroups(TEST_DATA_GROUPS);
            study.setTaskIdentifiers(TEST_TASK_IDENTIFIERS);
            study.setUserProfileAttributes(Sets.newHashSet("phone","can_be_recontacted"));
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            studyService.createStudy(study);
        }
    }

}
