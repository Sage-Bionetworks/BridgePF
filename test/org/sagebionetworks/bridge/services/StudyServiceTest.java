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
import static org.sagebionetworks.bridge.services.StudyService.EXPORTER_SYNAPSE_USER_ID;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.reflection.model.PaginatedResults;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.MembershipInvtnSubmission;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.util.ModelConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceTest {

    private static final Long TEST_USER_ID = Long.parseLong(BridgeConfigFactory.getConfig().getTestSynapseUserId()); // test user exists in synapse
    private static final String TEST_PROJECT_ID = "testProjectId";
    private static final Long TEST_TEAM_ID = 1234L;

    @Resource
    StudyService studyService;
    
    @Resource
    StudyConsentService studyConsentService;
    
    @Resource
    SubpopulationService subpopService;
    
    @Resource
    DirectoryDao directoryDao;
    
    @Resource
    SubpopulationDao subpopDao;

    @Resource
    SynapseClient synapseClient;
    
    @Resource
    NotificationTopicService topicService;

    @Autowired
    CacheProvider cache;
    
    private CacheProvider mockCache;
    
    private Study study;

    private Project project;
    private Team team;
    
    @Before
    public void before() {
        mockCache = mock(CacheProvider.class);
        studyService.setCacheProvider(mockCache);
    }
    
    @After
    public void after() throws SynapseException {
        if (study != null) {
            studyService.deleteStudy(study.getIdentifier(), true);
        }
        if (project != null) {
            synapseClient.deleteEntityById(project.getId());
        }
        if (team != null) {
            synapseClient.deleteTeam(team.getId());
        }
    }

    @After
    public void resetCache() {
        studyService.setCacheProvider(cache);
    }

    @Test
    public void createSynapseProjectTeam() throws SynapseException {
        // integration test with synapseclient
        // pre-setup
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        // remove team and project id for succeed testing
        study.setSynapseDataAccessTeamId(null);
        study.setSynapseProjectId(null);
        studyService.createStudy(study);

        // execute
        Study retStudy = studyService.createSynapseProjectTeam(TEST_USER_ID, study);

        // verify study
        assertEquals(retStudy.getIdentifier(), study.getIdentifier());
        String projectId = retStudy.getSynapseProjectId();
        Long teamId = retStudy.getSynapseDataAccessTeamId();

        // verify if project and team exists
        Entity project = synapseClient.getEntityById(projectId);
        assertNotNull(project);
        assertEquals(project.getEntityType(), "org.sagebionetworks.repo.model.Project");
        this.project = (Project) project;
        Team team = synapseClient.getTeam(teamId.toString());
        assertNotNull(team);
        this.team = team;

        // project acl
        AccessControlList projectAcl = synapseClient.getACL(projectId);
        Set<ResourceAccess> projectRa =  projectAcl.getResourceAccess();
        assertNotNull(projectRa);
        assertEquals(projectRa.size(), 3); // target user, exporter and bridgepf itself
        // first verify exporter
        List<ResourceAccess> retListForExporter = projectRa.stream()
                .filter(ra -> ra.getPrincipalId().equals(Long.parseLong(EXPORTER_SYNAPSE_USER_ID)))
                .collect(Collectors.toList());

        assertNotNull(retListForExporter);
        assertEquals(retListForExporter.size(), 1); // should only have one exporter info
        ResourceAccess exporterRa = retListForExporter.get(0);
        assertNotNull(exporterRa);
        assertEquals(exporterRa.getPrincipalId().toString(), EXPORTER_SYNAPSE_USER_ID);
        assertEquals(exporterRa.getAccessType(), ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);
        // then verify target user
        List<ResourceAccess> retListForUser = projectRa.stream()
                .filter(ra -> ra.getPrincipalId().equals(TEST_USER_ID))
                .collect(Collectors.toList());

        assertNotNull(retListForUser);
        assertEquals(retListForUser.size(), 1); // should only have target user info
        ResourceAccess userRa = retListForUser.get(0);
        assertNotNull(userRa);
        assertEquals(userRa.getPrincipalId(), TEST_USER_ID);
        assertEquals(userRa.getAccessType(), ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);

        // membership invitation to target user
        // (teamId, inviteeId, limit, offset)
        PaginatedResults<MembershipInvtnSubmission> retInvitations =  synapseClient.getOpenMembershipInvitationSubmissions(teamId.toString(), TEST_USER_ID.toString(), 1, 0);
        List<MembershipInvtnSubmission> invitationList = retInvitations.getResults();
        assertEquals(invitationList.size(), 1); // only one invitation submission from newly created team to target user
        MembershipInvtnSubmission invtnSubmission = invitationList.get(0);
        assertEquals(invtnSubmission.getInviteeId(), TEST_USER_ID.toString());
        assertEquals(invtnSubmission.getTeamId(), teamId.toString());
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void studyHasProjectId() throws SynapseException {
        Study testStudy = new DynamoStudy();
        // remove team and project id for succeed testing
        testStudy.setSynapseDataAccessTeamId(null);
        testStudy.setSynapseProjectId(TEST_PROJECT_ID);
        studyService.createSynapseProjectTeam(TEST_USER_ID, testStudy);
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void studyHasTeamId() throws SynapseException {
        Study testStudy = new DynamoStudy();
        // remove team and project id for succeed testing
        testStudy.setSynapseDataAccessTeamId(TEST_TEAM_ID);
        testStudy.setSynapseProjectId(null);
        studyService.createSynapseProjectTeam(TEST_USER_ID, testStudy);
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
        Subpopulation subpop = subpopService.getSubpopulation(study.getStudyIdentifier(),
                SubpopulationGuid.create(study.getIdentifier()));
        StudyConsentView view = studyConsentService.getActiveConsent(subpop);
        assertTrue(view.getDocumentContent().contains("This is a placeholder for your consent document."));
        
        // Create an associated topic
        NotificationTopic topic = TestUtils.getNotificationTopic();
        topic.setStudyId(study.getIdentifier());
        topicService.createTopic(topic);
        assertEquals(1, topicService.listTopics(study.getStudyIdentifier()).size());
        
        Study newStudy = studyService.getStudy(study.getIdentifier());
        assertTrue(newStudy.isActive());
        assertTrue(newStudy.isStrictUploadValidationEnabled());
        assertTrue(newStudy.isEmailVerificationEnabled());
        assertEquals(study.getIdentifier(), newStudy.getIdentifier());
        assertEquals("Test Study [StudyServiceTest]", newStudy.getName());
        assertEquals(18, newStudy.getMinAgeOfConsent());
        assertEquals(Sets.newHashSet("beta_users", "production_users", BridgeConstants.TEST_USER_GROUP),
                newStudy.getDataGroups());
        assertEquals(0, newStudy.getTaskIdentifiers().size());
        // these should have been changed
        assertNotEquals("http://local-test-junk", newStudy.getStormpathHref());
        verify(mockCache).getStudy(newStudy.getIdentifier());
        verify(mockCache).setStudy(newStudy);
        verifyNoMoreInteractions(mockCache);
        reset(mockCache);

        studyService.deleteStudy(study.getIdentifier(), true);
        verify(mockCache).getStudy(study.getIdentifier());
        verify(mockCache).setStudy(study);
        verify(mockCache).removeStudy(study.getIdentifier());
        
        assertEquals(0, topicService.listTopics(study.getStudyIdentifier()).size());
        
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
        study.setHealthCodeExportEnabled(false);
        study.setEmailVerificationEnabled(true);
        study = studyService.createStudy(study);
        
        // Okay, now that these are set, researchers cannot change them
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(false);
        study = studyService.updateStudy(study, false); // nope
        assertFalse("isHealthCodeExportEnabled should be false", study.isHealthCodeExportEnabled());
        assertTrue("isEmailVerificationEnabled should be true", study.isEmailVerificationEnabled());
        
        // But administrators can
        study.setHealthCodeExportEnabled(true);
        study.setEmailVerificationEnabled(false);
        study = studyService.updateStudy(study, true); // yep
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
        studyService.deleteStudy("api", true);
    }
    
    @Test
    public void ckeditorHTMLIsPreserved() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        
        String body = "<s>This is a test</s><p style=\"color:red\">of new attributes ${url}.</p><hr>";
        
        EmailTemplate template = new EmailTemplate("Subject", body, MimeType.HTML);
        
        study.setVerifyEmailTemplate(template);
        study.setResetPasswordTemplate(template);
        study = studyService.createStudy(study);
        
        // The templates are pretty-print formatted, so remove that. Otherwise, everything should be
        // preserved.
        
        template = study.getVerifyEmailTemplate();
        assertEquals(body, template.getBody().replaceAll("[\n\t\r]", ""));
        
        template = study.getResetPasswordTemplate();
        assertEquals(body, template.getBody().replaceAll("[\n\t\r]", ""));
    }
}
