package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.services.StudyService.EXPORTER_SYNAPSE_USER_ID;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.StudyValidator;

import com.google.common.collect.Sets;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.MembershipInvtnSubmission;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.util.ModelConstants;

@RunWith(MockitoJUnitRunner.class)
public class StudyServiceMockTest {

    private static final PasswordPolicy PASSWORD_POLICY = new PasswordPolicy(2, false, false, false, false);
    private static final EmailTemplate EMAIL_TEMPLATE = new EmailTemplate("new subject", "new body ${url}",
            MimeType.HTML);
    private static final Long TEST_USER_ID = Long.parseLong("3348228"); // test user exists in synapse
    private static final String TEST_PROJECT_NAME = TEST_USER_ID.toString() + "Project";
    private static final String TEST_TEAM_NAME = TEST_USER_ID.toString() + "Team";
    private static final String TEST_TEAM_ID = "1234";
    private static final String TEST_PROJECT_ID = "synapseProjectId";

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

    @Mock
    private SynapseClient mockSynapseClient;

    private StudyService service;
    private Team mockTeam;
    private Project mockProject;
    private MembershipInvtnSubmission mockTeamMemberInvitation;

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
        service.setSynapseClient(mockSynapseClient);

        when(studyDao.getStudy("test-study")).thenReturn(getTestStudy());

        // setup project and team
        mockTeam = new Team();
        mockProject = new Project();

        mockProject.setName(TEST_PROJECT_NAME);
        mockProject.setId(TEST_PROJECT_ID);
        mockTeam.setName(TEST_TEAM_NAME);
        mockTeam.setId(TEST_TEAM_ID);

        mockTeamMemberInvitation = new MembershipInvtnSubmission();
        mockTeamMemberInvitation.setInviteeId(TEST_USER_ID.toString());
        mockTeamMemberInvitation.setTeamId(TEST_TEAM_ID);
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

    @Test(expected = BadRequestException.class)
    public void deactivateStudyAlreadyDeactivatedBefore() {
        Study study = getTestStudy();
        study.setActive(false);
        when(studyDao.getStudy(study.getIdentifier())).thenReturn(study);

        service.deleteStudy(study.getIdentifier(), false);
    }

    @Test(expected = EntityNotFoundException.class)
    public void deactivateStudyNotFound() {
        Study study = getTestStudy();
        study.setActive(false);
        when(studyDao.getStudy(study.getIdentifier())).thenReturn(null);

        service.deleteStudy(study.getIdentifier(), false);

        verify(studyDao, never()).deactivateStudy(anyString());
        verify(studyDao, never()).deleteStudy(any());

    }

    @Test(expected = EntityNotFoundException.class)
    public void nonAdminsCannotUpdateDeactivatedStudy() {
        Study study = getTestStudy();
        study.setActive(false);
        when(studyDao.getStudy(study.getIdentifier())).thenReturn(study);

        service.updateStudy(study, false);

        verify(studyDao, never()).updateStudy(any());
    }

    @Test(expected = BadRequestException.class)
    public void nonAdminsCannotSetActiveToFalse() {
        Study originalStudy = getTestStudy();
        originalStudy.setActive(true);
        when(studyDao.getStudy(originalStudy.getIdentifier())).thenReturn(originalStudy);

        Study study = getTestStudy();
        study.setIdentifier(originalStudy.getIdentifier());
        study.setActive(false);

        service.updateStudy(study, false);

        verify(studyDao, never()).updateStudy(any());
    }

    @Test(expected = BadRequestException.class)
    public void adminCannotSetActiveToFalse() {
        Study originalStudy = getTestStudy();
        originalStudy.setActive(true);
        when(studyDao.getStudy(originalStudy.getIdentifier())).thenReturn(originalStudy);

        Study study = getTestStudy();
        study.setIdentifier(originalStudy.getIdentifier());
        study.setActive(false);

        service.updateStudy(study, true);

        verify(studyDao, never()).updateStudy(any());
    }

    @Test
    public void createSynapseProjectTeam() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        AccessControlList mockAcl = new AccessControlList();
        AccessControlList mockTeamAcl = new AccessControlList();
        mockAcl.setResourceAccess(new HashSet<ResourceAccess>());
        mockTeamAcl.setResourceAccess(new HashSet<ResourceAccess>());

