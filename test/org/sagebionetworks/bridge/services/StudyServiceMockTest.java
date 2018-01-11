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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.services.StudyService.EXPORTER_SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.services.StudyService.SYNAPSE_REGISTER_END_POINT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseClientException;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.client.exceptions.SynapseServerException;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.MembershipInvitation;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.util.ModelConstants;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.validators.StudyValidator;

@RunWith(MockitoJUnitRunner.class)
public class StudyServiceMockTest {
    private static final Long TEST_USER_ID = Long.parseLong("3348228"); // test user exists in synapse
    private static final String TEST_NAME_SCOPING_TOKEN = "qwerty";
    private static final String TEST_PROJECT_NAME = "Test Study StudyServiceMockTest Project " + TEST_NAME_SCOPING_TOKEN;
    private static final String TEST_TEAM_NAME = "Test Study StudyServiceMockTest Access Team " + TEST_NAME_SCOPING_TOKEN;
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
    private static final Set<String> EMPTY_SET = ImmutableSet.of();

    @Mock
    private CompoundActivityDefinitionService compoundActivityDefinitionService;

    @Mock
    private NotificationTopicService topicService;

    @Mock
    private UploadCertificateService uploadCertService;
    @Mock
    private StudyDao studyDao;
    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private SubpopulationService subpopService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private ParticipantService participantService;
    @Mock
    private AccessControlList mockAccessControlList;
    @Mock
    private SynapseClient mockSynapseClient;
    @Captor
    private ArgumentCaptor<Project> projectCaptor;
    @Captor
    private ArgumentCaptor<Team> teamCaptor;

    @Spy
    private StudyService service;
    
    private Study study;
    private Team mockTeam;
    private Project mockProject;
    private MembershipInvitation mockTeamMemberInvitation;

    @Before
    public void before() {
        service.setCompoundActivityDefinitionService(compoundActivityDefinitionService);
        service.setNotificationTopicService(topicService);
        service.setUploadCertificateService(uploadCertService);
        service.setStudyDao(studyDao);
        service.setValidator(new StudyValidator());
        service.setCacheProvider(cacheProvider);
        service.setSubpopulationService(subpopService);
        service.setEmailVerificationService(emailVerificationService);
        service.setSynapseClient(mockSynapseClient);
        service.setParticipantService(participantService);
        
        when(service.getNameScopingToken()).thenReturn(TEST_NAME_SCOPING_TOKEN);
        
        study = getTestStudy();
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        // setup project and team
        mockTeam = new Team();
        mockProject = new Project();
        mockProject.setId(TEST_PROJECT_ID);
        mockTeam.setId(TEST_TEAM_ID);

        mockTeamMemberInvitation = new MembershipInvitation();
        mockTeamMemberInvitation.setInviteeId(TEST_USER_ID.toString());
        mockTeamMemberInvitation.setTeamId(TEST_TEAM_ID);
    }

