package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.StudyService;

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
    ParticipantService mockParticipantService;
    
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
        controller.setParticipantService(mockParticipantService);
        
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        
        StudyParticipant otherParticipant = new StudyParticipant.Builder().withHealthCode(OTHER_PARTICIPANT_HEALTH_CODE)
                .build();
        
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_STUDY);
        session.setAuthenticated(true);
        
        doReturn(study).when(mockStudyService).getStudy(TEST_STUDY);
        doReturn(otherParticipant).when(mockParticipantService).getParticipant(study, OTHER_PARTICIPANT_ID, false);
        doReturn(session).when(controller).getSessionIfItExists();
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
    }
    
    private void setupContext() throws Exception {
        setupContext("Unknown Client/14 BridgeJavaSDK/10", "en-US");
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
        setupContext("Bad Request", "en-US");

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
        setupContext("", "en-US");
        
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
    public void deleteParticipantReportData() throws Exception {
        controller.deleteParticipantReport("foo", OTHER_PARTICIPANT_ID);
        
        verify(mockReportService).deleteParticipantReport(session.getStudyIdentifier(), "foo", OTHER_PARTICIPANT_HEALTH_CODE);
    }
    
    @Test
    public void deleteStudyReportData() throws Exception {
        controller.deleteStudyReport("foo");
        
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
