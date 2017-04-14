package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyService;

@SuppressWarnings("unchecked")
public class DefaultStudyBootstrapperTest {

    private StudyService studyService;

    private DefaultStudyBootstrapper defaultStudyBootstrapper;

    @Before
    public void before() {
        studyService = mock(StudyService.class);

        when(studyService.getStudy(any(StudyIdentifier.class))).thenThrow(EntityNotFoundException.class);
        defaultStudyBootstrapper = new DefaultStudyBootstrapper(studyService,
                mock(AnnotationBasedTableCreator.class),
                mock(DynamoInitializer.class)
        );
    }

    @Test
    public void createsDefaultStudyWhenMissing() {
        defaultStudyBootstrapper.initializeDatabase();

        ArgumentCaptor<Study> argument = ArgumentCaptor.forClass(Study.class);
        verify(studyService, times(2)).createStudy(argument.capture());

        List<Study> createdStudyList = argument.getAllValues();

        // Validate api study.
        Study study = createdStudyList.get(0);
        assertEquals("Test Study", study.getName());
        assertEquals(BridgeConstants.API_STUDY_ID_STRING, study.getIdentifier());
        assertEquals("Sage Bionetworks", study.getSponsorName());
        assertEquals(18, study.getMinAgeOfConsent());
        assertEquals("bridge-testing+consent@sagebase.org", study.getConsentNotificationEmail());
        assertEquals("bridge-testing+technical@sagebase.org", study.getTechnicalEmail());
        assertEquals("support@sagebridge.org", study.getSupportEmail());
        assertEquals("https://enterprise.stormpath.io/v1/directories/3OBNJsxNxvaaK5nSFwv8RD", study.getStormpathHref());
        assertEquals(Sets.newHashSet("phone", "can_be_recontacted"), study.getUserProfileAttributes());
        assertEquals(new PasswordPolicy(2, false, false, false, false), study.getPasswordPolicy());

        // Validate shared study. No need to test every attribute. Just validate the important attributes.
        Study sharedStudy = createdStudyList.get(1);
        assertEquals("Shared Module Library", sharedStudy.getName());
        assertEquals(BridgeConstants.SHARED_STUDY_ID_STRING, sharedStudy.getIdentifier());
        assertEquals("https://enterprise.stormpath.io/v1/directories/4VaJ0y63TCKDmJTIV8YGAs",
                sharedStudy.getStormpathHref());
    }
}
