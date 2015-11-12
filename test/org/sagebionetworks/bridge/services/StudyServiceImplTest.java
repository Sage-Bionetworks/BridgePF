package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceImplTest {

    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    StudyConsentServiceImpl studyConsentService;
    
    private CacheProvider cache;
    
    private Study study;
    
    @Before
    public void before() {
        cache = mock(CacheProvider.class);
        studyService.setCacheProvider(cache);
    }
    
    @After
    public void after() {
        if (study != null) {
            studyService.deleteStudy(study.getIdentifier());
        }
    }
    
    @Test(expected=InvalidEntityException.class)
    public void studyIsValidated() {
        Study testStudy = new DynamoStudy();
        testStudy.setName("Belgian Waffles [Test]");
        studyService.createStudy(testStudy);
    }
    
    @Test
    public void cannotCreateAnExistingStudyWithAVersion() {
        study = TestUtils.getValidStudy(StudyServiceImplTest.class);
        study = studyService.createStudy(study);
        try {
            study = studyService.createStudy(study);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void cannotCreateAStudyWithAVersion() {
        Study testStudy = TestUtils.getValidStudy(StudyServiceImplTest.class);
        testStudy.setVersion(1L);
        testStudy = studyService.createStudy(testStudy);
    }
    
    @Test
    public void crudStudy() {
        study = TestUtils.getValidStudy(StudyServiceImplTest.class);
        // verify this can be null, that's okay, and the flags are reset correctly on create
        study.setTaskIdentifiers(null);
        study.setActive(false);
        study.setHealthCodeExportEnabled(true);
        study = studyService.createStudy(study);
        
        assertNotNull("Version has been set", study.getVersion());
        assertTrue(study.isActive()); // study is active
        assertTrue(study.isHealthCodeExportEnabled()); // health code will not be exported
        
        verify(cache).setStudy(study);
        verifyNoMoreInteractions(cache);
        reset(cache);
        
        // A default, active consent should be created for the study.
        StudyConsentView view = studyConsentService.getActiveConsent(study.getStudyIdentifier());
        assertTrue(view.getDocumentContent().contains("This is a placeholder for your consent document."));
        assertTrue(view.getActive());
        
        Study newStudy = studyService.getStudy(study.getIdentifier());
        assertTrue(newStudy.isActive());
        assertEquals(study.getIdentifier(), newStudy.getIdentifier());
        assertEquals("Test Study [StudyServiceImplTest]", newStudy.getName());
        assertEquals(200, newStudy.getMaxNumOfParticipants());
        assertEquals(18, newStudy.getMinAgeOfConsent());
        assertEquals(Sets.newHashSet("beta_users", "production_users"), newStudy.getDataGroups());
        assertEquals(0, newStudy.getTaskIdentifiers().size());
        // these should have been changed
        assertNotEquals("http://local-test-junk", newStudy.getStormpathHref());
        verify(cache).getStudy(newStudy.getIdentifier());
        verify(cache).setStudy(newStudy);
        verifyNoMoreInteractions(cache);
        reset(cache);

        studyService.deleteStudy(study.getIdentifier());
        verify(cache).getStudy(study.getIdentifier());
        verify(cache).setStudy(study);
        verify(cache).removeStudy(study.getIdentifier());
        try {
            studyService.getStudy(study.getIdentifier());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        study = null;
    }
    
    @Test
    public void canUpdatePasswordPolicyAndEmailTemplates() {
        study = TestUtils.getValidStudy(StudyServiceImplTest.class);
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
        study = TestUtils.getValidStudy(StudyServiceImplTest.class);
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
        study = TestUtils.getValidStudy(StudyServiceImplTest.class);
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
    public void adminsCanChangeMaxNumParticipantsResearchersCannot() {
        study = TestUtils.getValidStudy(StudyServiceImplTest.class); // it's 200.
        study = studyService.createStudy(study);
        
        // Okay, not that it's set, researchers can change it to no effect
        study.setMaxNumOfParticipants(1000);
        study = studyService.updateStudy(study, false);
        assertEquals(200, study.getMaxNumOfParticipants()); // nope
        
        study.setMaxNumOfParticipants(1000);
        study = studyService.updateStudy(study, true);
        assertEquals(1000, study.getMaxNumOfParticipants()); // yep
    }
    
    @Test(expected=InvalidEntityException.class)
    public void updateWithNoTemplatesIsInvalid() {
        study = TestUtils.getValidStudy(StudyServiceImplTest.class);
        study = studyService.createStudy(study);
        
        study.setVerifyEmailTemplate(null);
        studyService.updateStudy(study, false);
    }

}