    private Study getTestStudy() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setIdentifier(TEST_STUDY_ID);
        return study;
    }

    @Test
    public void cannotRemoveTaskIdentifiers() {
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setTaskIdentifiers(Sets.newHashSet("task2", "different-tag"));
        
        try {
            service.updateStudy(updatedStudy, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals("Task identifiers cannot be deleted.", e.getMessage());
            assertEquals(TEST_STUDY_ID, e.getEntityKeys().get("identifier"));
            assertEquals("Study", e.getEntityKeys().get("type"));
        }
    }
    
    @Test
    public void cannotRemoveDataGroups() {
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setDataGroups(Sets.newHashSet("beta_users", "different-tag"));
        
        try {
            service.updateStudy(updatedStudy, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals("Data groups cannot be deleted.", e.getMessage());
            assertEquals(TEST_STUDY_ID, e.getEntityKeys().get("identifier"));
            assertEquals("Study", e.getEntityKeys().get("type"));
        }
    }
    
    @Test
    public void cannotRemoveTaskIdentifiersEmptyLists() {
        study.setTaskIdentifiers(EMPTY_SET);
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setTaskIdentifiers(EMPTY_SET);
        
        service.updateStudy(updatedStudy, true);
    }
    
    @Test
    public void cannotRemoveDataGroupsEmptyLists() {
        study.setDataGroups(EMPTY_SET);
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setDataGroups(EMPTY_SET);
        
        service.updateStudy(updatedStudy, true);
    }
    
    @Test(expected = BadRequestException.class)
    public void getStudyWithNullArgumentThrows() {
        service.getStudy((String)null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getStudyWithEmptyStringArgumentThrows() {
        service.getStudy("");
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
    public void loadingStudyWithoutAccountExistsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setEmailSignInTemplate(null);
        when(studyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getEmailSignInTemplate());
        assertNotNull(retStudy.getAccountExistsTemplate());
    }
    
    @Test
    public void physicallyDeleteStudy() {
        // execute
        service.deleteStudy(TEST_STUDY_ID, true);

        // verify we called the correct dependent services
        verify(studyDao).deleteStudy(study);
        verify(compoundActivityDefinitionService).deleteAllCompoundActivityDefinitionsInStudy(
                study.getStudyIdentifier());
        verify(subpopService).deleteAllSubpopulations(study.getStudyIdentifier());
        verify(topicService).deleteAllTopics(study.getStudyIdentifier());
        verify(cacheProvider).removeStudy(TEST_STUDY_ID);
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

    @Test
    public void updateUploadMetadataOldStudyHasNoFields() {
        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(null);
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));

        // execute - no exception
        service.updateStudy(newStudy, false);
    }

    @Test
    public void updateUploadMetadataNewStudyHasNoFields() {
        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(null);

        // execute - expect exception
        try {
            service.updateStudy(newStudy, false);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            assertEquals("Non-admins cannot delete or modify upload metadata fields; affected fields: test-field",
                    ex.getMessage());
        }
    }

    @Test
    public void updateUploadMetadataCanAddAndReorderFields() {
        // make fields for test
        UploadFieldDefinition reorderedField1 = new UploadFieldDefinition.Builder().withName("reoredered-field-1")
                .withType(UploadFieldType.INT).build();
        UploadFieldDefinition reorderedField2 = new UploadFieldDefinition.Builder().withName("reoredered-field-2")
                .withType(UploadFieldType.BOOLEAN).build();
        UploadFieldDefinition addedField = new UploadFieldDefinition.Builder().withName("added-field")
                .withType(UploadFieldType.TIMESTAMP).build();

        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(reorderedField1, reorderedField2));
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(reorderedField2, reorderedField1, addedField));

        // execute - no exception
        service.updateStudy(newStudy, false);
    }

    @Test
    public void nonAdminCantDeleteOrModifyFields() {
        // make fields for test
        UploadFieldDefinition goodField = new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build();
        UploadFieldDefinition deletedField = new UploadFieldDefinition.Builder().withName("deleted-field")
                .withType(UploadFieldType.INLINE_JSON_BLOB).build();
        UploadFieldDefinition modifiedFieldOld = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withMaxLength(1000).build();
        UploadFieldDefinition modifiedlFieldNew = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withUnboundedText(true).build();

        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, deletedField, modifiedFieldOld));
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, modifiedlFieldNew));

        // execute - expect exception
        try {
            service.updateStudy(newStudy, false);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            assertEquals("Non-admins cannot delete or modify upload metadata fields; affected fields: " +
                    "deleted-field, modified-field", ex.getMessage());
        }
    }

    @Test
    public void adminCanDeleteOrModifyFields() {
        // make fields for test
        UploadFieldDefinition goodField = new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build();
        UploadFieldDefinition deletedField = new UploadFieldDefinition.Builder().withName("deleted-field")
                .withType(UploadFieldType.INLINE_JSON_BLOB).build();
        UploadFieldDefinition modifiedFieldOld = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withMaxLength(1000).build();
        UploadFieldDefinition modifiedlFieldNew = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withUnboundedText(true).build();

        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, deletedField, modifiedFieldOld));
        when(studyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, modifiedlFieldNew));

        // execute - no exception
        service.updateStudy(newStudy, true);
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
        
        // stub out use of synapse client so we can validate it, not just ignore it.
        when(mockAccessControlList.getResourceAccess()).thenReturn(new HashSet<ResourceAccess>());
        when(mockSynapseClient.createEntity(projectCaptor.capture())).thenReturn(mockProject);
        when(mockSynapseClient.getACL(TEST_PROJECT_ID)).thenReturn(mockAccessControlList);
        when(mockSynapseClient.createTeam(teamCaptor.capture())).thenReturn(mockTeam);

        // stub
        when(participantService.createParticipant(any(), any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doNothing().when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);

        // verify
        verify(participantService, times(2)).createParticipant(any(), any(), any(), anyBoolean());
        verify(participantService).createParticipant(eq(study), eq(mockUser1.getRoles()), eq(mockUser1), eq(false));
        verify(participantService).createParticipant(eq(study), eq(mockUser2.getRoles()), eq(mockUser2), eq(false));
        verify(participantService, times(2)).requestResetPassword(eq(study), eq(mockIdentifierHolder.getIdentifier()));
        verify(mockSynapseClient, times(2)).newAccountEmailValidation(any(), eq(SYNAPSE_REGISTER_END_POINT));
        verify(service).createStudy(study);
        verify(service).createSynapseProjectTeam(TEST_ADMIN_IDS, study);
        
        assertEquals(TEST_PROJECT_NAME, projectCaptor.getValue().getName());
        assertEquals(TEST_TEAM_NAME, teamCaptor.getValue().getName());
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

    @Test(expected = BadRequestException.class)
    public void createStudyAndUserNullStudyName() throws Exception {
        // mock
        Study study = getTestStudy();
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdValidationEnabled(false);
        study.setName(null); // This is not a good name...

        service.createSynapseProjectTeam(ImmutableList.of(TEST_IDENTIFIER), study);
    }
    
    @Test(expected = BadRequestException.class)
    public void createStudyAndUserBadStudyName() throws Exception {
        // mock
        Study study = getTestStudy();
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdValidationEnabled(false);
        study.setName("# # "); // This is not a good name...

        service.createSynapseProjectTeam(ImmutableList.of(TEST_IDENTIFIER), study);
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
        verify(participantService).createParticipant(eq(study), eq(mockUser1.getRoles()), eq(mockUser1), eq(false));
        verify(participantService).createParticipant(eq(study), eq(mockUser2.getRoles()), eq(mockUser2), eq(false));
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
        mockAcl.setResourceAccess(new HashSet<>());
        mockTeamAcl.setResourceAccess(new HashSet<>());

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
        assertEquals(3, capturedProjectAclSet.size()); // only has exporter and team
        // first verify exporter
        List<ResourceAccess> retListForExporter = capturedProjectAclSet.stream()
                .filter(ra -> ra.getPrincipalId().equals(Long.parseLong(EXPORTER_SYNAPSE_USER_ID)))
                .collect(Collectors.toList());

        assertNotNull(retListForExporter);
        assertEquals(1, retListForExporter.size()); // should only have one exporter info
        ResourceAccess capturedExporterRa = retListForExporter.get(0);
        assertNotNull(capturedExporterRa);
        assertEquals(EXPORTER_SYNAPSE_USER_ID, capturedExporterRa.getPrincipalId().toString());
        assertEquals(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS, capturedExporterRa.getAccessType());

        assertEquals(EXPORTER_SYNAPSE_USER_ID, capturedExporterRa.getPrincipalId().toString());
        assertEquals(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS, capturedExporterRa.getAccessType());
        // then verify target user
        List<ResourceAccess> retListForUser = capturedProjectAclSet.stream()
                .filter(ra -> ra.getPrincipalId().equals(TEST_USER_ID))
                .collect(Collectors.toList());
        assertNotNull(retListForUser);
        assertEquals(1, retListForUser.size()); // should only have one exporter info
        ResourceAccess capturedUserRa = retListForUser.get(0);
        assertNotNull(capturedUserRa);
        assertEquals(TEST_USER_ID, capturedUserRa.getPrincipalId());
        assertEquals(ModelConstants.ENITY_ADMIN_ACCESS_PERMISSIONS, capturedUserRa.getAccessType());
        
        // then verify team
        List<ResourceAccess> retListForTeam = capturedProjectAclSet.stream()
                .filter(ra -> ra.getPrincipalId().equals(Long.parseLong(TEST_TEAM_ID)))
                .collect(Collectors.toList());

        assertNotNull(retListForTeam);
        assertEquals(retListForTeam.size(), 1); // should only have one team info
        ResourceAccess capturedTeamRa = retListForTeam.get(0);
        assertNotNull(capturedTeamRa);
        assertEquals(TEST_TEAM_ID, capturedTeamRa.getPrincipalId().toString());
        assertEquals(StudyService.READ_DOWNLOAD_ACCESS, capturedTeamRa.getAccessType());

        // invite user to team
        verify(mockSynapseClient).createMembershipInvitation(eq(mockTeamMemberInvitation), any(), any());
        verify(mockSynapseClient).setTeamMemberPermissions(eq(TEST_TEAM_ID), eq(TEST_USER_ID.toString()), anyBoolean());

        // update study
        assertNotNull(retStudy);
        assertEquals(study.getIdentifier(), retStudy.getIdentifier());
        assertEquals(study.getName(), retStudy.getName());
        assertEquals(TEST_PROJECT_ID, retStudy.getSynapseProjectId());
        assertEquals(TEST_TEAM_ID, retStudy.getSynapseDataAccessTeamId().toString());
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
    public void testAllFourTemplatesAreSanitized() {
        EmailTemplate source = new EmailTemplate("<p>${studyName} test</p>", "<p>This should remove: <iframe src=''></iframe></p>", MimeType.HTML);
        Study study = new DynamoStudy();
        study.setEmailSignInTemplate(source);
        study.setResetPasswordTemplate(source);
        study.setVerifyEmailTemplate(source);
        study.setAccountExistsTemplate(source);
        
        service.sanitizeHTML(study);
        assertHtmlTemplateSanitized( study.getEmailSignInTemplate() );
        assertHtmlTemplateSanitized( study.getResetPasswordTemplate() );
        assertHtmlTemplateSanitized( study.getVerifyEmailTemplate() );
        assertHtmlTemplateSanitized( study.getAccountExistsTemplate() );
    }

    private void assertHtmlTemplateSanitized(EmailTemplate result) {
        assertEquals("${studyName} test", result.getSubject());
        assertEquals("<p>This should remove: </p>", result.getBody());
        assertEquals(MimeType.HTML, result.getMimeType());
    }
}
