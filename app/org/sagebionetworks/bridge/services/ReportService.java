package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class ReportService {
    private static final int MAX_RANGE_DAYS = 45;
    
    private ReportDataDao reportDataDao;
    
    @Autowired
    final void setReportDataDao(ReportDataDao reportDataDao) {
        this.reportDataDao =reportDataDao;
    }
    
    public DateRangeResourceList<? extends ReportData> getStudyReport(StudyIdentifier studyId, String identifier,
            LocalDate startDate, LocalDate endDate) {
        // ReportDataKey validates all parameters to this method
        
        startDate = defaultValueToYesterday(startDate);
        endDate = defaultValueToYesterday(endDate);
        validateDateRange(startDate, endDate);

        ReportDataKey key = makeKey(null, identifier, studyId, ReportType.STUDY);
        return reportDataDao.getReportData(key, startDate, endDate);
    }
    
    public DateRangeResourceList<? extends ReportData> getParticipantReport(StudyIdentifier studyId, String identifier, String healthCode, LocalDate startDate, LocalDate endDate) {
        // ReportDataKey validates all parameters to this method
        
        startDate = defaultValueToYesterday(startDate);
        endDate = defaultValueToYesterday(endDate);
        validateDateRange(startDate, endDate);

        ReportDataKey key = makeKey(healthCode, identifier, studyId, ReportType.PARTICIPANT);
        return reportDataDao.getReportData(key, startDate, endDate);
    }

    public void saveStudyReport(StudyIdentifier studyId, String identifier, ReportData reportData) {
        checkNotNull(reportData);
        // ReportDataKey validates all other parameters to this method

        ReportDataKey key = makeKey(null, identifier, studyId, ReportType.STUDY);
        reportData.setKey(key.getKeyString());
        
        reportDataDao.saveReportData(reportData);
    }
    
    public void saveParticipantReport(StudyIdentifier studyId, String identifier, String healthCode, ReportData reportData) {
        checkNotNull(reportData);
        // ReportDataKey validates all other parameters to this method
        
        ReportDataKey key = makeKey(healthCode, identifier, studyId, ReportType.PARTICIPANT);
        reportData.setKey(key.getKeyString());
        
        reportDataDao.saveReportData(reportData);
    }
    
    public void deleteStudyReport(StudyIdentifier studyId, String identifier) {
        // ReportDataKey validates all parameters to this method

        ReportDataKey key = makeKey(null, identifier, studyId, ReportType.STUDY);
        
        reportDataDao.deleteReportData(key);
    }
    
    public void deleteParticipantReport(StudyIdentifier studyId, String identifier, String healthCode) {
        // ReportDataKey validates all parameters to this method
        
        ReportDataKey key = makeKey(healthCode, identifier, studyId, ReportType.PARTICIPANT);
        
        reportDataDao.deleteReportData(key);
    }
    
    private ReportDataKey makeKey(String healthCode, String identifier, StudyIdentifier studyId, ReportType reportType) {
        return new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(reportType)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
    }
    
    private LocalDate defaultValueToYesterday(LocalDate submittedValue) {
        if (submittedValue == null) {
            return DateUtils.getCurrentCalendarDateInLocalTime().minusDays(1);
        }
        return submittedValue;
    }
    
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("start date " + startDate + " can't be after end date " + endDate);
        }
        Period dateRange = new Period(startDate, endDate, PeriodType.days());
        if (dateRange.getDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days, startDate=" +
                    startDate + ", endDate=" + endDate);
        }    
    }
}
