package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getLocalDateOrDefault;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ReportService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;

/**
 * <p>Permissions for participant reports are more complicated than other controllers:</p>
 * 
 * <p><b>Participant Reports</b></p>
 * <ul>
 *   <li>any authenticated user can get the participant identifiers (indices)</li>
 *   <li>user or researcher can see reports (for user, only self report)</li>
 *   <li>developers/workers can add/delete</li>
 * </ul>
 */
@Controller
public class ParticipantReportController extends BaseController {
    
    @Autowired
    ReportService reportService;
    
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    public Result getParticipantReportForSelf(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();

        LocalDate startDate = getLocalDateOrDefault(startDateString, null);
        LocalDate endDate = getLocalDateOrDefault(endDateString, null);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                session.getStudyIdentifier(), identifier, session.getHealthCode(), startDate, endDate);
        
        return okResult(results);
    }
    
    public Result getParticipantReportForSelfV4(String identifier, String startTimeString, String endTimeString,
            String offsetKey, String pageSizeString) {
        UserSession session = getAuthenticatedSession();

        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ReportData> results = reportService.getParticipantReportV4(
                session.getStudyIdentifier(), identifier, session.getHealthCode(), startTime, endTime, offsetKey,
                pageSize);
        
        return okResult(results);
    }
    
    public Result saveParticipantReportForSelf(String identifier) {
        UserSession session = getAuthenticatedSession();
        
        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                session.getHealthCode(), reportData);
        
        return createdResult("Report data saved.");
    }
    
    /**
     * Get a list of the identifiers used for participant reports in this study.
     */
    public Result listParticipantReportIndices() {
        UserSession session = getAuthenticatedSession();
        
        ReportTypeResourceList<? extends ReportIndex> indices = reportService
                .getReportIndices(session.getStudyIdentifier(), ReportType.PARTICIPANT);
        return okResult(indices);
    }
    
    public Result getParticipantReportIndex(String identifier) {
        UserSession session = getAuthenticatedSession();
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(ReportType.PARTICIPANT)
                .withStudyIdentifier(session.getStudyIdentifier()).build();
        
        ReportIndex index = reportService.getReportIndex(key);
        return okResult(index);
    }

    /** API to get reports for the given user by date. */
    public Result getParticipantReport(String userId, String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        return getParticipantReportInternal(session.getStudyIdentifier(), userId, identifier, startDateString,
                endDateString);
    }

    /** Worker API to get reports for the given user in the given study by date. */
    public Result getParticipantReportForWorker(String studyId, String userId, String reportId, String startDateString,
            String endDateString) {
        getAuthenticatedSession(WORKER);
        return getParticipantReportInternal(new StudyIdentifierImpl(studyId), userId, reportId, startDateString,
                endDateString);
    }

    private Result getParticipantReportInternal(StudyIdentifier studyId, String userId, String reportId,
            String startDateString, String endDateString) {
        LocalDate startDate = getLocalDateOrDefault(startDateString, null);
        LocalDate endDate = getLocalDateOrDefault(endDateString, null);

        Account account = accountDao.getAccount(AccountId.forId(studyId.getIdentifier(), userId));

        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                studyId, reportId, account.getHealthCode(), startDate, endDate);

        return okResult(results);
    }

    /** API to get reports for the given user by date-time. */
    public Result getParticipantReportV4(String userId, String identifier, String startTimeString, String endTimeString,
            String offsetKey, String pageSizeString) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        return getParticipantReportInternalV4(session.getStudyIdentifier(), userId, identifier, startTimeString,
                endTimeString, offsetKey, pageSizeString);
    }

    /** Worker API to get reports for the given user in the given study by date-time. */
    public Result getParticipantReportForWorkerV4(String studyId, String userId, String reportId, String startTimeString,
            String endTimeString, String offsetKey, String pageSizeString) {
        getAuthenticatedSession(WORKER);
        return getParticipantReportInternalV4(new StudyIdentifierImpl(studyId), userId, reportId, startTimeString,
                endTimeString, offsetKey, pageSizeString);
    }

    // Helper method, shared by both getParticipantReportV4() and getParticipantReportForWorkerV4().
    private Result getParticipantReportInternalV4(StudyIdentifier studyId, String userId, String reportId,
            String startTimeString, String endTimeString, String offsetKey, String pageSizeString) {
        DateTime startTime = getDateTimeOrDefault(startTimeString, null);
        DateTime endTime = getDateTimeOrDefault(endTimeString, null);
        int pageSize = getIntOrDefault(pageSizeString, BridgeConstants.API_DEFAULT_PAGE_SIZE);

        Account account = accountDao.getAccount(AccountId.forId(studyId.getIdentifier(), userId));

        ForwardCursorPagedResourceList<ReportData> page = reportService.getParticipantReportV4(studyId, reportId,
                account.getHealthCode(), startTime, endTime, offsetKey, pageSize);

        return okResult(page);
    }

    /**
     * Report participant data can be saved by developers or by worker processes. The JSON for these must 
     * include a healthCode field. This is validated when constructing the DataReportKey.
     */
    public Result saveParticipantReport(String userId, String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), userId));
        
        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                account.getHealthCode(), reportData);
        
        return createdResult("Report data saved.");
    }
    
    /**
     * When saving, worker accounts do not know the userId of the account, only the healthCode, so a 
     * special method is needed.
     */
    public Result saveParticipantReportForWorker(String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(WORKER);
        
        JsonNode node = parseJson(request(), JsonNode.class);
        if (!node.has("healthCode")) {
            throw new BadRequestException("A health code is required to save report data.");
        }
        String healthCode = node.get("healthCode").asText();
        
        ReportData reportData = MAPPER.treeToValue(node, ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                healthCode, reportData);
        
        return createdResult("Report data saved.");
    }
    
    /**
     * Developers and workers can delete participant report data (though worker accounts are unlikely 
     * to know the user ID for records). This deletes all reports for all users. This is not 
     * performant for large data sets and should only be done during testing. 
     */
    public Result deleteParticipantReport(String userId, String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), userId));
        
        reportService.deleteParticipantReport(session.getStudyIdentifier(), identifier, account.getHealthCode());
        
        return okResult("Report deleted.");
    }
    
    /**
     * Delete an individual participant report record
     */
    public Result deleteParticipantReportRecord(String userId, String identifier, String dateString) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), userId));
        
        reportService.deleteParticipantReportRecord(session.getStudyIdentifier(), identifier, dateString, account.getHealthCode());
        
        return okResult("Report record deleted.");
    }
    
    public Result deleteParticipantReportIndex(String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        reportService.deleteParticipantReportIndex(session.getStudyIdentifier(), identifier);
        
        return okResult("Report index deleted.");
    }
    
}
