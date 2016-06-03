package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportDataType;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ReportServiceTest {

    private static final String IDENTIFIER = "MyTestReport";
    
    private static final String HEALTH_CODE = "healthCode";
    
    private static final LocalDate START_DATE = LocalDate.parse("2015-01-02");
    
    private static final LocalDate END_DATE = LocalDate.parse("2015-02-02");
    
    private static final ReportDataKey STUDY_REPORT_DATA_KEY = new ReportDataKey.Builder()
            .withReportType(ReportDataType.STUDY).withStudyIdentifier(TEST_STUDY).withIdentifier(IDENTIFIER).build();
    
    private static final ReportDataKey PARTICIPANT_REPORT_DATA_KEY = new ReportDataKey.Builder()
            .withReportType(ReportDataType.PARTICIPANT).withStudyIdentifier(TEST_STUDY).withHealthCode(HEALTH_CODE)
            .withIdentifier(IDENTIFIER).build();
    
    private static final ReportData CANNED_REPORT = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
    
    @Mock
    ReportDataDao reportDataDao;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    ReportService service;
    
    DateRangeResourceList<? extends ReportData> results;
    
    @Before
    public void before() throws Exception {
        service = new ReportService();
        service.setReportDataDao(reportDataDao);

        List<ReportData> list = Lists.newArrayList();
        list.add(createReport(LocalDate.parse("2015-02-10"), "First", "Name"));
        list.add(createReport(LocalDate.parse("2015-02-12"), "Last", "Name"));
        results = new DateRangeResourceList<ReportData>(list, START_DATE, END_DATE);
    }
    
    private static ReportData createReport(LocalDate date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey(IDENTIFIER +":" + TEST_STUDY.getIdentifier());
        report.setData(node);
        report.setDate(date);
        return report;
    }
    
    @Test
    public void getStudyReportData() {
        doReturn(results).when(reportDataDao).getReportData(STUDY_REPORT_DATA_KEY, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> retrieved = service.getStudyReport(
                TEST_STUDY, IDENTIFIER, START_DATE, END_DATE);
        
        verify(reportDataDao).getReportData(STUDY_REPORT_DATA_KEY, START_DATE, END_DATE);
        assertEquals(results, retrieved);
    }
    
    @Test
    public void getParticipantReportData() {
        doReturn(results).when(reportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> retrieved = service.getParticipantReport(
                TEST_STUDY, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE);

        verify(reportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, START_DATE, END_DATE);
        assertEquals(results, retrieved);
    }

    @Test
    public void saveStudyReportData() {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        
        service.saveStudyReport(TEST_STUDY, IDENTIFIER, someData);
        
        verify(reportDataDao).saveReportData(reportDataCaptor.capture());
        ReportData retrieved = reportDataCaptor.getValue();
        assertEquals(someData, retrieved);
        assertEquals(STUDY_REPORT_DATA_KEY.toString(), retrieved.getKey());
        assertEquals(LocalDate.parse("2015-02-10"), retrieved.getDate());
        assertEquals("First", retrieved.getData().get("field1").asText());
        assertEquals("Name", retrieved.getData().get("field2").asText());
    }
    
    @Test
    public void saveParticipantReportData() {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        
        service.saveParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, someData);
        
        verify(reportDataDao).saveReportData(reportDataCaptor.capture());
        ReportData retrieved = reportDataCaptor.getValue();
        assertEquals(someData, retrieved);
        assertEquals(PARTICIPANT_REPORT_DATA_KEY.toString(), retrieved.getKey());
        assertEquals(LocalDate.parse("2015-02-10"), retrieved.getDate());
        assertEquals("First", retrieved.getData().get("field1").asText());
        assertEquals("Name", retrieved.getData().get("field2").asText());
    }
    
    @Test
    public void deleteStudyReport() {
        service.deleteStudyReport(TEST_STUDY, IDENTIFIER);
        
        verify(reportDataDao).deleteReport(STUDY_REPORT_DATA_KEY);
    }
    
    @Test
    public void deleteParticipantReport() {
        service.deleteParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE);
        
        verify(reportDataDao).deleteReport(PARTICIPANT_REPORT_DATA_KEY);
    }
    
    // The following are date range tests from MPowerVisualizationService, they should work with this service too
    
    @Test
    public void defaultStartAndEndDates() {
        // mock now
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2016-02-08T09:00-0800").getMillis());
        try {
            service.getParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, null, null);
            
            verify(reportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, LocalDate.parse("2016-02-07"), LocalDate.parse("2016-02-07"));
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test(expected = BadRequestException.class)
    public void startDateAfterEndDate() {
        service.getParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, END_DATE, START_DATE);
    }

    @Test(expected = BadRequestException.class)
    public void dateRangeTooWide() {
        service.getParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, START_DATE, START_DATE.plusDays(46));
    }
    
    // Most of these values, if they're missing, are a programmer error (study and health code are 
    // retrieved from the caller's session, and the ReportData object will exist, even if the JSON 
    // itself holds no data or incorrect data). But identifier is provided from the UI and could in 
    // theory be missing depending on how it is mapped in the routes (as we map it, it must exist for 
    // the route to be matched), or an invalid string. So those cases throw a BadRequestException.
    
    @Test
    public void getStudyReportBadIdentifier() {
        invalidId(() -> service.getStudyReport(TEST_STUDY, "bad identifier", START_DATE, END_DATE),
                "can only contain letters, numbers, underscore and dash");
    }
    
    @Test
    public void getStudyReportDataNoStudy() {
        noAction(() -> service.getStudyReport(null, IDENTIFIER, START_DATE, END_DATE));
    }
    
    @Test
    public void getStudyReportDataNoIdentifier() {
        invalidId(() -> service.getStudyReport(TEST_STUDY, null, START_DATE, END_DATE),
                "cannot be null or blank");
    }
    
    @Test
    public void getParticipantReportDataNoStudy() {
        noAction(() -> service.getParticipantReport(null, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE));
    }

    @Test
    public void getParticipantReportDataNoIdentifier() {
        invalidId(() -> service.getParticipantReport(TEST_STUDY, null, HEALTH_CODE, START_DATE, END_DATE),
                "cannot be null or blank");
    }
    
    @Test
    public void getParticipantReportDataNoHealthCode() {
        noAction(() -> service.getParticipantReport(TEST_STUDY, IDENTIFIER, null, START_DATE, END_DATE));
    }
    
    @Test
    public void saveStudyReportDataNoStudy() {
        noAction(() -> service.saveStudyReport(null, IDENTIFIER, CANNED_REPORT));
    }
    
    @Test
    public void saveStudyReportDataNoIdentifier() {
        invalidId(() -> service.saveStudyReport(TEST_STUDY, null, CANNED_REPORT), "cannot be null or blank");
    }

    @Test
    public void saveStudyReportDataNoData() {
        noAction(() -> service.saveStudyReport(TEST_STUDY, IDENTIFIER, null));
    }

    @Test
    public void saveParticipantReportDataNoStudy() {
        noAction(() -> service.saveParticipantReport(null, IDENTIFIER, HEALTH_CODE, CANNED_REPORT));
    }
    
    @Test
    public void saveParticipantReportDataNoIdentifier() {
        invalidId(() -> service.saveParticipantReport(TEST_STUDY, null, HEALTH_CODE, CANNED_REPORT),
                "cannot be null or blank");
    }
    
    @Test
    public void saveParticipantReportDataNoHealthCode() {
        invalidHealthCode(() -> service.saveParticipantReport(TEST_STUDY, IDENTIFIER, null, CANNED_REPORT),
                "is required for participant reports");
    }

    @Test
    public void saveParticipantReportDataNoData() {
        noAction(() -> service.saveParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, null));
    }
    
    @Test
    public void deleteStudyReportNoStudy() {
        noAction(() -> service.deleteStudyReport(null, IDENTIFIER));
    }
    
    @Test
    public void deleteStudyReportNoIdentifier() {
        invalidId(() -> service.deleteStudyReport(TEST_STUDY, null), "cannot be null or blank");
    }
    
    @Test
    public void deleteParticipantReportNoStudy() {
        noAction(() -> service.deleteParticipantReport(null, IDENTIFIER, HEALTH_CODE));
    }
    
    @Test
    public void deleteParticipantReportNoIdentifier() {
        invalidId(() -> service.deleteParticipantReport(TEST_STUDY, null, HEALTH_CODE), "cannot be null or blank");
    }

    @Test
    public void deleteParticipantReportNoHealthCode() {
        noAction(() -> service.deleteParticipantReport(TEST_STUDY, IDENTIFIER, null));
    }
    
    private void invalidHealthCode(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(reportDataDao);
            String errorMsg = e.getErrors().get("healthCode").get(0);
            assertEquals("healthCode " + message, errorMsg);
            // Also verify that we didn't call the DAO
            verifyNoMoreInteractions(reportDataDao);
        }
    }

    private void invalidId(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(reportDataDao);
            String errorMsg = e.getErrors().get("identifier").get(0);
            assertEquals("identifier " + message, errorMsg);
            // Also verify that we didn't call the DAO
            verifyNoMoreInteractions(reportDataDao);
        }
    }
    
    private void noAction(Runnable runnable) {
        try {
            runnable.run();
        } catch(IllegalArgumentException | NullPointerException e) {
            // verify that we did no work in this case.
            verifyNoMoreInteractions(reportDataDao);
        }
    }
}
