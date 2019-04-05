package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import play.core.j.JavaResultExtractor;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DefaultObjectMapper;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.studies.SynapseProjectIdTeamIdHolder;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;
import org.sagebionetworks.bridge.services.StudyEmailType;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UploadService;

@RunWith(MockitoJUnitRunner.class)
public class StudyControllerTest {
    private static final String DUMMY_VERIFICATION_TOKEN = "dummy-token";
    private static final String EMAIL_ADDRESS = "foo@foo.com";

    private static final String PEM_TEXT = "-----BEGIN CERTIFICATE-----\nMIIExDCCA6ygAwIBAgIGBhCnnOuXMA0GCSqGSIb3DQEBBQUAMIGeMQswCQYDVQQG\nEwJVUzELMAkGA1UECAwCV0ExEDAOBgNVBAcMB1NlYXR0bGUxGTAXBgNVBAoMEFNh\nVlOwuuAxumMyIq5W4Dqk8SBcH9Y4qlk7\nEND CERTIFICATE-----";

    private static final TypeReference<ForwardCursorPagedResourceList<? extends Upload>> UPLOADS_REF = new TypeReference<ForwardCursorPagedResourceList<? extends Upload>>(){};

    private static final String TEST_PROJECT_ID = "synapseProjectId";
    private static final Long TEST_TEAM_ID = Long.parseLong("123");
    private static final String TEST_USER_ID = "1234";
    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";

    private StudyController controller;
    private StudyIdentifier studyId;

    @Mock
    private UserSession mockSession;
    @Mock
    private UploadCertificateService mockUploadCertService;
    @Mock
    private StudyService mockStudyService;
    @Mock
    private EmailVerificationService mockVerificationService;
    @Mock
    private CacheProvider mockCacheProvider;
    @Mock
    private UploadService mockUploadService;
    @Mock
    private BridgeConfig mockBridgeConfig;
    
    private Study study;
    
    @Before
    public void before() throws Exception {
        controller = spy(new StudyController());
        
        // mock session with study identifier
        studyId = new StudyIdentifierImpl(TestConstants.TEST_STUDY_IDENTIFIER + "test");
        when(mockSession.getStudyIdentifier()).thenReturn(studyId);
        
        study = new DynamoStudy();
        study.setSupportEmail(EMAIL_ADDRESS);
        study.setIdentifier(studyId.getIdentifier());
        study.setSynapseProjectId(TEST_PROJECT_ID);
        study.setSynapseDataAccessTeamId(TEST_TEAM_ID);
        study.setActive(true);
        
        when(mockStudyService.getStudy(studyId)).thenReturn(study);
        when(mockStudyService.createSynapseProjectTeam(any(), any())).thenReturn(study);

        when(mockVerificationService.getEmailStatus(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);

        mockUploadCertService = mock(UploadCertificateService.class);
        when(mockUploadCertService.getPublicKeyAsPem(any(StudyIdentifier.class))).thenReturn(PEM_TEXT);
        
        controller.setStudyService(mockStudyService);
        controller.setCacheProvider(mockCacheProvider);
        controller.setEmailVerificationService(mockVerificationService);
        controller.setUploadCertificateService(mockUploadCertService);
        controller.setUploadService(mockUploadService);
        controller.setBridgeConfig(mockBridgeConfig);
        
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        
        TestUtils.mockPlay().mock();
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode")
                .withRoles(Sets.newHashSet()).build();
        UserSession session = new UserSession(participant);
        session.setStudyIdentifier(studyId);
        session.setAuthenticated(true);
        
        doReturn(session).when(controller).getSessionIfItExists();

        controller.getStudyPublicKeyAsPem();
    }

    @Test(expected = UnauthorizedException.class)
    public void cannotAccessGetUploadsForSpecifiedStudyUnlessWorker () throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode")
                .withRoles(Sets.newHashSet()).build();
        UserSession session = new UserSession(participant);
        session.setStudyIdentifier(studyId);
        session.setAuthenticated(true);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        doReturn(session).when(controller).getSessionIfItExists();

        controller.getUploadsForStudy(studyId.getIdentifier(), startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test
    public void canDeactivateForAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);

        controller.deleteStudy(study.getIdentifier(), "false");

        verify(mockStudyService).deleteStudy(study.getIdentifier(), false);
        verifyNoMoreInteractions(mockStudyService);
    }

