package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ReportControllerTest {

    private static final String REPORT_ID = "foo";

    private static final String VALID_LANGUAGE_HEADER = "en-US";

    private static final String VALID_USER_AGENT_HEADER = "Unknown Client/14 BridgeJavaSDK/10";

    private static final String OTHER_PARTICIPANT_HEALTH_CODE = "ABC";

    private static final String OTHER_PARTICIPANT_ID = "userId";

    private static final String HEALTH_CODE = "healthCode";
    
    private static final LocalDate START_DATE = LocalDate.parse("2015-01-02");
    
    private static final LocalDate END_DATE = LocalDate.parse("2015-02-02");
    
    @Mock
    ReportService mockReportService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    AccountDao mockAccountDao;
    
    @Mock
    Account mockAccount;
    
    @Mock
    Account mockOtherAccount;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    @Captor
    ArgumentCaptor<ReportIndex> reportDataIndex;
    
    ReportController controller;
    
    UserSession session;
    
    @Before
    public void before() throws Exception {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        
        controller = spy(new ReportController());
        controller.setReportService(mockReportService);
        controller.setStudyService(mockStudyService);
        controller.setAccountDao(mockAccountDao);
        
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        
        doReturn(mockOtherAccount).when(mockAccountDao).getAccount(study, OTHER_PARTICIPANT_ID);
        
        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("GUID"))
                .withConsented(true).withRequired(true).withSignedMostRecentConsent(true).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
        
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        session.setAuthenticated(true);
        session.setConsentStatuses(statuses);
        
        doReturn(study).when(mockStudyService).getStudy(TEST_STUDY);
        doReturn(study).when(mockStudyService).getStudy(TEST_STUDY.getIdentifier());
        doReturn(OTHER_PARTICIPANT_HEALTH_CODE).when(mockOtherAccount).getHealthCode();
        doReturn(HEALTH_CODE).when(mockAccount).getHealthCode();
        doReturn(session).when(controller).getSessionIfItExists();
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
        doReturn(session).when(controller).getAuthenticatedSession(Roles.WORKER);
        
        ReportIndex index = ReportIndex.create();
        index.setIdentifier("fofo");
        ReportTypeResourceList<? extends ReportIndex> list = new ReportTypeResourceList<>(
                Lists.newArrayList(index), ReportType.STUDY);
        doReturn(list).when(mockReportService).getReportIndices(TEST_STUDY, ReportType.STUDY);
        
        index = ReportIndex.create();
        index.setIdentifier("fofo");
        list = new ReportTypeResourceList<>(Lists.newArrayList(index), ReportType.PARTICIPANT);
        doReturn(list).when(mockReportService).getReportIndices(TEST_STUDY, ReportType.PARTICIPANT);
    }
    
    private void setupContext() throws Exception {
        setupContext(VALID_USER_AGENT_HEADER, VALID_LANGUAGE_HEADER);
    }
    
    private void setupContext(String userAgent, String languages) throws Exception {
        Map<String,String[]> headers = Maps.newHashMap();
        headers.put("User-Agent", new String[] {userAgent});
        headers.put("Accept-Language", new String[] {languages});

        TestUtils.mockPlayContextWithJson("{}", headers);
    }
    
    @Test
    public void getParticipantReportData() throws Exception {
        setupContext();
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getStudyIdentifier(),
                REPORT_ID, HEALTH_CODE, START_DATE, END_DATE);
        
        Result result = controller.getParticipantReport(REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertEquals(200, result.status());
        assertResult(result);
    }

    @Test
    public void getParticipantReportDataAsResearcher() throws Exception {
        // No consents so user is not consented, but is a researcher and can also see these reports
        session.setConsentStatuses(Maps.newHashMap());
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        session.setParticipant(participant);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getStudyIdentifier(),
                REPORT_ID, HEALTH_CODE, START_DATE, END_DATE);
        
        Result result = controller.getParticipantReport(REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test
    public void getParticipantReportDataNoDates() throws Exception {
        setupContext();
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getStudyIdentifier(),
                REPORT_ID, HEALTH_CODE, null, null);
        
        Result result = controller.getParticipantReport(REPORT_ID, null, null);
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test
    public void getStudyReportIndexAsDeveloper() throws Exception {
        // Developer is set up in the @Before method, no further changes necessary
        getStudyReportIndex();
    }
    
    @Test
    public void getStudyReportIndexAsResearcher() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.RESEARCHER))
                .build();
        session.setParticipant(participant);
        
        getStudyReportIndex();
    }
    
    @Test(expected = UnauthorizedException.class) 
    public void cannotAccessAsUser() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet()).build();
        session.setParticipant(participant);
        
        getStudyReportIndex();
    }

    private void getStudyReportIndex() throws Exception {
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(REPORT_ID);
        index.setPublic(true);
        index.setKey(REPORT_ID+":STUDY");
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(REPORT_ID)
                .withReportType(ReportType.STUDY)
                .withStudyIdentifier(TEST_STUDY).build();
        
        doReturn(index).when(mockReportService).getReportIndex(key);
        
        Result result = controller.getStudyReportIndex(REPORT_ID);
        assertEquals(200, result.status());
        ReportIndex deserIndex = BridgeObjectMapper.get().readValue(Helpers.contentAsString(result), ReportIndex.class);
        assertEquals(REPORT_ID, deserIndex.getIdentifier());
        assertTrue(deserIndex.isPublic());
        assertNull(deserIndex.getKey()); // isn't returned in API
    }

    @Test
    public void getStudyReportData() throws Exception {
        setupContext();
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                REPORT_ID, START_DATE, END_DATE);
        
        Result result = controller.getStudyReport(REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test
    public void getStudyReportDataWithNoDates() throws Exception {
        setupContext();
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                REPORT_ID, null, null);
        
        Result result = controller.getStudyReport(REPORT_ID, null, null);
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test
    public void getStudyReportDataWithNoUserAgentAsResearcherOK() throws Exception {
        setupContext("", VALID_LANGUAGE_HEADER);
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                REPORT_ID, START_DATE, END_DATE);
        
        controller.getStudyReport(REPORT_ID, START_DATE.toString(), END_DATE.toString());
    }    
    
    @Test
    public void saveParticipantReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.saveParticipantReport(OTHER_PARTICIPANT_ID, REPORT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");

        verify(mockReportService).saveParticipantReport(eq(TEST_STUDY), eq(REPORT_ID), eq(OTHER_PARTICIPANT_HEALTH_CODE), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(LocalDate.parse("2015-02-12").toString(), reportData.getDate().toString());
        assertNull(reportData.getKey());
        assertEquals("Last", reportData.getData().get("field1").asText());
        assertEquals("Name", reportData.getData().get("field2").asText());
    }
    
    // This should be legal
    @Test
    public void saveParticipantEmptyReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{}}");
        
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.saveParticipantReport(OTHER_PARTICIPANT_ID, REPORT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");
    }
    
    @Test
    public void saveParticipantReportForWorker() throws Exception {
        String json = TestUtils.createJson("{'healthCode': '"+OTHER_PARTICIPANT_HEALTH_CODE+"', 'date':'2015-02-12','data':['A','B','C']}");
        
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.saveParticipantReportForWorker(REPORT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");
        
        verify(mockReportService).saveParticipantReport(eq(TEST_STUDY), eq(REPORT_ID), eq(OTHER_PARTICIPANT_HEALTH_CODE), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(LocalDate.parse("2015-02-12").toString(), reportData.getDate().toString());
        assertNull(reportData.getKey());
        assertEquals("A", reportData.getData().get(0).asText());
        assertEquals("B", reportData.getData().get(1).asText());
        assertEquals("C", reportData.getData().get(2).asText());
    }
    
    @Test
    public void saveParticipantReportForWorkerRequiresHealthCode() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':['A','B','C']}");
        
        TestUtils.mockPlayContextWithJson(json);
        try {
            controller.saveParticipantReportForWorker(REPORT_ID);    
        } catch(BadRequestException e) {
            assertEquals("A health code is required to save report data.", e.getMessage());
            verifyNoMoreInteractions(mockReportService);
        }
    }
    
    @Test
    public void saveStudyReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        TestUtils.mockPlayContextWithJson(json);
                
        Result result = controller.saveStudyReport(REPORT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");
        
        verify(mockReportService).saveStudyReport(eq(TEST_STUDY), eq(REPORT_ID), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(LocalDate.parse("2015-02-12").toString(), reportData.getDate().toString());
        assertNull(reportData.getKey());
        assertEquals("Last", reportData.getData().get("field1").asText());
        assertEquals("Name", reportData.getData().get("field2").asText());
    }

    @Test
    public void saveStudyReportForSpecifiedStudyData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.saveStudyReportForSpecifiedStudy(TEST_STUDY.getIdentifier(), REPORT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");

        verify(mockStudyService).getStudy(TEST_STUDY_IDENTIFIER);
        verify(mockReportService).saveStudyReport(eq(TEST_STUDY), eq(REPORT_ID), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(LocalDate.parse("2015-02-12").toString(), reportData.getDate().toString());
        assertNull(reportData.getKey());
        assertEquals("Last", reportData.getData().get("field1").asText());
        assertEquals("Name", reportData.getData().get("field2").asText());
    }

    @Test
    public void getStudyReportIndices() throws Exception {
        Result result = controller.getReportIndices("study");
        assertEquals(200, result.status());
        
        ReportTypeResourceList<ReportIndex> results = BridgeObjectMapper.get().readValue(
                Helpers.contentAsString(result),
                new TypeReference<ReportTypeResourceList<ReportIndex>>() {});
        assertEquals(1, results.getTotal());
        assertEquals(1, results.getItems().size());
        assertEquals(ReportType.STUDY, results.getReportType());
        assertEquals("fofo", results.getItems().get(0).getIdentifier());
        
        verify(mockReportService).getReportIndices(TEST_STUDY, ReportType.STUDY);
    }
    
    @Test
    public void getParticipantReportIndices() throws Exception {
        Result result = controller.getReportIndices("participant");
        assertEquals(200, result.status());
        
        ReportTypeResourceList<ReportIndex> results = BridgeObjectMapper.get().readValue(
                Helpers.contentAsString(result),
                new TypeReference<ReportTypeResourceList<ReportIndex>>() {});
        assertEquals(1, results.getTotal());
        assertEquals(1, results.getItems().size());
        assertEquals(ReportType.PARTICIPANT, results.getReportType());
        assertEquals("fofo", results.getItems().get(0).getIdentifier());
        
        verify(mockReportService).getReportIndices(TEST_STUDY, ReportType.PARTICIPANT);
    }
    
    @Test
    public void deleteParticipantReportData() throws Exception {
        Result result = controller.deleteParticipantReport(OTHER_PARTICIPANT_ID, REPORT_ID);
        TestUtils.assertResult(result, 200, "Report deleted.");
        
        verify(mockReportService).deleteParticipantReport(session.getStudyIdentifier(), REPORT_ID, OTHER_PARTICIPANT_HEALTH_CODE);
    }
    
    @Test
    public void deleteStudyReportData() throws Exception {
        Result result = controller.deleteStudyReport(REPORT_ID);
        TestUtils.assertResult(result, 200, "Report deleted.");
        
        verify(mockReportService).deleteStudyReport(session.getStudyIdentifier(), REPORT_ID);
    }
    
    @Test
    public void deleteParticipantReportDataRecord() throws Exception {
        Result result = controller.deleteParticipantReportRecord(OTHER_PARTICIPANT_ID, REPORT_ID, "2014-05-10");
        TestUtils.assertResult(result, 200, "Report record deleted.");
        
        verify(mockReportService).deleteParticipantReportRecord(session.getStudyIdentifier(), REPORT_ID,
                LocalDate.parse("2014-05-10"), OTHER_PARTICIPANT_HEALTH_CODE);
    }
    
    @Test
    public void deleteStudyReportDataRecord() throws Exception {
        Result result = controller.deleteStudyReportRecord(REPORT_ID, "2014-05-10");
        TestUtils.assertResult(result, 200, "Report record deleted.");
        
        verify(mockReportService).deleteStudyReportRecord(session.getStudyIdentifier(), REPORT_ID,
                LocalDate.parse("2014-05-10"));
    }
    
    @Test(expected = UnauthorizedException.class)
    public void deleteStudyRecordDataRecordDeveloper() {
        StudyParticipant regularUser = new StudyParticipant.Builder().copyOf(session.getParticipant())
            .withRoles(Sets.newHashSet()).build();
        session.setParticipant(regularUser);
        
        controller.deleteStudyReportRecord(REPORT_ID, "2014-05-10");
    }
    
    @Test(expected = UnauthorizedException.class)
    public void deleteParticipantRecordDataRecordDeveloper() {
        StudyParticipant regularUser = new StudyParticipant.Builder().copyOf(session.getParticipant())
            .withRoles(Sets.newHashSet(Roles.ADMIN)).build();
        session.setParticipant(regularUser);
        
        controller.deleteParticipantReportRecord(REPORT_ID, "bar", "2014-05-10");
    }
    
    @Test
    public void adminCanDeleteParticipantIndex() {
        StudyParticipant regularUser = new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(Roles.ADMIN)).build();
            session.setParticipant(regularUser);
        
        controller.deleteParticipantReportIndex(REPORT_ID);
        verify(mockReportService).deleteParticipantReportIndex(TEST_STUDY, REPORT_ID);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void nonAdminCannotDeleteParticipantIndex() throws Exception {
        controller.deleteParticipantReportIndex(REPORT_ID);
    }
    
    @Test
    public void canUpdateStudyReportIndex() throws Exception {
        TestUtils.mockPlayContextWithJson("{\"public\":true}");
        
        Result result = controller.updateStudyReportIndex(REPORT_ID);
        
        verify(mockReportService).updateReportIndex(eq(ReportType.STUDY), reportDataIndex.capture());
        ReportIndex index = reportDataIndex.getValue();
        assertTrue(index.isPublic());
        assertEquals(REPORT_ID, index.getIdentifier());
        assertEquals("api:STUDY", index.getKey());
        
        TestUtils.assertResult(result, 200, "Report index updated.");
    }
    
    @Test
    public void canUpdateParticipantReportIndex() throws Exception {
        TestUtils.mockPlayContextWithJson("{\"public\":true}");
        
        Result result = controller.updateParticipantReportIndex(REPORT_ID);
        
        verify(mockReportService).updateReportIndex(eq(ReportType.PARTICIPANT), reportDataIndex.capture());
        ReportIndex index = reportDataIndex.getValue();
        assertTrue(index.isPublic());
        assertEquals(REPORT_ID, index.getIdentifier());
        assertEquals("api:PARTICIPANT", index.getKey());
        
        TestUtils.assertResult(result, 200, "Report index updated.");
    }
    
    private static final TypeReference<DateRangeResourceList<? extends ReportData>> REPORT_REF = new TypeReference<DateRangeResourceList<? extends ReportData>>() {
    };
    
    @Test
    public void canGetPublicStudyReport() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder().withStudyIdentifier(TEST_STUDY).withIdentifier(REPORT_ID)
                .withReportType(ReportType.STUDY).build();
        
        ReportIndex index = ReportIndex.create();
        index.setPublic(true);
        index.setIdentifier(REPORT_ID);
        doReturn(index).when(mockReportService).getReportIndex(key);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                REPORT_ID, START_DATE, END_DATE);
        
        Result result = controller.getPublicStudyReport(
                TEST_STUDY.getIdentifier(), REPORT_ID, START_DATE.toString(), END_DATE.toString());
        
        assertEquals(200, result.status());
        DateRangeResourceList<? extends ReportData> reportData = BridgeObjectMapper.get()
                .readValue(Helpers.contentAsString(result), REPORT_REF);
        assertEquals(2, reportData.getItems().size());
        
        verify(mockReportService).getReportIndex(key);
        verify(mockReportService).getStudyReport(TEST_STUDY, REPORT_ID, START_DATE, END_DATE);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void missingPublicStudyReturns404() throws Exception {
        controller.getPublicStudyReport(TEST_STUDY.getIdentifier(), "does-not-exist", "2016-05-02", "2016-05-09");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void privatePublicStudyReturns404() throws Exception {
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(REPORT_ID)
                .withReportType(ReportType.STUDY)
                .withStudyIdentifier(TEST_STUDY).build();
        
        ReportIndex index = ReportIndex.create();//reportService.getReportIndex(key);
        index.setPublic(false);
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(REPORT_ID);
        
        doReturn(index).when(mockReportService).getReportIndex(key);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                REPORT_ID, START_DATE, END_DATE);
        
        controller.getPublicStudyReport(TEST_STUDY.getIdentifier(), REPORT_ID, START_DATE.toString(), END_DATE.toString());
    }
    
    private void assertResult(Result result) throws Exception {
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("2015-01-02", node.get("startDate").asText());
        assertEquals("2015-02-02", node.get("endDate").asText());
        assertEquals(2, node.get("total").asInt());
        assertEquals("DateRangeResourceList", node.get("type").asText());
        
        JsonNode child1 = node.get("items").get(0);
        assertEquals("2015-02-10", child1.get("date").asText());
        assertEquals("ReportData", child1.get("type").asText());
        JsonNode child1Data = child1.get("data");
        assertEquals("First", child1Data.get("field1").asText());
        assertEquals("Name", child1Data.get("field2").asText());
        
        JsonNode child2 = node.get("items").get(1);
        assertEquals("2015-02-12", child2.get("date").asText());
        assertEquals("ReportData", child2.get("type").asText());
        JsonNode child2Data = child2.get("data");
        assertEquals("Last", child2Data.get("field1").asText());
        assertEquals("Name", child2Data.get("field2").asText());
    }
    
    private DateRangeResourceList<ReportData> makeResults(LocalDate startDate, LocalDate endDate){
        List<ReportData> list = Lists.newArrayList();
        list.add(createReport(LocalDate.parse("2015-02-10"), "First", "Name"));
        list.add(createReport(LocalDate.parse("2015-02-12"), "Last", "Name"));
        return new DateRangeResourceList<ReportData>(list, startDate, endDate);
    }
    
    private ReportData createReport(LocalDate date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey("foo:" + TEST_STUDY.getIdentifier());
        report.setDate(date);
        report.setData(node);
        return report;
    }
    
}
