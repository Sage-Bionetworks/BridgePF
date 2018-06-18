package org.sagebionetworks.bridge;

import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.Sets;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("defaultStudyBootstrapper")
public class DefaultStudyBootstrapper {

    /**
     * The data group set in the test (api) study. This includes groups that are required for the SDK integration tests.
     */
    public static final Set<String> TEST_DATA_GROUPS = Sets.newHashSet("sdk-int-1", "sdk-int-2", "group1", BridgeConstants.TEST_USER_GROUP);

    /**
     * The task identifiers set in the test (api) study. This includes task identifiers that are required for the SDK
     * integration tests.
     */
    public static final Set<String> TEST_TASK_IDENTIFIERS = Sets.newHashSet("task:AAA", "task:BBB", "task:CCC", "CCC", "task1");

    private final StudyService studyService;
    private final DynamoInitializer dynamoInitializer;
    private final AnnotationBasedTableCreator annotationBasedTableCreator;


    @Autowired
    public DefaultStudyBootstrapper(StudyService studyService,
                                    AnnotationBasedTableCreator annotationBasedTableCreator,
                                    DynamoInitializer dynamoInitializer) {
        this.studyService = studyService;
        this.dynamoInitializer = dynamoInitializer;
        this.annotationBasedTableCreator = annotationBasedTableCreator;
    }

    @PostConstruct
    public void initializeDatabase() {
        List<TableDescription> tables = annotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb");
        dynamoInitializer.init(tables);

        // Create the "api" study if it doesn't exist. This is used for local testing and integ tests.
        try {
            studyService.getStudy(BridgeConstants.API_STUDY_ID);
        } catch (EntityNotFoundException e) {
            Study study = Study.create();
            study.setName("Test Study");
            study.setShortName("TestStudy");
            study.setIdentifier(BridgeConstants.API_STUDY_ID_STRING);
            study.setSponsorName("Sage Bionetworks");
            study.setMinAgeOfConsent(18);
            study.setConsentNotificationEmail("bridge-testing+consent@sagebase.org");
            study.setTechnicalEmail("bridge-testing+technical@sagebase.org");
            study.setSupportEmail("support@sagebridge.org");
            study.setDataGroups(TEST_DATA_GROUPS);
            study.setTaskIdentifiers(TEST_TASK_IDENTIFIERS);
            study.setUserProfileAttributes(Sets.newHashSet("can_be_recontacted"));
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            study.setEmailVerificationEnabled(true);
            study.setVerifyChannelOnSignInEnabled(true);
            studyService.createStudy(study);
        }

        // Create the "shared" study if it doesn't exist. This is used for the Shared Module Library.
        try {
            studyService.getStudy(BridgeConstants.SHARED_STUDY_ID);
        } catch (EntityNotFoundException e) {
            Study study = Study.create();
            study.setName("Shared Module Library");
            study.setShortName("SharedLib");
            study.setSponsorName("Sage Bionetworks");
            study.setIdentifier(BridgeConstants.SHARED_STUDY_ID_STRING);
            study.setSupportEmail("bridgeit@sagebridge.org");
            study.setTechnicalEmail("bridgeit@sagebridge.org");
            study.setConsentNotificationEmail("bridgeit@sagebridge.org");
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            study.setEmailVerificationEnabled(true);
            study.setVerifyChannelOnSignInEnabled(true);
            studyService.createStudy(study);
        }
    }
}