    @Test
    public void canDeleteForAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);

        controller.deleteStudy(study.getIdentifier(), "true");

        verify(mockStudyService).deleteStudy(study.getIdentifier(), true);
        verifyNoMoreInteractions(mockStudyService);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void cannotDeactivateForDeveloper() throws Exception {
        controller.deleteStudy(study.getIdentifier(), "false");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void cannotDeleteForDeveloper() throws Exception {
        controller.deleteStudy(study.getIdentifier(), "true");
    }

    @Test(expected = EntityNotFoundException.class)
    public void deactivateStudyThrowsGoodException() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).deleteStudy(study.getIdentifier(), false);

        controller.deleteStudy(study.getIdentifier(), "false");
    }

    @Test
    public void canCreateStudyAndUser() throws Exception {
        // mock
        Study study = TestUtils.getValidStudy(StudyControllerTest.class);
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setVersion(1L);

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
        List<String> adminIds = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);

        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(adminIds, study, mockUsers);
        TestUtils.mockPlay().withBody(mockStudyAndUsers).mock();

        // stub
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        ArgumentCaptor<StudyAndUsers> argumentCaptor = ArgumentCaptor.forClass(StudyAndUsers.class);
        when(mockStudyService.createStudyAndUsers(argumentCaptor.capture())).thenReturn(study);

        // execute
        Result result = controller.createStudyAndUsers();
        TestUtils.assertResult(result, 201);
        
        String versionHolderStr = Helpers.contentAsString(result);
        VersionHolder versionHolder = BridgeObjectMapper.get().readValue(versionHolderStr, VersionHolder.class);

        // verify
        verify(mockStudyService, times(1)).createStudyAndUsers(any());
        StudyAndUsers capObj = argumentCaptor.getValue();
        assertEquals(study, capObj.getStudy());
        assertEquals(mockUsers, capObj.getUsers());
        assertEquals(adminIds, capObj.getAdminIds());
        assertEquals(study.getVersion(), versionHolder.getVersion());
    }


    @Test
    public void canCreateSynapse() throws Exception {
        // mock
        List<String> mockUserIds = ImmutableList.of(TEST_USER_ID);
        TestUtils.mockPlay().withBody(mockUserIds).mock();

        // stub
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        Result result = controller.createSynapse();
        TestUtils.assertResult(result, 201);
        String synapseIds = Helpers.contentAsString(result);

        // verify
        verify(mockStudyService).getStudy(eq(studyId));
        verify(mockStudyService).createSynapseProjectTeam(eq(mockUserIds), eq(study));

        JsonNode synapse = BridgeObjectMapper.get().readTree(synapseIds);
        assertEquals(TEST_PROJECT_ID, synapse.get("projectId").asText());
        assertEquals(TEST_TEAM_ID.longValue(), synapse.get("teamId").asLong());
        assertEquals(SynapseProjectIdTeamIdHolder.class.getName(), "org.sagebionetworks.bridge.models.studies." + synapse.get("type").asText());
    }

    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Result result = controller.getStudyPublicKeyAsPem();
        TestUtils.assertResult(result, 200);

        String pemFile = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(pemFile);
        assertTrue(node.get("publicKey").asText().contains("-----BEGIN CERTIFICATE-----"));
        assertEquals("CmsPublicKey", node.get("type").asText());
    }
    
    @Test
    public void getEmailStatus() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Result result = controller.getEmailStatus();
        TestUtils.assertResult(result, 200);
        
        verify(mockVerificationService).getEmailStatus(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }
    
    @Test
    public void verifySenderEmail() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockVerificationService.verifyEmailAddress(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);
        
        Result result = controller.verifySenderEmail();
        TestUtils.assertResult(result, 200);
        
        verify(mockVerificationService).verifyEmailAddress(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }

    @Test(expected = BadRequestException.class)
    public void resendVerifyEmailNullType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail(null);
    }

    @Test(expected = BadRequestException.class)
    public void resendVerifyEmailEmptyType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail("");
    }

    @Test(expected = BadRequestException.class)
    public void resendVerifyEmailBlankType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail("   ");
    }

    @Test(expected = BadRequestException.class)
    public void resendVerifyEmailInvalidType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail("bad-type");
    }

    @Test
    public void resendVerifyEmailSuccess() throws Exception {
        // Mock session
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        // Execute
        Result result = controller.resendVerifyEmail(StudyEmailType.CONSENT_NOTIFICATION.toString().toLowerCase());
        TestUtils.assertResult(result, 200);

        // Verify call to StudyService
        verify(mockStudyService).sendVerifyEmail(studyId, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expected = BadRequestException.class)
    public void verifyEmailNullType() throws Exception {
        controller.verifyEmail(studyId.getIdentifier(), DUMMY_VERIFICATION_TOKEN, null);
    }

    @Test(expected = BadRequestException.class)
    public void verifyEmailEmptyType() throws Exception {
        controller.verifyEmail(studyId.getIdentifier(), DUMMY_VERIFICATION_TOKEN, "");
    }

    @Test(expected = BadRequestException.class)
    public void verifyEmailBlankType() throws Exception {
        controller.verifyEmail(studyId.getIdentifier(), DUMMY_VERIFICATION_TOKEN, "   ");
    }

    @Test(expected = BadRequestException.class)
    public void verifyEmailInvalidType() throws Exception {
        controller.verifyEmail(studyId.getIdentifier(), DUMMY_VERIFICATION_TOKEN, "bad-type");
    }

    @Test
    public void verifyEmailSuccess() throws Exception {
        // Execute
        Result result = controller.verifyEmail(studyId.getIdentifier(), DUMMY_VERIFICATION_TOKEN,
                StudyEmailType.CONSENT_NOTIFICATION.toString().toLowerCase());
        TestUtils.assertResult(result, 200);

        // Verify call to StudyService
        verify(mockStudyService).verifyEmail(studyId, DUMMY_VERIFICATION_TOKEN, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test
    public void developerCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(DEVELOPER);
    }
    
    @Test
    public void researcherCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(RESEARCHER);
    }
    
    @Test
    public void adminCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(ADMIN);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void userCannotAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void canGetUploadsForStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        List<Upload> list = Lists.newArrayList();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockUploadService).getStudyUploads(studyId, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);
        
        Result result = controller.getUploads(startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
        TestUtils.assertResult(result, 200);
        
        verify(mockUploadService).getStudyUploads(studyId, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);
        verify(mockStudyService, never()).getStudy(studyId.toString());
        // in other words, it's the object we mocked out from the service, we were returned the value.
        ForwardCursorPagedResourceList<? extends Upload> retrieved = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), UPLOADS_REF);
        assertNull(retrieved.getRequestParams().get("offsetBy"));
        assertNull(retrieved.getTotal());
        assertEquals(API_MAXIMUM_PAGE_SIZE, retrieved.getRequestParams().get("pageSize"));
        assertEquals(startTime.toString(), retrieved.getRequestParams().get("startTime"));
        assertEquals(endTime.toString(), retrieved.getRequestParams().get("endTime"));
    }

    @Test(expected = BadRequestException.class)
    public void getUploadsForStudyWithNullStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy(null, startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test(expected = BadRequestException.class)
    public void getUploadsForStudyWitEmptyStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy("", startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test(expected = BadRequestException.class)
    public void getUploadsForStudyWithBlankStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy(" ", startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void canGetUploadsForSpecifiedStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        List<Upload> list = Lists.newArrayList();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockUploadService).getStudyUploads(studyId, startTime, endTime, API_MAXIMUM_PAGE_SIZE,
                null);

        Result result = controller.getUploadsForStudy(studyId.getIdentifier(), startTime.toString(), endTime.toString(),
                API_MAXIMUM_PAGE_SIZE, null);
        TestUtils.assertResult(result, 200);

        verify(mockUploadService).getStudyUploads(studyId, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);

        // in other words, it's the object we mocked out from the service, we were returned the value.
        ForwardCursorPagedResourceList<? extends Upload> retrieved = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), UPLOADS_REF);
        assertNull(retrieved.getRequestParams().get("offsetBy"));
        assertNull(retrieved.getTotal());
        assertEquals(API_MAXIMUM_PAGE_SIZE, retrieved.getRequestParams().get("pageSize"));
        assertEquals(startTime.toString(), retrieved.getRequestParams().get("startTime"));
        assertEquals(endTime.toString(), retrieved.getRequestParams().get("endTime"));
    }
    
    @Test
    public void getSummaryStudiesWithFormatWorks() throws Exception {
        List<Study> studies = Lists.newArrayList(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        Result result = controller.getAllStudies("summary", null);
        TestUtils.assertResult(result, 200);
        ResourceList<Study> list = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), new TypeReference<ResourceList<Study>>() {});
        assertTrue((Boolean)list.getRequestParams().get("summary"));

        assertFalse(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));
    }

    @Test
    public void getSummaryStudiesWithSummaryWorks() throws Exception {
        List<Study> studies = Lists.newArrayList(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        Result result = controller.getAllStudies(null, "true");
        TestUtils.assertResult(result, 200);

        assertFalse(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));
    }

    @Test
    public void getSummaryStudiesWithInactiveOnes() throws Exception {
        DynamoStudy testStudy1 = new DynamoStudy();
        testStudy1.setName("test_study_1");
        testStudy1.setActive(true);

        DynamoStudy testStudy2 = new DynamoStudy();
        testStudy2.setName("test_study_2");

        List<Study> studies = Lists.newArrayList(testStudy1, testStudy2);
        doReturn(studies).when(mockStudyService).getStudies();

        Result result = controller.getAllStudies("summary", null);
        TestUtils.assertResult(result, 200);

        // only active studies will be returned
        byte[] body = JavaResultExtractor.getBody(result, 0L);
        JsonNode recordJsonNode = DefaultObjectMapper.INSTANCE.readTree(body);
        JsonNode items = recordJsonNode.get("items");
        assertTrue(items.size() == 1);
        JsonNode study = items.get(0);
        assertEquals("test_study_1", study.get("name").asText());
        assertFalse(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));

        verify(controller, never()).getAuthenticatedSession(ADMIN);
    }
    
    @Test
    public void getFullStudiesWorks() throws Exception {
        List<Study> studies = Lists.newArrayList(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        
        Result result = controller.getAllStudies(null, "false");
        TestUtils.assertResult(result, 200);
        ResourceList<Study> list = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), new TypeReference<ResourceList<Study>>() {});
        assertFalse((Boolean)list.getRequestParams().get("summary"));

        assertTrue(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));
    }
        
    private void testRoleAccessToCurrentStudy(Roles role) throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(role)).build();
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(studyId);
        doReturn(session).when(controller).getSessionIfItExists();
        
        Result result = controller.getCurrentStudy();
        TestUtils.assertResult(result, 200);
        
        Study study = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), Study.class);
        assertEquals(EMAIL_ADDRESS, study.getSupportEmail());        
    }

}
