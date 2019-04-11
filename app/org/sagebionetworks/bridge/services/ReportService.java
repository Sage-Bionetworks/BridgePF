package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

import java.util.Set;

import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ReportDataDao;
import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RangeTuple;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.ReportDataKeyValidator;
import org.sagebionetworks.bridge.validators.ReportDataValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * <p>A service for creating and retrieving reports for either participants or studies. A given report can have records
 * that are saved given either a LocalDate or DateTime value, but all the records in a given report (represented by a
 * single report index) should use the same timestamp value, and not mix the two types.</p> 
 * 
 * <p>Some methods in this service will enforce specific substudy permissions in the following manner. If the caller or 
 * the index for a given report have no substudy memberships, the call is allowed. If both have substudy memberships, 
 * then the caller must have at least one substudy in common with the report in order for the method to succeed. Because 
 * the identifiers for these reports are scoped by study, not by substudy, all users can see all the indices for all 
 * reports, even if they cannot retrieve individual records (this is needed to provide information on potential 
 * conflicts).</p>
 */
@Component
public class ReportService {
    private static final int MAX_RANGE_DAYS = 45;
    
    private static final String RECORD_DATE_MISSING_MSG = "Date of report record is required";
    
    private static final String EITHER_BOTH_DATES_OR_NEITHER = "Only one date of a date range provided (both startTime and endTime required)";

    private static final String AMBIGUOUS_TIMEZONE = "startTime and endTime must be in the same time zone";
    
    private static final String INVALID_TIME_RANGE = "startTime later in time than endTime";
    
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
    
    /**
     * Get a report index. Substudy memberships are ignored.
     */
    public ReportIndex getReportIndex(ReportDataKey key) {
        checkNotNull(key);
        
        return reportIndexDao.getIndex(key);
    }
    
