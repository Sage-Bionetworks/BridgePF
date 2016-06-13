package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoReportDataDaoTest {
    
    private static final LocalDate END_DATE = LocalDate.parse("2016-03-31");

    private static final LocalDate START_DATE = LocalDate.parse("2016-03-29");
    
    @Autowired
    DynamoReportDataDao dao;
    
    private String reportId;
    
    private ReportDataKey reportDataKey;
    
    private ReportDataKey differentReportDataKey;
    
    @Before
    public void before() {
        reportId = TestUtils.randomName(DynamoReportDataDaoTest.class);
        reportDataKey = new ReportDataKey.Builder().withReportType(ReportType.STUDY).withIdentifier(reportId)
                .withStudyIdentifier(TEST_STUDY).build();
    }
    
    @After
    public void after() {
        if (reportDataKey != null) {
            dao.deleteReportData(reportDataKey);    
        }
        if (differentReportDataKey != null) {
            dao.deleteReportData(differentReportDataKey);;
        }
    }
    
    @Test
    public void canCrud() {
        String reportId = TestUtils.randomName(DynamoReportDataDaoTest.class);
        differentReportDataKey = new ReportDataKey.Builder().withReportType(ReportType.STUDY).withIdentifier(reportId)
                .withStudyIdentifier(TEST_STUDY).build();
        
        // A report clearly outside the date range
        ReportData report0 = createReport(LocalDate.parse("2016-02-14"), "g", "h");
        // Two reports that are in the date range, with the right key, that should be returned.
        ReportData report1 = createReport(LocalDate.parse("2016-03-30"), "a", "b");
        ReportData report2 = createReport(LocalDate.parse("2016-03-31"), "c", "d");
        // And this is in the date range, but has a different key.
        ReportData report3 = createReport(LocalDate.parse("2016-03-30"), "e", "f");
        report3.setKey(differentReportDataKey.getKeyString());
        
        // These are not in date order. They should come back in date order.
        dao.saveReportData(report3);
        dao.saveReportData(report2);
        dao.saveReportData(report1);
        dao.saveReportData(report0);
        
        DateRangeResourceList<? extends ReportData> results = dao.getReportData(
                reportDataKey, START_DATE, END_DATE);
        
        assertResourceList(results, 2);
        assertReportDataEqual(report1, results.getItems().get(0));
        assertReportDataEqual(report2, results.getItems().get(1));
        
        dao.deleteReportData(reportDataKey);
        
        results = dao.getReportData(reportDataKey, START_DATE, END_DATE);
        assertResourceList(results, 0);
    }

    @Test
    public void canDeleteSingleStudyRecords() {
        ReportData report1 = createReport(LocalDate.parse("2016-03-30"), "a", "b");
        ReportData report2 = createReport(LocalDate.parse("2016-03-31"), "c", "d");
        
        dao.saveReportData(report1);
        dao.saveReportData(report2);
        assertEquals(2, dao.getReportData(reportDataKey, START_DATE, END_DATE).getTotal());
        
        dao.deleteReportDataRecord(reportDataKey, LocalDate.parse("2016-03-30"));
        assertEquals(1, dao.getReportData(reportDataKey, START_DATE, END_DATE).getTotal());
        
        dao.deleteReportDataRecord(reportDataKey, LocalDate.parse("2016-03-31"));
        assertEquals(0, dao.getReportData(reportDataKey, START_DATE, END_DATE).getTotal());
    }
    
    private ReportData createReport(LocalDate date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey(reportDataKey.getKeyString());
        report.setData(node);
        report.setDate(date);
        return report;
    }
    
    private void assertReportDataEqual(ReportData original, ReportData retrieved) {
        assertEquals(original.getDate(), retrieved.getDate());
        assertEquals(reportDataKey.getKeyString(), retrieved.getKey());
        assertEquals(original.getData().get("field1").asText(), 
                retrieved.getData().get("field1").asText());
        assertEquals(original.getData().get("field2").asText(), 
                retrieved.getData().get("field2").asText());
    }
    
    private void assertResourceList(DateRangeResourceList<? extends ReportData> results, int recordNumber) {
        assertEquals(recordNumber, results.getTotal());
        assertEquals(recordNumber, results.getItems().size());
        assertEquals(START_DATE, results.getStartDate());
        assertEquals(END_DATE, results.getEndDate());
    }
}
