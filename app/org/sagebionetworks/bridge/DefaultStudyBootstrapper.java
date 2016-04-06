package org.sagebionetworks.bridge;

import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.Sets;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
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
    public static final Set<String> TEST_DATA_GROUPS = Sets.newHashSet("sdk-int-1", "sdk-int-2", "group1");

    /**
     * The task identifiers set in the test (api) study. This includes task identifiers that are required for the SDK
     * integration tests.
     */
    public static final Set<String> TEST_TASK_IDENTIFIERS =
            Sets.newHashSet("task:AAA", "task:BBB", "task:CCC", "CCC", "task1");

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
            study.setUserProfileAttributes(Sets.newHashSet("phone", "can_be_recontacted"));
            study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
            studyService.createStudy(study);
        }
    }

}