        // pre-setup
        when(mockSynapseClient.createTeam(any())).thenReturn(mockTeam);
        when(mockSynapseClient.createEntity(any())).thenReturn(mockProject);
        when(mockSynapseClient.getACL(any())).thenReturn(mockAcl);
        when(mockSynapseClient.getTeamACL(any())).thenReturn(mockTeamAcl);

        // execute
        Study retStudy = service.createSynapseProjectTeam(TEST_USER_ID, study);

        // verify
        // create project and team
        verify(mockSynapseClient).createTeam(any());
        verify(mockSynapseClient).createEntity(any());
        // get project acl
        verify(mockSynapseClient).getACL(eq(TEST_PROJECT_ID));

        // update project acl
        ArgumentCaptor<AccessControlList> argumentProjectAcl = ArgumentCaptor.forClass(AccessControlList.class);
        verify(mockSynapseClient).updateACL(argumentProjectAcl.capture());
        AccessControlList capturedProjectAcl = argumentProjectAcl.getValue();
        Set<ResourceAccess> capturedProjectAclSet = capturedProjectAcl.getResourceAccess();
        assertNotNull(capturedProjectAclSet);
        assertEquals(capturedProjectAclSet.size(), 2); // only has target user and exporter
        // first verify exporter
        List<ResourceAccess> retListForExporter = capturedProjectAclSet.stream()
                .filter(ra -> ra.getPrincipalId().equals(Long.parseLong(EXPORTER_SYNAPSE_USER_ID)))
                .collect(Collectors.toList());

        assertNotNull(retListForExporter);
        assertEquals(retListForExporter.size(), 1); // should only have one exporter info
        ResourceAccess capturedExporterRa = retListForExporter.get(0);
        assertNotNull(capturedExporterRa);
        assertEquals(capturedExporterRa.getPrincipalId().toString(), EXPORTER_SYNAPSE_USER_ID);
        assertEquals(capturedExporterRa.getAccessType(), ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);
        // then verify target user
        List<ResourceAccess> retListForUser = capturedProjectAclSet.stream()
                .filter(ra -> ra.getPrincipalId().equals(TEST_USER_ID))
                .collect(Collectors.toList());

        assertNotNull(retListForUser);
        assertEquals(retListForUser.size(), 1); // should only have one exporter info
        ResourceAccess capturedUserRa = retListForUser.get(0);
        assertNotNull(capturedUserRa);
        assertEquals(capturedUserRa.getPrincipalId(), TEST_USER_ID);
        assertEquals(capturedUserRa.getAccessType(), ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);

        // invite user to team
        verify(mockSynapseClient).createMembershipInvitation(eq(mockTeamMemberInvitation), any(), any());
        verify(mockSynapseClient).setTeamMemberPermissions(eq(TEST_TEAM_ID), eq(TEST_USER_ID.toString()), anyBoolean());

        // update study
        assertNotNull(retStudy);
        assertEquals(retStudy.getIdentifier(), study.getIdentifier());
        assertEquals(retStudy.getName(), study.getName());
        assertEquals(retStudy.getSynapseProjectId(), TEST_PROJECT_ID);
        assertEquals(retStudy.getSynapseDataAccessTeamId().toString(), TEST_TEAM_ID);
    }

    @Test
    public void changingIrrelevantFieldsDoesNotUpdateDirectory() {
        Study study = getTestStudy();

        // here's a bunch of things we can change that won't cause the directory to be updated
        study.setSynapseDataAccessTeamId(23L);
        study.setSynapseProjectId("newid");
        study.setConsentNotificationEmail("newemail@newemail.com");
        study.setMinAgeOfConsent(50);
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
    public void changingEmailVerificationEnabledUpdatesDirectory() {
        assertDirectoryUpdated(study -> study.setEmailVerificationEnabled(false));
    }
    
    @Test
    public void newStudyVerifiesSupportEmail() {
        Study study = getTestStudy();
        when(emailVerificationService.verifyEmailAddress(study.getSupportEmail()))
                .thenReturn(EmailVerificationStatus.PENDING);
        when(studyDao.createStudy(study)).thenReturn(study);

        service.createStudy(study);

        verify(emailVerificationService).verifyEmailAddress(study.getSupportEmail());
        assertTrue(study.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP));
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
