package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.config.BridgeTestSpringConfig;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.Sets;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(classes = {BridgeTestSpringConfig.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class DefaultStudyBootstrapperTest {
    @Resource
    private StudyService studyService;
    @Resource
    private DefaultStudyBootstrapper defaultStudyBootstrapper;

    @Before
    public void before() {
        studyService = mock(StudyService.class);
        when(studyService.getStudy("api")).thenThrow(new EntityNotFoundException(
                Study.class,
                "Study 'api' not found."
        ));
    }

    @Test
    public void createsDefaultStudyWhenMissing() {
        defaultStudyBootstrapper.initializeDatabase();

        ArgumentCaptor<Study> argument = ArgumentCaptor.forClass(Study.class);
        verify(studyService).createStudy(argument.capture());

        Study study = argument.getValue();
        assertEquals("Test Study", study.getName());
        assertEquals("api", study.getIdentifier());
        assertEquals("Sage Bionetworks", study.getSponsorName());
        assertEquals(18, study.getMinAgeOfConsent());
        assertEquals("bridge-testing+consent@sagebase.org", study.getConsentNotificationEmail());
        assertEquals("bridge-testing+technical@sagebase.org", study.getTechnicalEmail());
        assertEquals("support@sagebridge.org", study.getSupportEmail());
        assertEquals("https://enterprise.stormpath.io/v1/directories/3OBNJsxNxvaaK5nSFwv8RD", study.getStormpathHref());
        assertEquals(Sets.newHashSet("phone", "can_be_recontacted"), study.getUserProfileAttributes());
        assertEquals(new PasswordPolicy(2, false, false, false, false), study.getPasswordPolicy());
    }

}
