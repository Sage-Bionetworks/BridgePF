package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

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
import org.sagebionetworks.bridge.dynamodb.DynamoReportIndex;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
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
    
    ReportController controller;
    
    UserSession session;
    
    @Before
    public void before() throws Exception {
        DynamoStudy study = new DynamoStudy();
        
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
        doReturn(OTHER_PARTICIPANT_HEALTH_CODE).when(mockOtherAccount).getHealthCode();
        doReturn(HEALTH_CODE).when(mockAccount).getHealthCode();
        doReturn(session).when(controller).getSessionIfItExists();
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
        doReturn(session).when(controller).getAuthenticatedSession(Roles.WORKER);
        
        ReportIndex index = new DynamoReportIndex();
        index.setIdentifier("fofo");
        List<? extends ReportIndex> list = Lists.newArrayList(index);
        
        doReturn(list).when(mockReportService).getReportIndices(eq(TEST_STUDY), any());
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
                "foo", HEALTH_CODE, START_DATE, END_DATE);
        
        Result result = controller.getParticipantReport("foo", START_DATE.toString(), END_DATE.toString());
        assertEquals(200, result.status());
        assertResult(result);
    }

    @Test
    public void getParticipantReportDataAsResearcher() throws Exception {
        // No consents, not consented, but is a researcher... can see these reports, too
        session.setConsentStatuses(Maps.newHashMap());
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        session.setParticipant(participant);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getStudyIdentifier(),
                "foo", HEALTH_CODE, START_DATE, END_DATE);
        
        Result result = controller.getParticipantReport("foo", START_DATE.toString(), END_DATE.toString());
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test
    public void getParticipantReportDataNoDates() throws Exception {
        setupContext();
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getStudyIdentifier(),
                "foo", HEALTH_CODE, null, null);
        
        Result result = controller.getParticipantReport("foo", null, null);
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test(expected = BadRequestException.class)
    public void getParticipantReportNoUserAgent() throws Exception {
        setupContext("Bad Request", VALID_LANGUAGE_HEADER);

        controller.getParticipantReport("foo", START_DATE.toString(), END_DATE.toString());
    }
    
    @Test(expected = BadRequestException.class)
    public void getParticipantReportNoLanguageHeader() throws Exception {
        setupContext(VALID_USER_AGENT_HEADER, null);
        
        controller.getParticipantReport("foo", START_DATE.toString(), END_DATE.toString());
    }
    
    @Test(expected = BadRequestException.class)
    public void getParticipantReportBadLanguageHeader() throws Exception {
        setupContext(VALID_USER_AGENT_HEADER, "bad language header");

        controller.getParticipantReport("foo", START_DATE.toString(), END_DATE.toString());
    }
    
    @Test
    public void getStudyReportData() throws Exception {
        setupContext();
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                "foo", START_DATE, END_DATE);
        
        Result result = controller.getStudyReport("foo", START_DATE.toString(), END_DATE.toString());
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test
    public void getStudyReportDataWithNoDates() throws Exception {
        setupContext();
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getStudyReport(session.getStudyIdentifier(),
                "foo", null, null);
        
        Result result = controller.getStudyReport("foo", null, null);
        assertEquals(200, result.status());
        assertResult(result);
    }
    
    @Test(expected = BadRequestException.class)
    public void getStudyReportDataWithNoUserAgent() throws Exception {
        setupContext("", VALID_LANGUAGE_HEADER);
        
        controller.getStudyReport("foo", null, null);
    }    
    
    @Test
    public void saveParticipantReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.saveParticipantReport("foo", OTHER_PARTICIPANT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");

        verify(mockReportService).saveParticipantReport(eq(TEST_STUDY), eq("foo"), eq(OTHER_PARTICIPANT_HEALTH_CODE), reportDataCaptor.capture());
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

        Result result = controller.saveParticipantReport("foo", OTHER_PARTICIPANT_ID);
        TestUtils.assertResult(result, 201, "Report data saved.");
    }
    
    @Test
    public void saveParticipantReportForWorker() throws Exception {
        String json = TestUtils.createJson("{'healthCode': '"+OTHER_PARTICIPANT_HEALTH_CODE+"', 'date':'2015-02-12','data':['A','B','C']}");
        
        TestUtils.mockPlayContextWithJson(json);

        Result result = controller.saveParticipantReportForWorker("foo");
        TestUtils.assertResult(result, 201, "Report data saved.");
        
        verify(mockReportService).saveParticipantReport(eq(TEST_STUDY), eq("foo"), eq(OTHER_PARTICIPANT_HEALTH_CODE), reportDataCaptor.capture());
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
            controller.saveParticipantReportForWorker("foo");    
        } catch(BadRequestException e) {
            assertEquals("A health code is required to save report data.", e.getMessage());
            verifyNoMoreInteractions(mockReportService);
        }
    }
    
    @Test
    public void saveStudyReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        TestUtils.mockPlayContextWithJson(json);
                
        Result result = controller.saveStudyReport("foo");
        TestUtils.assertResult(result, 201, "Report data saved.");
        
        verify(mockReportService).saveStudyReport(eq(TEST_STUDY), eq("foo"), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(LocalDate.parse("2015-02-12").toString(), reportData.getDate().toString());
        assertNull(reportData.getKey());
        assertEquals("Last", reportData.getData().get("field1").asText());
        assertEquals("Name", reportData.getData().get("field2").asText());
    }
    
    @Test
    public void getStudyReportIndices() throws Exception {
        Result result = controller.getStudyReportIndices();
        assertEquals(200, result.status());
        
        ResourceList<ReportIndex> results = BridgeObjectMapper.get().readValue(
                Helpers.contentAsString(result),
                new TypeReference<ResourceList<ReportIndex>>() {});
        assertEquals(1, results.getTotal());
        assertEquals(1, results.getItems().size());
        assertEquals("fofo", results.getItems().get(0).getIdentifier());
        
        verify(mockReportService).getReportIndices(TEST_STUDY, ReportType.STUDY);
    }
    
    @Test
    public void getParticipantReportIndices() throws Exception {
        Result result = controller.getParticipantReportIndices();
        assertEquals(200, result.status());
        
        ResourceList<ReportIndex> results = BridgeObjectMapper.get().readValue(
                Helpers.contentAsString(result),
                new TypeReference<ResourceList<ReportIndex>>() {});
        assertEquals(1, results.getTotal());
        assertEquals(1, results.getItems().size());
        assertEquals("fofo", results.getItems().get(0).getIdentifier());
        
        verify(mockReportService).getReportIndices(TEST_STUDY, ReportType.PARTICIPANT);
    }
    
    @Test
    public void deleteParticipantReportData() throws Exception {
        Result result = controller.deleteParticipantReport("foo", OTHER_PARTICIPANT_ID);
        TestUtils.assertResult(result, 200, "Report deleted.");
        
        verify(mockReportService).deleteParticipantReport(session.getStudyIdentifier(), "foo", OTHER_PARTICIPANT_HEALTH_CODE);
    }
    
    @Test
    public void deleteStudyReportData() throws Exception {
        Result result = controller.deleteStudyReport("foo");
        TestUtils.assertResult(result, 200, "Report deleted.");
        
        verify(mockReportService).deleteStudyReport(session.getStudyIdentifier(), "foo");
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
