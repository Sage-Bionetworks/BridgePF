package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceTest {

    @Resource
    StudyService studyService;
    
    @Resource
    StudyConsentService studyConsentService;
    
    @Resource
    DirectoryDao directoryDao;
    
    @Resource
    SubpopulationDao subpopDao;

    @Autowired
    CacheProvider cache;
         
    private CacheProvider mockCache;
    
    private Study study;
    
    @Before
    public void before() {
        mockCache = mock(CacheProvider.class);
        studyService.setCacheProvider(mockCache);
    }
    
    @After
    public void after() {
        if (study != null) {
            studyService.deleteStudy(study.getIdentifier());
        }
    }

    @After
    public void resetCache() {
        studyService.setCacheProvider(cache);
    }

    @Test(expected=InvalidEntityException.class)
    public void studyIsValidated() {
        Study testStudy = new DynamoStudy();
        testStudy.setName("Belgian Waffles [Test]");
        studyService.createStudy(testStudy);
    }
    
    @Test
    public void cannotCreateAnExistingStudyWithAVersion() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study = studyService.createStudy(study);
        try {
            study = studyService.createStudy(study);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void cannotCreateAStudyWithAVersion() {
        Study testStudy = TestUtils.getValidStudy(StudyServiceTest.class);
        testStudy.setVersion(1L);
        testStudy = studyService.createStudy(testStudy);
    }
    
    @Test
    public void crudStudy() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        // verify this can be null, that's okay, and the flags are reset correctly on create
        study.setTaskIdentifiers(null);
        study.setActive(false);
        study.setStrictUploadValidationEnabled(false);
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(false);
        study = studyService.createStudy(study);
        
        assertNotNull("Version has been set", study.getVersion());
        assertTrue(study.isActive());
        assertTrue(study.isStrictUploadValidationEnabled()); // by default set to true
        assertTrue(study.isHealthCodeExportEnabled()); // it was set true in the study

        verify(mockCache).setStudy(study);
        verifyNoMoreInteractions(mockCache);
        reset(mockCache);
        
        // A default, active consent should be created for the study.
        StudyConsentView view = studyConsentService.getActiveConsent(SubpopulationGuid.create(study.getIdentifier()));
        assertTrue(view.getDocumentContent().contains("This is a placeholder for your consent document."));
        assertTrue(view.getActive());
        
        Study newStudy = studyService.getStudy(study.getIdentifier());
        assertTrue(newStudy.isActive());
        assertTrue(newStudy.isStrictUploadValidationEnabled());
        assertTrue(newStudy.isEmailVerificationEnabled());
        assertEquals(study.getIdentifier(), newStudy.getIdentifier());
        assertEquals("Test Study [StudyServiceTest]", newStudy.getName());
        assertEquals(200, newStudy.getMaxNumOfParticipants());
        assertEquals(18, newStudy.getMinAgeOfConsent());
        assertEquals(Sets.newHashSet("beta_users", "production_users"), newStudy.getDataGroups());
        assertEquals(0, newStudy.getTaskIdentifiers().size());
        // these should have been changed
        assertNotEquals("http://local-test-junk", newStudy.getStormpathHref());
        verify(mockCache).getStudy(newStudy.getIdentifier());
        verify(mockCache).setStudy(newStudy);
        verifyNoMoreInteractions(mockCache);
        reset(mockCache);        

        studyService.deleteStudy(study.getIdentifier());
        verify(mockCache).getStudy(study.getIdentifier());
        verify(mockCache).setStudy(study);
        verify(mockCache).removeStudy(study.getIdentifier());        
        try {
            studyService.getStudy(study.getIdentifier());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        // Verify that all the dependent stuff has been deleted as well:
        assertNull(directoryDao.getDirectoryForStudy(study));
        assertEquals(0, subpopDao.getSubpopulations(study.getStudyIdentifier(), false, true).size());
        assertEquals(0, studyConsentService.getAllConsents(SubpopulationGuid.create(study.getIdentifier())).size());
        study = null;
    }
    
    @Test
    public void canUpdatePasswordPolicyAndEmailTemplates() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setPasswordPolicy(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);
        study = studyService.createStudy(study);

        // First, verify that defaults are set...
        PasswordPolicy policy = study.getPasswordPolicy();
        assertNotNull(policy);
        assertEquals(8, policy.getMinLength());
        assertTrue(policy.isNumericRequired());
        assertTrue(policy.isSymbolRequired());
        assertTrue(policy.isUpperCaseRequired());

        EmailTemplate veTemplate = study.getVerifyEmailTemplate();
        assertNotNull(veTemplate);
        assertNotNull(veTemplate.getSubject());
        assertNotNull(veTemplate.getBody());
        
        EmailTemplate rpTemplate = study.getResetPasswordTemplate();
        assertNotNull(rpTemplate);
        assertNotNull(rpTemplate.getSubject());
        assertNotNull(rpTemplate.getBody());
        
        // Now change them and verify they are changed.
        study.setPasswordPolicy(new PasswordPolicy(6, true, false, false, true));
        study.setVerifyEmailTemplate(new EmailTemplate("subject *", "body ${url} *", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject **", "body ${url} **", MimeType.TEXT));
        
        study = studyService.updateStudy(study, true);
        policy = study.getPasswordPolicy();
        assertTrue(study.isEmailVerificationEnabled());
        assertEquals(6, policy.getMinLength());
        assertTrue(policy.isNumericRequired());
        assertFalse(policy.isSymbolRequired());
        assertFalse(policy.isLowerCaseRequired());
        assertTrue(policy.isUpperCaseRequired());
        
        veTemplate = study.getVerifyEmailTemplate();
        assertEquals("subject *", veTemplate.getSubject());
        assertEquals("body ${url} *", veTemplate.getBody());
        assertEquals(MimeType.TEXT, veTemplate.getMimeType());
        
        rpTemplate = study.getResetPasswordTemplate();
        assertEquals("subject **", rpTemplate.getSubject());
        assertEquals("body ${url} **", rpTemplate.getBody());
        assertEquals(MimeType.TEXT, rpTemplate.getMimeType());
    }
    
    @Test
    public void defaultsAreUsedWhenNotProvided() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setPasswordPolicy(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(new EmailTemplate("   ", null, MimeType.TEXT));
        study = studyService.createStudy(study);
        
        assertEquals(PasswordPolicy.DEFAULT_PASSWORD_POLICY, study.getPasswordPolicy());
        assertNotNull(study.getVerifyEmailTemplate());
        assertNotNull(study.getResetPasswordTemplate());
        assertNotNull(study.getResetPasswordTemplate().getSubject());
        assertNotNull(study.getResetPasswordTemplate().getBody());
    }
    
    @Test
    public void problematicHtmlIsRemovedFromTemplates() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setVerifyEmailTemplate(new EmailTemplate("<b>This is not allowed [ve]</b>", "<p>Test [ve] ${url}</p><script></script>", MimeType.HTML));
        study.setResetPasswordTemplate(new EmailTemplate("<b>This is not allowed [rp]</b>", "<p>Test [rp] ${url}</p>", MimeType.TEXT));
        study = studyService.createStudy(study);
        
        EmailTemplate template = study.getVerifyEmailTemplate();
        assertEquals("This is not allowed [ve]", template.getSubject());
        assertEquals("<p>Test [ve] ${url}</p>", template.getBody());
        assertEquals(MimeType.HTML, template.getMimeType());
        
        template = study.getResetPasswordTemplate();
        assertEquals("This is not allowed [rp]", template.getSubject());
        assertEquals("Test [rp] ${url}", template.getBody());
        assertEquals(MimeType.TEXT, template.getMimeType());
    }
    
    @Test
    public void adminsCanChangeSomeValuesResearchersCannot() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setMaxNumOfParticipants(200);
        study.setHealthCodeExportEnabled(false);
        study.setEmailVerificationEnabled(true);
        study = studyService.createStudy(study);
        
        // Okay, now that these are set, researchers cannot change them
        study.setMaxNumOfParticipants(1000);
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(false);
        study = studyService.updateStudy(study, false); // nope
        assertEquals(200, study.getMaxNumOfParticipants());
        assertFalse("isHealthCodeExportEnabled should be false", study.isHealthCodeExportEnabled());
        assertTrue("isEmailVerificationEnabled should be true", study.isEmailVerificationEnabled());
        
        // But administrators can
        study.setMaxNumOfParticipants(1000);
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(false);
        study = studyService.updateStudy(study, true); // yep
        assertEquals(1000, study.getMaxNumOfParticipants());
        assertTrue(study.isHealthCodeExportEnabled());
        assertFalse(study.isEmailVerificationEnabled());
    }
    
    @Test(expected=InvalidEntityException.class)
    public void updateWithNoTemplatesIsInvalid() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study = studyService.createStudy(study);
        
        study.setVerifyEmailTemplate(null);
        studyService.updateStudy(study, false);
    }

    @Test(expected = UnauthorizedException.class)
    public void cantDeleteApiStudy() {
        studyService.deleteStudy("api");
    }
}
