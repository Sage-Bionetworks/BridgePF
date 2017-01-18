package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Component
public class ReportService {
    private static final int MAX_RANGE_DAYS = 45;
    
    // A short-lived cache to prevent repeatedly writing an index on batch jobs
    private static final Cache<String,String> REPORT_INDEX_CACHE = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();
    
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
    
    public ReportIndex getReportIndex(ReportDataKey key) {
        checkNotNull(key);
        
        return reportIndexDao.getIndex(key);
    }
    
    public DateRangeResourceList<? extends ReportData> getStudyReport(StudyIdentifier studyId, String identifier,
            LocalDate startDate, LocalDate endDate) {
        // ReportDataKey validates all parameters to this method
        
        startDate = defaultValueToMinusDays(startDate, 1);
        endDate = defaultValueToMinusDays(endDate, 0);
        validateDateRange(startDate, endDate);

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        return reportDataDao.getReportData(key, startDate, endDate);
    }
    
    public DateRangeResourceList<? extends ReportData> getParticipantReport(StudyIdentifier studyId, String identifier, String healthCode, LocalDate startDate, LocalDate endDate) {
        // ReportDataKey validates all parameters to this method
        
        startDate = defaultValueToMinusDays(startDate, 1);
        endDate = defaultValueToMinusDays(endDate, 0);
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
                .withStudyIdentifier(studyId)
                .validateWithDate(reportData.getDate()).build();
        reportData.setKey(key.getKeyString());
        
        reportDataDao.saveReportData(reportData);
        addToIndex(key);
    }
    
    public void saveParticipantReport(StudyIdentifier studyId, String identifier, String healthCode,
            ReportData reportData) {
        checkNotNull(reportData);
        // ReportDataKey validates all other parameters to this method
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId)
                .validateWithDate(reportData.getDate()).build();
        reportData.setKey(key.getKeyString());
        
        reportDataDao.saveReportData(reportData);
        addToIndex(key);        
    }
    
    public void deleteStudyReport(StudyIdentifier studyId, String identifier) {
        // ReportDataKey validates all parameters to this method

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        reportDataDao.deleteReportData(key);
        REPORT_INDEX_CACHE.invalidate(key.getIndexKeyString());
        reportIndexDao.removeIndex(key);
    }
    
    public void deleteStudyReportRecord(StudyIdentifier studyId, String identifier, LocalDate date) {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId)
                .validateWithDate(date).build();
        
        reportDataDao.deleteReportDataRecord(key, date);
        
        // If this is the last key visible in the window, you can delete the index because this is a study record
        LocalDate startDate = LocalDate.now().minusDays(MAX_RANGE_DAYS);
        LocalDate endDate = LocalDate.now();
        DateRangeResourceList<? extends ReportData> results = getStudyReport(studyId, identifier, startDate, endDate);
        if (results.getItems().isEmpty()) {
            REPORT_INDEX_CACHE.invalidate(key.getIndexKeyString());
            reportIndexDao.removeIndex(key);
        }
    }
    
    public ReportTypeResourceList<? extends ReportIndex> getReportIndices(StudyIdentifier studyId, ReportType reportType) {
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
    
    public void deleteParticipantReportRecord(StudyIdentifier studyId, String identifier, LocalDate date, String healthCode) {
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId)
                .validateWithDate(date).build();
        
        reportDataDao.deleteReportDataRecord(key, date);
    }
    
    public void deleteParticipantReportIndex(StudyIdentifier studyId, String identifier) {
        ReportDataKey key = new ReportDataKey.Builder()
             // force INDEX key to be generated event for participant index (healthCode not relevant for this)
                .withHealthCode("dummy-value") 
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        reportIndexDao.removeIndex(key);
    }
    
    public void updateReportIndex(ReportType reportType, ReportIndex index) {
        if (reportType == ReportType.PARTICIPANT) {
            index.setPublic(false);
        }
        reportIndexDao.updateIndex(index);
    }

    private void addToIndex(ReportDataKey key) {
        try {
            REPORT_INDEX_CACHE.get(key.getIndexKeyString(), () -> {
                reportIndexDao.addIndex(key);
                return key.getIndexKeyString();
            });
        } catch(ExecutionException e) {
            throw new BridgeServiceException(e);
        }
    }
    
    private LocalDate defaultValueToMinusDays(LocalDate submittedValue, int minusDays) {
        if (submittedValue == null) {
            return DateUtils.getCurrentCalendarDateInLocalTime().minusDays(minusDays);
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