    /**
     * Return set of study report records based on the provided local date range. Substudy memberships are enforced.
     */
    public DateRangeResourceList<? extends ReportData> getStudyReport(StudyIdentifier studyId, String identifier,
            LocalDate startDate, LocalDate endDate) {
        
        RangeTuple<LocalDate> finalDates = validateLocalDateRange(startDate, endDate);
        startDate = finalDates.getStart();
        endDate = finalDates.getEnd();

        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }
        return reportDataDao.getReportData(key, startDate, endDate);
    }
    
    /**
     * Return set of participant report records based on the provided local date range. Substudy memberships are
     * enforced.
     */
    public DateRangeResourceList<? extends ReportData> getParticipantReport(StudyIdentifier studyId, String identifier,
            String healthCode, LocalDate startDate, LocalDate endDate) {
        
        RangeTuple<LocalDate> finalDates = validateLocalDateRange(startDate, endDate);
        startDate = finalDates.getStart();
        endDate = finalDates.getEnd();
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }
        return reportDataDao.getReportData(key, startDate, endDate);
    }
    
    /**
     * Return set of participant report records based on the provided datetime range. Substudy memberships are enforced.
     */
    public ForwardCursorPagedResourceList<ReportData> getParticipantReportV4(final StudyIdentifier studyId,
            final String identifier, final String healthCode, final DateTime startTime, final DateTime endTime,
            final String offsetKey, final int pageSize) {
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        RangeTuple<DateTime> finalTimes = validateDateTimeRange(startTime, endTime);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }
        return reportDataDao.getReportDataV4(key, finalTimes.getStart(), finalTimes.getEnd(), offsetKey, pageSize);
    }
    
    /**
     * Return set of study report records based on the provided datetime range. Substudy memberships are enforced.
     */
    public ForwardCursorPagedResourceList<ReportData> getStudyReportV4(final StudyIdentifier studyId,
            final String identifier, final DateTime startTime, final DateTime endTime, final String offsetKey,
            final int pageSize) {
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        RangeTuple<DateTime> finalTimes = validateDateTimeRange(startTime, endTime);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }
        return reportDataDao.getReportDataV4(key, finalTimes.getStart(), finalTimes.getEnd(), offsetKey, pageSize);
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }

    /**
     * Save a study report record. If this is the first record for this report, the data can contain one or more
     * substudies defining who can see this report. The user can submit any substudies regardless of membership (if 
     * the user locks themselves out of the study we do not prevent it). On subsequent saves, the substudy 
     * memberships will be enforced based on the existing report index.
     */
    public void saveStudyReport(StudyIdentifier studyId, String identifier, ReportData reportData) {
        checkNotNull(reportData);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        reportData.setReportDataKey(key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }
        
        ReportDataValidator validator = new ReportDataValidator(index);
        Validate.entityThrowingException(validator, reportData);
        
        reportDataDao.saveReportData(reportData);
        if (index == null) {
            addToIndex(key, reportData.getSubstudyIds());    
        }
    }
    
    /**
     * Save a participant report record. If this is the first record for this report, the data can contain one 
     * or more substudies defining who can see this report. The substudies can be any substudy if the caller has 
     * no substudy memberships, or it must be a subset of the substudies assigned to the caller. If it is a 
     * subsequent record, then substudy memberships will be enforced based on the existing report index.
     */
    public void saveParticipantReport(StudyIdentifier studyId, String identifier, String healthCode,
            ReportData reportData) {
        checkNotNull(reportData);
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        reportData.setReportDataKey(key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }
        
        ReportDataValidator validator = new ReportDataValidator(index);
        Validate.entityThrowingException(validator, reportData);

        reportDataDao.saveReportData(reportData);
        if (index == null) {
            addToIndex(key, reportData.getSubstudyIds());    
        }
    }
    
    /**
     * Delete all records for a study report. Substudy memberships will be enforced.
     */
    public void deleteStudyReport(StudyIdentifier studyId, String identifier) {
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }        
        reportDataDao.deleteReportData(key);
        reportIndexDao.removeIndex(key);
    }
    
    /**
     * Delete one record of a study report. Substudy memberships will be enforced.
     */
    public void deleteStudyReportRecord(StudyIdentifier studyId, String identifier, String date) {
        if (StringUtils.isBlank(date)) {
            throw new BadRequestException(RECORD_DATE_MISSING_MSG);
        }
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }        
        reportDataDao.deleteReportDataRecord(key, date);
        
        // If this is the last key visible in the window, you can delete the index because this is a study record
        LocalDate startDate = LocalDate.now().minusDays(MAX_RANGE_DAYS);
        LocalDate endDate = LocalDate.now();
        DateRangeResourceList<? extends ReportData> results = getStudyReport(studyId, identifier, startDate, endDate);
        if (results.getItems().isEmpty()) {
            reportIndexDao.removeIndex(key);
        }
    }
    
    /**
     * Return all report indices for the supplied type (participant or study). No substudy memberships are enforced.
     */
    public ReportTypeResourceList<? extends ReportIndex> getReportIndices(StudyIdentifier studyId, ReportType reportType) {
        checkNotNull(studyId);
        checkNotNull(reportType);
        
        return reportIndexDao.getIndices(studyId, reportType);
    }
    
    /**
     * Delete all records of a participant report. Substudy memberships are enforced. 
     */
    public void deleteParticipantReport(StudyIdentifier studyId, String identifier, String healthCode) {
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }        
        reportDataDao.deleteReportData(key);
    }
    
    /**
     * Delete one record of a participant report. Substudy memberships are enforced. 
     */
    public void deleteParticipantReportRecord(StudyIdentifier studyId, String identifier, String date, String healthCode) {
        if (StringUtils.isBlank(date)) {
            throw new BadRequestException(RECORD_DATE_MISSING_MSG);
        }
        
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(healthCode)
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }        
        reportDataDao.deleteReportDataRecord(key, date);
    }
    
    /**
     * Delete a participant report index. Substudy memberships are enforced. Typically we do not automatically 
     * delete these because we cannot determine all individual records have been deleted without a table scan, 
     * but this method is provided for tests. 
     */
    public void deleteParticipantReportIndex(StudyIdentifier studyId, String identifier) {
        ReportDataKey key = new ReportDataKey.Builder()
             // force INDEX key to be generated event for participant index (healthCode not relevant for this)
                .withHealthCode("dummy-value") 
                .withReportType(ReportType.PARTICIPANT)
                .withIdentifier(identifier)
                .withStudyIdentifier(studyId).build();
        
        ReportIndex index = reportIndexDao.getIndex(key);
        if (!canAccess(index)) {
            throw new UnauthorizedException();
        }        
        reportIndexDao.removeIndex(key);
    }

    /**
     * Update a report index. Substudy memberships are enforced. Only a user who is not associated to any substudies 
     * may change the substudy associations of the report index.
     */
    public void updateReportIndex(StudyIdentifier studyId, ReportType reportType, ReportIndex index) {
        if (reportType == ReportType.PARTICIPANT) {
            index.setPublic(false);
        }
        ReportDataKey key = new ReportDataKey.Builder()
                .withReportType(ReportType.STUDY)
                .withIdentifier(index.getIdentifier())
                .withStudyIdentifier(studyId).build();
        Validate.entityThrowingException(ReportDataKeyValidator.INSTANCE, key);
        
        ReportIndex existingIndex = reportIndexDao.getIndex(key);
        if (existingIndex == null) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
        if (!canAccess(existingIndex)) {
            throw new UnauthorizedException();
        }
        // Caller cannot change substudy relationships unless they are not associated to any substudy. 
        // We could allow users to add/remove the substudies they have membership in, but in practice that's
        // only one substudy and not likely to be very useful. It requires about 5 lines of set-based checks 
        // and a lot of tests, so skipping it for now.
        Set<String> callerRoles = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (!callerRoles.isEmpty()) {
            index.setSubstudyIds(existingIndex.getSubstudyIds());
        }
        reportIndexDao.updateIndex(index);
    }
    
    protected boolean canAccess(ReportIndex index) {
        if (index == null || index.getSubstudyIds() == null || index.getSubstudyIds().isEmpty() || index.isPublic()) {
            return true;
        }
        Set<String> callerRoles = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (callerRoles.isEmpty()) {
            return true;
        }
        return !Sets.intersection(callerRoles, index.getSubstudyIds()).isEmpty();
    }

    private void addToIndex(ReportDataKey key, Set<String> substudies) {
        reportIndexDao.addIndex(key, substudies);
    }
    
    private RangeTuple<DateTime> validateDateTimeRange(DateTime startTime, DateTime endTime) {
        // If nothing is provided, we will default to 13 days prior to today
        if (startTime == null && endTime == null) {
            DateTime now = getDateTime();
            startTime = now.minusDays(14);
            endTime = now;
        }
        if (startTime == null || endTime == null) {
            throw new BadRequestException(EITHER_BOTH_DATES_OR_NEITHER);
        }
        if (startTime.isAfter(endTime)) {
            throw new BadRequestException(INVALID_TIME_RANGE);
        }
        DateTimeZone timezone = startTime.getZone();
        if (!timezone.equals(endTime.getZone())) {
            throw new BadRequestException(AMBIGUOUS_TIMEZONE);
        }
        return new RangeTuple<>(startTime, endTime);
    }
    
    private RangeTuple<LocalDate> validateLocalDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = DateUtils.getCurrentCalendarDateInLocalTime().minusDays(1);
        }
        if (endDate == null) {
            endDate = DateUtils.getCurrentCalendarDateInLocalTime();
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date " + startDate + " can't be after end date " + endDate);
        }
        Period dateRange = new Period(startDate, endDate, PeriodType.days());
        if (dateRange.getDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot exceed " + MAX_RANGE_DAYS + " days, startDate=" +
                    startDate + ", endDate=" + endDate);
        }
        return new RangeTuple<>(startDate, endDate);
    }
}
