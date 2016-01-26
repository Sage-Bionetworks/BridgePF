package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.StudyValidator;

import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class StudyServiceMockTest {

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
        
        Study persistedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        persistedStudy.setStormpathHref("http://foo");
        when(studyDao.getStudy("test-study")).thenReturn(persistedStudy);
    }
    
    private Study getTestStudy() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setIdentifier("test-study");
        return study;
    }
    
    @Test
    public void verifyChangedStudyTriggersDirectoryUpdate() {
        Study study = getTestStudy();
        
        // here's a bunch of things we can change that won't cause the directory to be updated
        study.setSynapseDataAccessTeamId(23L);
        study.setSynapseProjectId("newid");
        study.setConsentNotificationEmail("newemail@newemail.com");
        study.setMinAgeOfConsent(50);
        study.setMaxNumOfParticipants(100);
        study.setUserProfileAttributes(Sets.newHashSet("a","b"));
        study.setTaskIdentifiers(Sets.newHashSet("c","d"));
        study.setDataGroups(Sets.newHashSet("e","f"));
        study.setStrictUploadValidationEnabled(false);
        study.setHealthCodeExportEnabled(false);
        study.getMinSupportedAppVersions().put("some platform", 22);
        
        service.updateStudy(study, true);
        verify(directoryDao, never()).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingNameUpdatesDirectory() {
        Study study = getTestStudy();
        study.setName("a new name");
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingSponsorNameUpdatesDirectory() {
        Study study = getTestStudy();
        study.setSponsorName("a new name");
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingSupportEmailUpdatesDirectory() {
        Study study = getTestStudy();
        study.setSupportEmail("new@new.com");
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingTechnicalEmailUpdatesDirectory() {
        Study study = getTestStudy();
        study.setTechnicalEmail("new@new.com");
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingPasswordPolicyUpdatesDirectory() {
        Study study = getTestStudy();
        study.setPasswordPolicy(new PasswordPolicy(2, false, false, false, false));
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingVerifyEmailTemplateUpdatesDirectory() {
        Study study = getTestStudy();
        study.setVerifyEmailTemplate(new EmailTemplate("new subject", "new body ${url}", MimeType.HTML));
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
    @Test
    public void changingResetPasswordTemplateUpdatesDirectory() {
        Study study = getTestStudy();
        study.setResetPasswordTemplate(new EmailTemplate("new subject", "new body ${url}", MimeType.HTML));
        
        service.updateStudy(study, true);
        verify(directoryDao).updateDirectoryForStudy(study);
    }
    
}
