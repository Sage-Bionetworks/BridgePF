package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.services.StudyService.EXPORTER_SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.services.StudyService.SYNAPSE_REGISTER_END_POINT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseClientException;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.client.exceptions.SynapseServerException;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.MembershipInvtnSubmission;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.util.ModelConstants;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.validators.StudyValidator;

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

    // Don't use TestConstants.TEST_STUDY since this conflicts with the whitelist.
    private static final String TEST_STUDY_ID = "test-study";

    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password12AB";
    private static final String TEST_IDENTIFIER = "test_identifier";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";
    private static final List<String> TEST_ADMIN_IDS = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);

    @Mock
    private CompoundActivityDefinitionService compoundActivityDefinitionService;

    @Mock
    private NotificationTopicService topicService;

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
    private ParticipantService participantService;
    @Mock
    private SchedulePlanService schedulePlanService;

    @Mock
    private SynapseClient mockSynapseClient;

    private StudyService service;
    private Study study;
    private Team mockTeam;
    private Project mockProject;
    private MembershipInvtnSubmission mockTeamMemberInvitation;

    @Before
    public void before() {
        service = spy(new StudyService());
        service.setCompoundActivityDefinitionService(compoundActivityDefinitionService);
        service.setNotificationTopicService(topicService);
        service.setUploadCertificateService(uploadCertService);
        service.setStudyDao(studyDao);
        service.setDirectoryDao(directoryDao);
        service.setValidator(new StudyValidator());
        service.setCacheProvider(cacheProvider);
        service.setSubpopulationService(subpopService);
        service.setEmailVerificationService(emailVerificationService);
        service.setSynapseClient(mockSynapseClient);
        service.setParticipantService(participantService);
        service.setSchedulePlanService(schedulePlanService);

        study = getTestStudy();
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

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
        study.setIdentifier(TEST_STUDY_ID);
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
    public void loadingStudyWithoutEmailSignInTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setEmailSignInTemplate(null);
        when(studyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getEmailSignInTemplate());
    }

    @Test
    public void physicallyDeleteStudy() {
        // execute
        service.deleteStudy(TEST_STUDY_ID, true);

        // verify we called the correct dependent services
        verify(studyDao).deleteStudy(study);
        verify(directoryDao).deleteDirectoryForStudy(study);
        verify(compoundActivityDefinitionService).deleteAllCompoundActivityDefinitionsInStudy(
                study.getStudyIdentifier());
        verify(subpopService).deleteAllSubpopulations(study.getStudyIdentifier());
        verify(topicService).deleteAllTopics(study.getStudyIdentifier());
        verify(cacheProvider).removeStudy(TEST_STUDY_ID);
    }
    
    @Test
    public void cannotRemoveTaskIdentifierInUse() {
        String taskId = study.getTaskIdentifiers().iterator().next();
        study.getTaskIdentifiers().remove(taskId);

        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(new StudyIdentifierImpl(TEST_STUDY_ID));
        Activity newActivity = new Activity.Builder().withTask(new TaskReference(taskId, null)).build();
        plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().set(0, newActivity);
        when(schedulePlanService.getSchedulePlans(any(), any())).thenReturn(Lists.newArrayList(plan));
        
        try {
            service.updateStudy(study, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            verify(studyDao, never()).updateStudy(study);
            assertEquals("test-study", e.getEntityKeys().get("identifier"));
            assertEquals("Study", e.getEntityKeys().get("type"));
            assertEquals("GGG", e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
        }
    }
    
    @Test
    public void canRemoveDataGroupIfSubpopulationDeleted() {
        String taskId = study.getTaskIdentifiers().iterator().next();
        study.getTaskIdentifiers().remove(taskId);
        
        Criteria criteria = Criteria.create();
        criteria.getAllOfGroups().add(taskId);
        Subpopulation subpop = Subpopulation.create();
        subpop.setCriteria(criteria);
        subpop.setGuidString("guidString");
        subpop.setDeleted(true);
        
        when(subpopService.getSubpopulations(any())).thenReturn(Lists.newArrayList(subpop));
        
        service.updateStudy(study, true);
        
        verify(studyDao).updateStudy(study);
    }
    
    @Test
    public void cannotRemoveDataGroupInUseInSubpopulation() {
        String taskId = study.getTaskIdentifiers().iterator().next();
        study.getTaskIdentifiers().remove(taskId);
        
        Criteria criteria = Criteria.create();
        criteria.getAllOfGroups().add(taskId);
        Subpopulation subpop = Subpopulation.create();
        subpop.setCriteria(criteria);
        subpop.setGuidString("guidString");
        
        when(subpopService.getSubpopulations(any())).thenReturn(Lists.newArrayList(subpop));
        
        try {
            service.updateStudy(study, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            verify(studyDao, never()).updateStudy(study);
            assertEquals("test-study", e.getEntityKeys().get("identifier"));
            assertEquals("Study", e.getEntityKeys().get("type"));
            assertEquals("guidString", e.getReferrerKeys().get("guid"));
            assertEquals("Subpopulation", e.getReferrerKeys().get("type"));
        }
    }

    @Test
    public void cannotRemoveDataGroupInUseInSchedulePlan() {
        String taskId = study.getTaskIdentifiers().iterator().next();
        study.getTaskIdentifiers().remove(taskId);

        SchedulePlan plan = TestUtils.getCriteriaSchedulePlan(new StudyIdentifierImpl(TEST_STUDY_ID));
        when(schedulePlanService.getSchedulePlans(any(), any())).thenReturn(Lists.newArrayList(plan));
        
        try {
            service.updateStudy(study, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            verify(studyDao, never()).updateStudy(study);
            assertEquals("test-study", e.getEntityKeys().get("identifier"));
            assertEquals("Study", e.getEntityKeys().get("type"));
            assertEquals("GGG", e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
        }
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
    public void createStudyAndUser() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdValidationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);

        // spy
        doReturn(study).when(service).createStudy(any());
        doReturn(study).when(service).createSynapseProjectTeam(any(), any());

        // stub
        when(participantService.createParticipant(any(), any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doNothing().when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);

        // verify
        verify(participantService, times(2)).createParticipant(any(), any(), any(), anyBoolean());
        verify(participantService).createParticipant(eq(study), eq(mockUser1.getRoles()), eq(mockUser1), eq(true));
        verify(participantService).createParticipant(eq(study), eq(mockUser2.getRoles()), eq(mockUser2), eq(true));
        verify(participantService, times(2)).requestResetPassword(eq(study), eq(mockIdentifierHolder.getIdentifier()));
        verify(mockSynapseClient, times(2)).newAccountEmailValidation(any(), eq(SYNAPSE_REGISTER_END_POINT));
        verify(service).createStudy(study);
        verify(service).createSynapseProjectTeam(TEST_ADMIN_IDS, study);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithInvalidRoles() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.ADMIN))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(null, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithNullAdmins() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(null, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithEmptyRoles() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of())
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(null, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithEmptyAdmins() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(ImmutableList.of(), study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithEmptyUser() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        List<StudyParticipant> mockUsers = new ArrayList<>();
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithNullUser() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, null);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expected = BadRequestException.class)
    public void createStudyAndUserWithNullStudy() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, null, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test(expected = SynapseClientException.class)
    public void createStudyAndUserThrowExceptionNotLogged() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdValidationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);

        // spy
        doReturn(study).when(service).createStudy(any());
        doReturn(study).when(service).createSynapseProjectTeam(any(), any());

        // stub
        when(participantService.createParticipant(any(), any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doThrow(SynapseClientException.class).when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test
    public void createStudyAndUserThrowExceptionLogged() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdValidationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);

        // spy
        doReturn(study).when(service).createStudy(any());
        doReturn(study).when(service).createSynapseProjectTeam(any(), any());

        // stub
        when(participantService.createParticipant(any(), any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doThrow(new SynapseServerException(500, "The email address provided is already used.")).when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);

        // verify
        verify(participantService, times(2)).createParticipant(any(), any(), any(), anyBoolean());
        verify(participantService).createParticipant(eq(study), eq(mockUser1.getRoles()), eq(mockUser1), eq(true));
        verify(participantService).createParticipant(eq(study), eq(mockUser2.getRoles()), eq(mockUser2), eq(true));
        verify(participantService, times(2)).requestResetPassword(eq(study), eq(mockIdentifierHolder.getIdentifier()));
        verify(mockSynapseClient, times(2)).newAccountEmailValidation(any(), eq(SYNAPSE_REGISTER_END_POINT));
        verify(service).createStudy(study);
        verify(service).createSynapseProjectTeam(TEST_ADMIN_IDS, study);
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
        Study retStudy = service.createSynapseProjectTeam(ImmutableList.of(TEST_USER_ID.toString()), study);

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
        assertEquals(capturedProjectAclSet.size(), 3); // only has target user, exporter and team
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
        // then verify team
        List<ResourceAccess> retListForTeam = capturedProjectAclSet.stream()
                .filter(ra -> ra.getPrincipalId().equals(Long.parseLong(TEST_TEAM_ID)))
                .collect(Collectors.toList());

        assertNotNull(retListForTeam);
        assertEquals(retListForTeam.size(), 1); // should only have one team info
        ResourceAccess capturedTeamRa = retListForTeam.get(0);
        assertNotNull(capturedTeamRa);
        assertEquals(capturedTeamRa.getPrincipalId().toString(), TEST_TEAM_ID);
        assertEquals(capturedTeamRa.getAccessType(), ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS);

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

    @SuppressWarnings("unchecked")
    @Test(expected = BadRequestException.class)
    public void createSynapseProjectTeamNonExistUserID() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        // pre-setup
        when(mockSynapseClient.getUserProfile(any())).thenThrow(SynapseNotFoundException.class);

        // execute
        service.createSynapseProjectTeam(ImmutableList.of(TEST_USER_ID.toString()), study);
    }

    @Test(expected = BadRequestException.class)
    public void createSynapseProjectTeamNullUserID() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        // execute
        service.createSynapseProjectTeam(null, study);
    }

    @Test(expected = BadRequestException.class)
    public void createSynapseProjectTeamEmptyUserID() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        // execute
        service.createSynapseProjectTeam(ImmutableList.of(), study);
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
    
    @Test
    public void textTemplateIsSanitized() {
        EmailTemplate source = new EmailTemplate("<p>Test</p>","<p>This should have no markup</p>", MimeType.TEXT);
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        assertEquals("Test", result.getSubject());
        assertEquals("This should have no markup", result.getBody());
        assertEquals(MimeType.TEXT, result.getMimeType());
    }
    
    @Test
    public void htmlTemplateIsSanitized() {
        EmailTemplate source = new EmailTemplate("<p>${studyName} test</p>", "<p>This should remove: <iframe src=''></iframe></p>", MimeType.HTML); 
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        assertHtmlTemplateSanitized(result);
    }
    
    @Test
    public void emptyTemplateIsSanitized() {
        EmailTemplate source = new EmailTemplate("", "", MimeType.HTML); 
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        assertEquals("", result.getSubject());
        assertEquals("", result.getBody());
        assertEquals(MimeType.HTML, result.getMimeType());
    }
    
    @Test
    public void testAllThreeTemplatesAreSanitized() {
        EmailTemplate source = new EmailTemplate("<p>${studyName} test</p>", "<p>This should remove: <iframe src=''></iframe></p>", MimeType.HTML);
        Study study = new DynamoStudy();
        study.setEmailSignInTemplate(source);
        study.setResetPasswordTemplate(source);
        study.setVerifyEmailTemplate(source);
        
        service.sanitizeHTML(study);
        assertHtmlTemplateSanitized( study.getEmailSignInTemplate() );
        assertHtmlTemplateSanitized( study.getResetPasswordTemplate() );
        assertHtmlTemplateSanitized( study.getVerifyEmailTemplate() );
    }

    private void assertHtmlTemplateSanitized(EmailTemplate result) {
        assertEquals("${studyName} test", result.getSubject());
        assertEquals("<p>This should remove: </p>", result.getBody());
        assertEquals(MimeType.HTML, result.getMimeType());
    }
}
