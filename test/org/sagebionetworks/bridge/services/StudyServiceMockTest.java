package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.StudyValidator;

import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class StudyServiceMockTest {

    private static final PasswordPolicy PASSWORD_POLICY = new PasswordPolicy(2, false, false, false, false);
    private static final EmailTemplate EMAIL_TEMPLATE = new EmailTemplate("new subject", "new body ${url}",
            MimeType.HTML);

    @Mock
    private UploadCertificateService uploadCertService;
    @Mock
    private StudyDao studyDao;
    @Mock
    private DirectoryDao directoryDao;
    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private EmailVerificationService emailVerificationService;

    private StudyService service;

    @Before
    public void before() {
        service = new StudyService();
        service.setUploadCertificateService(uploadCertService);
        service.setStudyDao(studyDao);
        service.setDirectoryDao(directoryDao);
        service.setValidator(new StudyValidator());
        service.setCacheProvider(cacheProvider);
        service.setSubpopulationService(subpopService);
        service.setEmailVerificationService(emailVerificationService);

        when(studyDao.getStudy("test-study")).thenReturn(getTestStudy());
    }

    private Study getTestStudy() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setIdentifier("test-study");
        study.setStormpathHref("http://foo");
        return study;
    }

    private void assertDirectoryUpdated(Consumer<Study> consumer) {
        Study study = getTestStudy();
        consumer.accept(study);
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }

    @Test
    public void changingIrrelevantFieldsDoesNotUpdateDirectory() {
        Study study = getTestStudy();

        // here's a bunch of things we can change that won't cause the directory to be updated
        study.setSynapseDataAccessTeamId(23L);
        study.setSynapseProjectId("newid");
        study.setConsentNotificationEmail("newemail@newemail.com");
        study.setMinAgeOfConsent(50);
        study.setMaxNumOfParticipants(100);
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        study.setTaskIdentifiers(Sets.newHashSet("c", "d"));
        study.setDataGroups(Sets.newHashSet("e", "f"));
        study.setStrictUploadValidationEnabled(false);
        study.setHealthCodeExportEnabled(false);
        study.getMinSupportedAppVersions().put("some platform", 22);

        service.updateStudy(study, true);
        verify(directoryDao, never()).updateDirectoryForStudy(study);
    }

    @Test
    public void changingNameUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setName("name"));
    }

    @Test
    public void changingSponsorNameUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setSponsorName("a new name"));
    }

    @Test
    public void changingSupportEmailUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setSupportEmail("new@new.com"));
    }

    @Test
    public void changingTechnicalEmailUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setTechnicalEmail("new@new.com"));
    }

    @Test
    public void changingPasswordPolicyUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setPasswordPolicy(PASSWORD_POLICY));
    }

    @Test
    public void changingVerifyEmailTemplateUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setVerifyEmailTemplate(EMAIL_TEMPLATE));
    }

    @Test
    public void changingResetPasswordTemplateUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setResetPasswordTemplate(EMAIL_TEMPLATE));
    }

    @Test
    public void newStudyVerifiesSupportEmail() {
        Study study = getTestStudy();
        when(emailVerificationService.verifyEmailAddress(study.getSupportEmail()))
                .thenReturn(EmailVerificationStatus.PENDING);
        when(studyDao.createStudy(study)).thenReturn(study);

        service.createStudy(study);

        verify(emailVerificationService).verifyEmailAddress(study.getSupportEmail());
    }

    @Test
    public void updatingStudyVerifiesSupportEmail() throws Exception {
        Study study = getTestStudy();
        when(emailVerificationService.verifyEmailAddress(study.getSupportEmail()))
                .thenReturn(EmailVerificationStatus.VERIFIED);
        when(studyDao.getStudy(study.getIdentifier())).thenReturn(study);

        // We need to copy study in order to set support email and have it be different than
        // the mock version returned from the database
        Study newStudy = BridgeObjectMapper.get().readValue(
                BridgeObjectMapper.get().writeValueAsString(study), Study.class);
        newStudy.setSupportEmail("foo@foo.com"); // it's new and must be verified.
        
        service.updateStudy(newStudy, false);
        verify(emailVerificationService).verifyEmailAddress("foo@foo.com");
    }

    @Test
    public void updatingStudyNoChangeInSupportEmailDoesNotVerifyEmail() {
        Study study = getTestStudy();
        when(emailVerificationService.verifyEmailAddress(study.getSupportEmail()))
                .thenReturn(EmailVerificationStatus.VERIFIED);
        when(studyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        service.updateStudy(study, false);
        verify(emailVerificationService, never()).verifyEmailAddress(any());
    }
    
}
