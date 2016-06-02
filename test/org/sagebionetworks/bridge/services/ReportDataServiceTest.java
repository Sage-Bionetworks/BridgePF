package org.sagebionetworks.bridge.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.ReportDataDao;

@RunWith(MockitoJUnitRunner.class)
public class ReportDataServiceTest {

    @Mock
    ReportDataDao reportDataDao;
    
    ReportDataService service;
    
    @Before
    public void before() {
        service = new ReportDataService();
        service.setReportDataDao(reportDataDao);
    }
    
    @Test
    public void getStudyReportData() {
        //service.getStudyReportData()
    }
    
    @Test
    public void getParticipantReportData() {
        //service.getParticipantReportData();
    }

    @Test
    public void saveStudyReportData() {
        //service.saveStudyReportData();
    }
    
    @Test
    public void saveParticipantReportData() {
        //service.saveParticipantReportData()
    }
    
    @Test
    public void deleteStudyReport() {
        // service.deleteStudyReport();
    }
    
    @Test
    public void deleteParticipantReport() {
        // service.deleteParticipantReport
    }
}
