package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
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
import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.dynamodb.DynamoReportIndex;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ReportServiceTest {

    private static final String IDENTIFIER = "MyTestReport";
    
    private static final String HEALTH_CODE = "healthCode";
    
    private static final LocalDate START_DATE = LocalDate.parse("2015-01-02");
    
    private static final LocalDate END_DATE = LocalDate.parse("2015-02-02");
    
    private static final LocalDate DATE = LocalDate.parse("2015-02-01");
    
    private static final ReportDataKey STUDY_REPORT_DATA_KEY = new ReportDataKey.Builder()
            .withReportType(ReportType.STUDY).withStudyIdentifier(TEST_STUDY).withIdentifier(IDENTIFIER).build();
    
    private static final ReportDataKey PARTICIPANT_REPORT_DATA_KEY = new ReportDataKey.Builder()
            .withReportType(ReportType.PARTICIPANT).withStudyIdentifier(TEST_STUDY).withHealthCode(HEALTH_CODE)
            .withIdentifier(IDENTIFIER).build();
    
    private static final ReportData CANNED_REPORT = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
    
    @Mock
    ReportDataDao mockReportDataDao;
    
    @Mock
    ReportIndexDao mockReportIndexDao;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    @Captor
    ArgumentCaptor<ReportIndex> reportIndexCaptor;
    
    @Captor
    ArgumentCaptor<ReportDataKey> reportDataKeyCaptor;
    
    ReportService service;
    
    DateRangeResourceList<? extends ReportData> results;
    
    ReportTypeResourceList<? extends ReportIndex> indices;
    
    @Before
    public void before() throws Exception {
        service = new ReportService();
        service.setReportDataDao(mockReportDataDao);
        service.setReportIndexDao(mockReportIndexDao);

        List<ReportData> list = Lists.newArrayList();
        list.add(createReport(LocalDate.parse("2015-02-10"), "First", "Name"));
        list.add(createReport(LocalDate.parse("2015-02-12"), "Last", "Name"));
        results = new DateRangeResourceList<ReportData>(list, START_DATE, END_DATE);
        
        DynamoReportIndex index = new DynamoReportIndex();
        index.setIdentifier(IDENTIFIER);
        indices = new ReportTypeResourceList<>(Lists.newArrayList(index), ReportType.STUDY);
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
        doReturn(results).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> retrieved = service.getStudyReport(
                TEST_STUDY, IDENTIFIER, START_DATE, END_DATE);
        
        verify(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, START_DATE, END_DATE);
        assertEquals(results, retrieved);
    }

    @Captor
    private ArgumentCaptor<LocalDate> localDateCaptor;
    
    @Test
    public void getStudyReportDataNoDates() {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05T12:00:00.000Z").getMillis());
        try {
            LocalDate yesterday = LocalDate.parse("2015-05-04");
            
            doReturn(results).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, yesterday, yesterday);
            
            DateRangeResourceList<? extends ReportData> retrieved = service.getStudyReport(
                    TEST_STUDY, IDENTIFIER, null, null);
            
            verify(mockReportDataDao).getReportData(eq(STUDY_REPORT_DATA_KEY), localDateCaptor.capture(),
                    localDateCaptor.capture());
            assertEquals(yesterday, localDateCaptor.getAllValues().get(0));
            assertEquals(yesterday, localDateCaptor.getAllValues().get(1));
            assertEquals(results, retrieved);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void getParticipantReportData() {
        doReturn(results).when(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> retrieved = service.getParticipantReport(
                TEST_STUDY, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE);

        verify(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, START_DATE, END_DATE);
        assertEquals(results, retrieved);
    }

    @Test
    public void getParticipantReportDataNoDates() {
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05T12:00:00.000Z").getMillis());
        try {
            LocalDate yesterday = LocalDate.parse("2015-05-04");
            
            doReturn(results).when(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, yesterday, yesterday);
            
            DateRangeResourceList<? extends ReportData> retrieved = service.getParticipantReport(
                    TEST_STUDY, IDENTIFIER, HEALTH_CODE, null, null);
            
            verify(mockReportDataDao).getReportData(eq(PARTICIPANT_REPORT_DATA_KEY), localDateCaptor.capture(),
                    localDateCaptor.capture());
            assertEquals(yesterday, localDateCaptor.getAllValues().get(0));
            assertEquals(yesterday, localDateCaptor.getAllValues().get(1));
            assertEquals(results, retrieved);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void saveStudyReportData() {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        
        // Calling twice, the report DAO will be called twice, but the index DAO will be 
        // called once (it caches for a minute)
        service.saveStudyReport(TEST_STUDY, IDENTIFIER, someData);
        service.saveStudyReport(TEST_STUDY, IDENTIFIER, someData);
        
        verify(mockReportDataDao, times(2)).saveReportData(reportDataCaptor.capture());
        ReportData retrieved = reportDataCaptor.getValue();
        assertEquals(someData, retrieved);
        assertEquals(STUDY_REPORT_DATA_KEY.getKeyString(), retrieved.getKey());
        assertEquals(LocalDate.parse("2015-02-10"), retrieved.getDate());
        assertEquals("First", retrieved.getData().get("field1").asText());
        assertEquals("Name", retrieved.getData().get("field2").asText());
        
        verify(mockReportIndexDao, times(1)).addIndex(new ReportDataKey.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withReportType(ReportType.STUDY)
                .withIdentifier(IDENTIFIER).build());
    }
    
    @Test
    public void saveParticipantReportData() throws Exception {
        ReportData someData = createReport(LocalDate.parse("2015-02-10"), "First", "Name");
        
        // Calling twice, the report DAO will be called twice, but the index DAO will be 
        // called once (it caches for a minute)
        service.saveParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, someData);
        service.saveParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, someData);

        verify(mockReportDataDao, times(2)).saveReportData(reportDataCaptor.capture());
        ReportData retrieved = reportDataCaptor.getValue();
        assertEquals(someData, retrieved);
        assertEquals(PARTICIPANT_REPORT_DATA_KEY.getKeyString(), retrieved.getKey());
        assertEquals(LocalDate.parse("2015-02-10"), retrieved.getDate());
        assertEquals("First", retrieved.getData().get("field1").asText());
        assertEquals("Name", retrieved.getData().get("field2").asText());
        
        verify(mockReportIndexDao, times(1)).addIndex(new ReportDataKey.Builder()
                .withHealthCode(HEALTH_CODE)
                .withStudyIdentifier(TEST_STUDY)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(IDENTIFIER).build());
    }
    
    @Test
    public void deleteStudyReport() {
        service.deleteStudyReport(TEST_STUDY, IDENTIFIER);
        
        verify(mockReportDataDao).deleteReportData(STUDY_REPORT_DATA_KEY);
        verify(mockReportIndexDao).removeIndex(STUDY_REPORT_DATA_KEY);
    }
    
    @Test
    public void deleteParticipantReport() {
        service.deleteParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE);
        
        verify(mockReportDataDao).deleteReportData(PARTICIPANT_REPORT_DATA_KEY);
        verifyNoMoreInteractions(mockReportIndexDao);
    }
    
    
    @Test
    public void deleteParticipantIndex() {
        service.deleteParticipantReportIndex(TEST_STUDY, IDENTIFIER);
        
        verify(mockReportIndexDao).removeIndex(reportDataKeyCaptor.capture());
        verifyNoMoreInteractions(mockReportDataDao);
        
        ReportDataKey key = reportDataKeyCaptor.getValue();
        assertEquals(TEST_STUDY, key.getStudyId());
        assertEquals(IDENTIFIER, key.getIdentifier());
    }
    
    @Test
    public void deleteStudyReportRecordNoIndexCleanup() {
        LocalDate startDate = LocalDate.parse("2015-05-05").minusDays(45);
        LocalDate endDate = LocalDate.parse("2015-05-05");
        doReturn(results).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
        
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05").getMillis());
        try {
            service.deleteStudyReportRecord(TEST_STUDY, IDENTIFIER, DATE);
            
            verify(mockReportDataDao).deleteReportDataRecord(STUDY_REPORT_DATA_KEY, DATE);
            verify(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
            verifyNoMoreInteractions(mockReportIndexDao);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void deleteStudyReportRecord() {
        LocalDate startDate = LocalDate.parse("2015-05-05").minusDays(45);
        LocalDate endDate = LocalDate.parse("2015-05-05");
        DateRangeResourceList<ReportData> emptyResults = new DateRangeResourceList<ReportData>(Lists.newArrayList(), START_DATE, END_DATE);
        doReturn(emptyResults).when(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
        
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05").getMillis());
        try {
            service.deleteStudyReportRecord(TEST_STUDY, IDENTIFIER, DATE);
            
            verify(mockReportDataDao).deleteReportDataRecord(STUDY_REPORT_DATA_KEY, DATE);
            verify(mockReportDataDao).getReportData(STUDY_REPORT_DATA_KEY, startDate, endDate);
            verify(mockReportIndexDao).removeIndex(STUDY_REPORT_DATA_KEY);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    @Test
    public void deleteParticipantReportRecord() {
        LocalDate startDate = LocalDate.parse("2015-05-05").minusDays(45);
        LocalDate endDate = LocalDate.parse("2015-05-05");
        doReturn(results).when(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, startDate, endDate);
        
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2015-05-05").getMillis());
        try {
            service.deleteParticipantReportRecord(TEST_STUDY, IDENTIFIER, DATE, HEALTH_CODE);

            verify(mockReportDataDao).deleteReportDataRecord(PARTICIPANT_REPORT_DATA_KEY, DATE);
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }
    
    // The following are date range tests from MPowerVisualizationService, they should work with this service too
    
    @Test
    public void defaultStartAndEndDates() {
        // mock now
        DateTimeUtils.setCurrentMillisFixed(DateTime.parse("2016-02-08T09:00-0800").getMillis());
        try {
            service.getParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, null, null);
            
            verify(mockReportDataDao).getReportData(PARTICIPANT_REPORT_DATA_KEY, LocalDate.parse("2016-02-07"), LocalDate.parse("2016-02-07"));
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
    }

    @Test(expected = BadRequestException.class)
    public void startDateAfterEndDateParticipant() {
        service.getParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, END_DATE, START_DATE);
    }

    @Test(expected = BadRequestException.class)
    public void dateRangeTooWideParticipant() {
        service.getParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, START_DATE, START_DATE.plusDays(46));
    }
    
    @Test(expected = BadRequestException.class)
    public void startDateAfterEndDateStudy() {
        service.getStudyReport(TEST_STUDY, IDENTIFIER, END_DATE, START_DATE);
    }

    @Test(expected = BadRequestException.class)
    public void dateRangeTooWideStudy() {
        service.getStudyReport(TEST_STUDY, IDENTIFIER, START_DATE, START_DATE.plusDays(46));
    }
    
    // Verify that validation errors occur in the service and that nothing is changed in persistence.
    
    @Test
    public void getStudyReportBadIdentifier() {
        invalid(() -> service.getStudyReport(TEST_STUDY, "bad identifier", START_DATE, END_DATE),
                "identifier", "can only contain letters, numbers, underscore and dash");
    }
    
    @Test
    public void getStudyReportDataNoStudy() {
        invalid(() -> service.getStudyReport(null, IDENTIFIER, START_DATE, END_DATE),
                "studyId", "is required");
    }
    
    @Test
    public void getStudyReportDataNoIdentifier() {
        invalid(() -> service.getStudyReport(TEST_STUDY, null, START_DATE, END_DATE),
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void getParticipantReportDataNoStudy() {
        invalid(() -> service.getParticipantReport(null, IDENTIFIER, HEALTH_CODE, START_DATE, END_DATE),
                "studyId", "is required");
    }

    @Test
    public void getParticipantReportDataNoIdentifier() {
        invalid(() -> service.getParticipantReport(TEST_STUDY, null, HEALTH_CODE, START_DATE, END_DATE),
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void getParticipantReportDataNoHealthCode() {
        invalid(() -> service.getParticipantReport(TEST_STUDY, IDENTIFIER, null, START_DATE, END_DATE),
                "healthCode", "is required for participant reports");
    }
    
    @Test
    public void saveStudyReportDataNoStudy() {
        invalid(() -> service.saveStudyReport(null, IDENTIFIER, CANNED_REPORT),
                "studyId", "is required");
    }
    
    @Test
    public void saveStudyReportDataNoIdentifier() {
        invalid(() -> service.saveStudyReport(TEST_STUDY, null, CANNED_REPORT), 
                "identifier", "cannot be missing or blank");
    }

    @Test
    public void saveStudyReportDataNoData() {
        checkNull(() -> service.saveStudyReport(TEST_STUDY, IDENTIFIER, null));
    }

    @Test
    public void saveParticipantReportDataNoStudy() {
        invalid(() -> service.saveParticipantReport(null, IDENTIFIER, HEALTH_CODE, CANNED_REPORT),
                "studyId", "is required");
    }
    
    @Test
    public void saveParticipantReportDataNoIdentifier() {
        invalid(() -> service.saveParticipantReport(TEST_STUDY, null, HEALTH_CODE, CANNED_REPORT),
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void saveParticipantReportDataNoHealthCode() {
        invalid(() -> service.saveParticipantReport(TEST_STUDY, IDENTIFIER, null, CANNED_REPORT),
                "healthCode", "is required for participant reports");
    }

    @Test
    public void saveParticipantReportDataNoData() {
        checkNull(() -> service.saveParticipantReport(TEST_STUDY, IDENTIFIER, HEALTH_CODE, null));
    }
    
    @Test
    public void deleteStudyReportNoStudy() {
        invalid(() -> service.deleteStudyReport(null, IDENTIFIER),
                "studyId", "is required");
    }
    
    @Test
    public void deleteStudyReportNoIdentifier() {
        invalid(() -> service.deleteStudyReport(TEST_STUDY, null), 
                "identifier", "cannot be missing or blank");
    }
    
    @Test
    public void deleteParticipantReportNoStudy() {
        invalid(() -> service.deleteParticipantReport(null, IDENTIFIER, HEALTH_CODE), 
                "studyId", "is required");
    }
    
    @Test
    public void deleteParticipantReportNoIdentifier() {
        invalid(() -> service.deleteParticipantReport(TEST_STUDY, null, HEALTH_CODE), 
                "identifier", "cannot be missing or blank");
    }

    @Test
    public void deleteParticipantReportNoHealthCode() {
        invalid(() -> service.deleteParticipantReport(TEST_STUDY, IDENTIFIER, null),
                "healthCode", "is required for participant reports");
    }
    
    @Test
    public void getStudyIndices() {
        doReturn(indices).when(mockReportIndexDao).getIndices(TEST_STUDY, ReportType.STUDY);

        ReportTypeResourceList<? extends ReportIndex> indices = service.getReportIndices(TEST_STUDY, ReportType.STUDY);
        
        assertEquals(IDENTIFIER, indices.getItems().get(0).getIdentifier());
        assertEquals(ReportType.STUDY, indices.getReportType());
        verify(mockReportIndexDao).getIndices(TEST_STUDY, ReportType.STUDY);
    }
    
    @Test
    public void getParticipantIndices() {
        // Need to create an index list with ReportType.PARTICIPANT for this test
        DynamoReportIndex index = new DynamoReportIndex();
        index.setIdentifier(IDENTIFIER);
        indices = new ReportTypeResourceList<>(Lists.newArrayList(index), ReportType.PARTICIPANT);
        
        doReturn(indices).when(mockReportIndexDao).getIndices(TEST_STUDY, ReportType.PARTICIPANT);

        ReportTypeResourceList<? extends ReportIndex> indices = service.getReportIndices(TEST_STUDY, ReportType.PARTICIPANT);
        
        assertEquals(IDENTIFIER, indices.getItems().get(0).getIdentifier());
        assertEquals(ReportType.PARTICIPANT, indices.getReportType());
        verify(mockReportIndexDao).getIndices(TEST_STUDY, ReportType.PARTICIPANT);
    }
    
    private void invalid(Runnable runnable, String fieldName, String message) {
        try {
            runnable.run();
        } catch(InvalidEntityException e) {
            verifyNoMoreInteractions(mockReportDataDao);
            String errorMsg = e.getErrors().get(fieldName).get(0);
            assertEquals(fieldName + " " + message, errorMsg);
            // Also verify that we didn't call the DAO
            verifyNoMoreInteractions(mockReportDataDao);
        }
    }
    
    private void checkNull(Runnable runnable) {
        try {
            runnable.run();
        } catch(NullPointerException e) {
            // verify that we did no work in this case.
            verifyNoMoreInteractions(mockReportDataDao);
        }
    }
}
