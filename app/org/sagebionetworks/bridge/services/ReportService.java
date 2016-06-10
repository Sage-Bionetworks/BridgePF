package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

@Component
public class ReportService {
    private static final int MAX_RANGE_DAYS = 45;
    
    private ReportDataDao reportDataDao;
    private ReportIndexDao reportIndexDao;
    
    @Autowired
    final void setReportDataDao(ReportDataDao reportDataDao) {
        this.reportDataDao =reportDataDao;
    }
    
    @Autowired
    final void setReportIndexDao(ReportIndexDao reportIndexDao) {
        this.reportIndexDao = reportIndexDao;
    }
    
    public DateRangeResourceList<? extends ReportData> getStudyReport(StudyIdentifier studyId, String identifier,
            LocalDate startDate, LocalDate endDate) {
        // ReportDataKey validates all parameters to this method
        
        startDate = defaultValueToYesterday(startDate);
        endDate = defaultValueToYesterday(endDate);
        validateDateRange(startDate, endDate);

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        return reportDataDao.getReportData(key, startDate, endDate);
    }
    
    public DateRangeResourceList<? extends ReportData> getParticipantReport(StudyIdentifier studyId, String identifier, String healthCode, LocalDate startDate, LocalDate endDate) {
        // ReportDataKey validates all parameters to this method
        
        startDate = defaultValueToYesterday(startDate);
        endDate = defaultValueToYesterday(endDate);
        validateDateRange(startDate, endDate);

        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        return reportDataDao.getReportData(key, startDate, endDate);
    }

    public void saveStudyReport(StudyIdentifier studyId, String identifier, ReportData reportData) {
        checkNotNull(reportData);
        // ReportDataKey validates all other parameters to this method

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        reportData.setKey(key.getKeyString());
        
        reportDataDao.saveReportData(reportData);
        reportIndexDao.addIndex(key);
    }
    
    public void saveParticipantReport(StudyIdentifier studyId, String identifier, String healthCode, ReportData reportData) {
        checkNotNull(reportData);
        // ReportDataKey validates all other parameters to this method
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        reportData.setKey(key.getKeyString());
        
        reportDataDao.saveReportData(reportData);
        reportIndexDao.addIndex(key);
    }
    
    public void deleteStudyReport(StudyIdentifier studyId, String identifier) {
        // ReportDataKey validates all parameters to this method

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        reportDataDao.deleteReportData(key);
        reportIndexDao.removeIndex(key);
    }
    
    public List<? extends ReportIndex> getReportIndices(StudyIdentifier studyId, ReportType reportType) {
        checkNotNull(studyId);
        checkNotNull(reportType);
        
        return reportIndexDao.getIndices(studyId, reportType);
    }
    
    public void deleteParticipantReport(StudyIdentifier studyId, String identifier, String healthCode) {
        // ReportDataKey validates all parameters to this method
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        reportDataDao.deleteReportData(key);
    }
    
    private LocalDate defaultValueToYesterday(LocalDate submittedValue) {
        if (submittedValue == null) {
            return DateUtils.getCurrentCalendarDateInLocalTime().minusDays(1);
        }
        return submittedValue;
    }
    
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date " + startDate + " can't be after end date " + endDate);
        }
        Period dateRange = new Period(startDate, endDate, PeriodType.days());
        if (dateRange.getDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days, startDate=" +
                    startDate + ", endDate=" + endDate);
        }    
    }
}
