package org.sagebionetworks.bridge.services;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class ReportDataService {

    private ReportDataDao reportDataDao;
    
    @Autowired
    final void setReportDataDao(ReportDataDao reportDataDao) {
        this.reportDataDao =reportDataDao;
    }
    
    public DateRangeResourceList<? extends ReportData> getStudyReportData(StudyIdentifier studyId, String identifier, LocalDate startDate, LocalDate endDate) {
        ReportDataKey key = makeKey(null, identifier, studyId);

        return reportDataDao.getReportData(key, startDate, endDate);
    }
    
    public DateRangeResourceList<? extends ReportData> getParticipantReportData(StudyIdentifier studyId, String identifier, String healthCode, LocalDate startDate, LocalDate endDate) {
        ReportDataKey key = makeKey(healthCode, identifier, studyId);
        
        return reportDataDao.getReportData(key, startDate, endDate);
    }

    public void saveStudyReportData(StudyIdentifier studyId, String identifier, ReportData reportData) {
        ReportDataKey key = makeKey(null, identifier, studyId);
        reportData.setKey(key.toString());
        
        reportDataDao.saveReportData(reportData);
    }
    
    public void saveParticipantReportData(StudyIdentifier studyId, String healthCode, String identifier, ReportData reportData) {
        ReportDataKey key = makeKey(healthCode, identifier, studyId);
        reportData.setKey(key.toString());
        
        reportDataDao.saveReportData(reportData);
    }
    
    public void deleteStudyReport(StudyIdentifier studyId, String identifier) {
        ReportDataKey key = makeKey(null, identifier, studyId);
        
        reportDataDao.deleteReport(key);
    }
    
    public void deleteParticipantReport(StudyIdentifier studyId, String identifier, String healthCode) {
        ReportDataKey key = makeKey(healthCode, identifier, studyId);
        
        reportDataDao.deleteReport(key);
    }
    
    private ReportDataKey makeKey(String healthCode, String identifier, StudyIdentifier studyId) {
        return new ReportDataKey.Builder().withHealthCode(healthCode)
                .withIdentifier(identifier).withStudyIdentifier(studyId).build();
    }
}
