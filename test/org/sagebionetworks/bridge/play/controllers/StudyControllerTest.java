package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DefaultObjectMapper;
import org.sagebionetworks.bridge.models.DateTimeRangeResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.*;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UploadService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.core.j.JavaResultExtractor;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class StudyControllerTest {

    private static final String EMAIL_ADDRESS = "foo@foo.com";

    private static final String PEM_TEXT = "-----BEGIN CERTIFICATE-----\nMIIExDCCA6ygAwIBAgIGBhCnnOuXMA0GCSqGSIb3DQEBBQUAMIGeMQswCQYDVQQG\nEwJVUzELMAkGA1UECAwCV0ExEDAOBgNVBAcMB1NlYXR0bGUxGTAXBgNVBAoMEFNh\nVlOwuuAxumMyIq5W4Dqk8SBcH9Y4qlk7\nEND CERTIFICATE-----";

    private static final TypeReference<DateTimeRangeResourceList<? extends Upload>> UPLOADS_REF = new TypeReference<DateTimeRangeResourceList<? extends Upload>>(){};

    private static final String TEST_PROJECT_ID = "synapseProjectId";
    private static final Long TEST_TEAM_ID = Long.parseLong("123");
    private static final String TEST_USER_ID = "1234";

    private StudyController controller;
    private StudyIdentifier studyId;

    @Mock
    private UserSession mockSession;
    @Mock
    private UploadCertificateService mockUploadCertService;
    @Mock
    private Study mockStudy;
    @Mock
    private StudyService mockStudyService;
    @Mock
    private EmailVerificationService mockVerificationService;
    @Mock
    private CacheProvider mockCacheProvider;
    @Mock
    private UploadService mockUploadService;
    
    private Study study;
    
    @Before
    public void before() throws Exception {
        controller = spy(new StudyController());
        
        // mock session with study identifier
        studyId = new StudyIdentifierImpl(TestConstants.TEST_STUDY_IDENTIFIER + "test");
        when(mockSession.getStudyIdentifier()).thenReturn(studyId);
        when(mockSession.isAuthenticated()).thenReturn(true);
        
        study = new DynamoStudy();
        study.setSupportEmail(EMAIL_ADDRESS);
        study.setIdentifier(studyId.getIdentifier());
        study.setSynapseProjectId(TEST_PROJECT_ID);
        study.setSynapseDataAccessTeamId(TEST_TEAM_ID);
        study.setActive(true);
        
        when(mockStudyService.getStudy(studyId)).thenReturn(study);
        when(mockStudyService.getStudy(studyId.getIdentifier())).thenReturn(study);
        when(mockStudyService.createSynapseProjectTeam(any(), any())).thenReturn(study);

        when(mockVerificationService.getEmailStatus(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);

        mockUploadCertService = mock(UploadCertificateService.class);
        when(mockUploadCertService.getPublicKeyAsPem(any(StudyIdentifier.class))).thenReturn(PEM_TEXT);
        
        controller.setStudyService(mockStudyService);
        controller.setCacheProvider(mockCacheProvider);
        controller.setEmailVerificationService(mockVerificationService);
        controller.setUploadCertificateService(mockUploadCertService);
        controller.setUploadService(mockUploadService);
        
        mockPlayContext();
    }
    
    @Test(expected = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode")
                .withRoles(Sets.newHashSet()).build();
        UserSession session = new UserSession(participant);
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
        session.setAuthenticated(true);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        doReturn(session).when(controller).getSessionIfItExists();

        controller.getUploadsForStudy(studyId.getIdentifier(), startTime.toString(), endTime.toString());
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
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        controller.deleteStudy(study.getIdentifier(), "false");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void cannotDeleteForDeveloper() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        controller.deleteStudy(study.getIdentifier(), "true");
    }

    @Test(expected = EntityNotFoundException.class)
    public void deactivateStudyThrowsGoodException() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).deleteStudy(study.getIdentifier(), false);

        controller.deleteStudy(study.getIdentifier(), "false");
    }

    @Test
    public void canCreateSynapse() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        Result result = controller.createSynapse(TEST_USER_ID);
        String synapseIds = Helpers.contentAsString(result);

        // verify
        verify(mockStudyService).getStudy(eq(studyId));
        verify(mockStudyService).createSynapseProjectTeam(eq(TEST_USER_ID), eq(study));

        JsonNode synapse = BridgeObjectMapper.get().readTree(synapseIds);
        assertEquals(TEST_PROJECT_ID, synapse.get("projectId").asText());
        assertEquals(TEST_TEAM_ID.longValue(), synapse.get("teamId").asLong());
        assertEquals(SynapseProjectIdTeamIdHolder.class.getName(), "org.sagebionetworks.bridge.models.studies." + synapse.get("type").asText());
        assertEquals(201, result.status());
    }

    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode")
                .withRoles(Sets.newHashSet(DEVELOPER)).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        
        Result result = controller.getStudyPublicKeyAsPem();
        String pemFile = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(pemFile);
        assertTrue(node.get("publicKey").asText().contains("-----BEGIN CERTIFICATE-----"));
        assertEquals("CmsPublicKey", node.get("type").asText());
    }
    
    @Test
    public void getEmailStatus() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Result result = controller.getEmailStatus();
        
        verify(mockVerificationService).getEmailStatus(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
    }
    
    @Test
    public void verifyEmail() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockVerificationService.verifyEmailAddress(EMAIL_ADDRESS)).thenReturn(EmailVerificationStatus.VERIFIED);
        
        Result result = controller.verifyEmail();
        
        verify(mockVerificationService).verifyEmailAddress(EMAIL_ADDRESS);
        EmailVerificationStatusHolder status = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result),
                EmailVerificationStatusHolder.class);
        assertEquals(EmailVerificationStatus.VERIFIED, status.getStatus());
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
    
    @Test
    public void canGetUploadsForStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");
        
        DateTimeRangeResourceList<? extends Upload> uploads = new DateTimeRangeResourceList<>(Lists.newArrayList(),
                startTime, endTime);
        doReturn(uploads).when(mockUploadService).getStudyUploads(studyId, startTime, endTime);
        
        Result result = controller.getUploads(startTime.toString(), endTime.toString());
        assertEquals(200, result.status());
        
        verify(mockUploadService).getStudyUploads(studyId, startTime, endTime);
        verify(mockStudyService, never()).getStudy(studyId.toString());
        // in other words, it's the object we mocked out from the service, we were returned the value.
        DateTimeRangeResourceList<? extends Upload> retrieved = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), UPLOADS_REF);
        assertEquals(startTime, retrieved.getStartTime());
        assertEquals(endTime, retrieved.getEndTime());
    }

    @Test(expected = BadRequestException.class)
    public void getUploadsForStudyWithNullStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy(null, startTime.toString(), endTime.toString());
    }

    @Test(expected = BadRequestException.class)
    public void getUploadsForStudyWitEmptyStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy("", startTime.toString(), endTime.toString());
    }

    @Test(expected = BadRequestException.class)
    public void getUploadsForStudyWithBlankStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy(" ", startTime.toString(), endTime.toString());
    }

    @Test
    public void canGetUploadsForSpecifiedStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        DateTimeRangeResourceList<? extends Upload> uploads = new DateTimeRangeResourceList<>(Lists.newArrayList(),
                startTime, endTime);
        doReturn(uploads).when(mockUploadService).getStudyUploads(studyId, startTime, endTime);

        Result result = controller.getUploadsForStudy(studyId.getIdentifier(), startTime.toString(), endTime.toString());
        assertEquals(200, result.status());

        verify(mockUploadService).getStudyUploads(studyId, startTime, endTime);

        // in other words, it's the object we mocked out from the service, we were returned the value.
        DateTimeRangeResourceList<? extends Upload> retrieved = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), UPLOADS_REF);
        assertEquals(startTime, retrieved.getStartTime());
        assertEquals(endTime, retrieved.getEndTime());
    }
    
    @Test
    public void getSummaryStudiesWithFormatWorks() throws Exception {
        List<Study> studies = Lists.newArrayList(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        Result result = controller.getAllStudies("summary", null);
        assertEquals(200, result.status());
        assertFalse(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));

        // Throw an exception if the code makes it this far.
        doThrow(new RuntimeException()).when(controller).getAuthenticatedSession(ADMIN);
    }

    @Test
    public void getSummaryStudiesWithSummaryWorks() throws Exception {
        List<Study> studies = Lists.newArrayList(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        Result result = controller.getAllStudies(null, "true");
        assertEquals(200, result.status());
        assertFalse(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));
        
        // Throw an exception if the code makes it this far.
        doThrow(new RuntimeException()).when(controller).getAuthenticatedSession(ADMIN);
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
        assertEquals(200, result.status());
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
        assertEquals(200, result.status());
        assertTrue(Helpers.contentAsString(result).contains("healthCodeExportEnabled"));
    }
        
    private void testRoleAccessToCurrentStudy(Roles role) throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(role)).build();
        UserSession session = new UserSession(participant);
        session.setAuthenticated(true);
        session.setStudyIdentifier(studyId);
        doReturn(session).when(controller).getSessionIfItExists();
        
        Result result = controller.getCurrentStudy();
        assertEquals(200, result.status());
        
        Study study = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), Study.class);
        assertEquals(EMAIL_ADDRESS, study.getSupportEmail());        
    }

}
