package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.apache.commons.lang3.StringUtils.isBlank;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
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
 * <p>Permissions for reports are more complicated than other controllers:</p>
 * <p><b>Study Reports</b></p>
 * <ul>
 *   <li>any authenticated user can get the study identifiers (indices)</li>
 *   <li>any authenticated user can see a study report</li>  
 *   <li>developers/workers can add/delete</li>
 * </ul>
 * 
 * <p><b>Participant Reports</b></p>
 * <ul>
 *   <li>any authenticated user can get the participant identifiers (indices)</li>
 *   <li>user or researcher can see reports (for user, only self report)</li>
 *   <li>developers/workers can add/delete</li>
 * </ul>
 */
@Controller
public class ReportController extends BaseController {
    
    @Autowired
    ReportService reportService;
    
    @Autowired
    AccountDao accountDao;
    
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    /**
     * Get a list of the identifiers used for reports in this study. If the value is missing or invalid, 
     * defaults to list of study identifiers.
     */
    public Result getReportIndices(String type) throws Exception {
        UserSession session = getAuthenticatedSession();
        ReportType reportType = ("participant".equals(type)) ? ReportType.PARTICIPANT : ReportType.STUDY;
        
        ReportTypeResourceList<? extends ReportIndex> indices = reportService.getReportIndices(session.getStudyIdentifier(), reportType);
        return okResult(indices);
    }
    
    /**
     * Individuals can get their own participant reports. For this request, because we are checking consent, 
     * we also verify the consent-related headers are being sent (this report has been retrieved by embedded web 
     * components that haven't sent the correct headers in the past).
     */
    public Result getParticipantReport(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();

        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                session.getStudyIdentifier(), identifier, session.getHealthCode(), startDate, endDate);
        
        return okResult(results);
    }
    
    public Result getParticipantReportForResearcher(String userId, String identifier, String startDateString,
            String endDateString) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        Account account = accountDao.getAccount(study, userId);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                session.getStudyIdentifier(), identifier, account.getHealthCode(), startDate, endDate);
        
        return okResult(results);
    }
    
    /**
     * Report participant data can be saved by developers or by worker processes. The JSON for these must 
     * include a healthCode field. This is validated when constructing the DataReportKey.
     */
    public Result saveParticipantReport(String userId, String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountDao.getAccount(study, userId);
        
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
        
        JsonNode node = requestToJSON(request());
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
        
        Account account = accountDao.getAccount(study, userId);
        
        reportService.deleteParticipantReport(session.getStudyIdentifier(), identifier, account.getHealthCode());
        
        return okResult("Report deleted.");
    }
    
    /**
     * Delete an individual participant report record
     */
    public Result deleteParticipantReportRecord(String userId, String identifier, String dateString) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        LocalDate date = parseDateHelper(dateString);
        
        Account account = accountDao.getAccount(study, userId);
        
        reportService.deleteParticipantReportRecord(session.getStudyIdentifier(), identifier, date, account.getHealthCode());
        
        return okResult("Report record deleted.");
    }
    
    public Result deleteParticipantReportIndex(String identifier) {
        UserSession session = getAuthenticatedSession(ADMIN);
        
        reportService.deleteParticipantReportIndex(session.getStudyIdentifier(), identifier);
        
        return okResult("Report index deleted.");
    }
    
    /**
     * Any authenticated user can get study reports, as some might be internal/administrative and some might 
     * be intended for end users, and these do not expose user-specific information.
     */
    public Result getStudyReport(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();
        
        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService
                .getStudyReport(session.getStudyIdentifier(), identifier, startDate, endDate);
        
        return okResult(results);
    }
    
    /**
     * Get a study report *if* it is marked public, as this call does not require the user to be authenticated.
     */
    public Result getPublicStudyReport(String studyIdString, String identifier, String startDateString,
            String endDateString) {
        StudyIdentifier studyId = new StudyIdentifierImpl(studyIdString);

        verifyIndex(studyId, identifier);

        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getStudyReport(
                studyId, identifier, startDate, endDate);
        
        return okResult(results);
    }
    
    /**
     * Report study data can be saved by developers or by worker processes.
     */
    public Result saveStudyReport(String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
     
        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveStudyReport(session.getStudyIdentifier(), identifier, reportData);
        
        return createdResult("Report data saved.");
    }

    /**
     * A similar method as above but specifying study id only for WORKER
     */
    public Result saveStudyReportForSpecifiedStudy(String studyIdString, String identifier) throws Exception {
        getAuthenticatedSession(WORKER);

        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it

        StudyIdentifier studyId = new StudyIdentifierImpl(studyIdString);
        reportService.saveStudyReport(studyId, identifier, reportData);

        return createdResult("Report data saved.");
    }
    
    /**
     * Developers and workers can delete study report data. This is not performant for large data sets and 
     * should only be done during testing.
     */
    public Result deleteStudyReport(String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        reportService.deleteStudyReport(session.getStudyIdentifier(), identifier);
        
        return okResult("Report deleted.");
    }
    
    /**
     * Delete an individual study report record. 
     */
    public Result deleteStudyReportRecord(String identifier, String dateString) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        LocalDate date = parseDateHelper(dateString);
        
        reportService.deleteStudyReportRecord(session.getStudyIdentifier(), identifier, date);
        
        return okResult("Report record deleted.");
    }
    
    /**
     * Get a single study report index
     */
    public Result getStudyReportIndex(String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(ReportType.STUDY)
                .withStudyIdentifier(session.getStudyIdentifier()).build();
        
        ReportIndex index = reportService.getReportIndex(key);
        return okResult(index);
    }
    
    /**
     * Update a single study report index. 
     */
    public Result updateStudyReportIndex(String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        ReportIndex index = parseJson(request(), ReportIndex.class);
        ReportDataKey key = new ReportDataKey.Builder()
                .withHealthCode(session.getHealthCode())
                .withReportType(ReportType.STUDY)
                .withIdentifier(identifier)
                .withStudyIdentifier(session.getStudyIdentifier()).build();
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(identifier);
        
        reportService.updateReportIndex(ReportType.STUDY, index);
        
        return okResult("Report index updated.");
    }
    
    private void verifyIndex(final StudyIdentifier studyId, final String identifier) {
        ReportDataKey key = new ReportDataKey.Builder()
                .withIdentifier(identifier)
                .withReportType(ReportType.STUDY)
                .withStudyIdentifier(studyId).build();
        
        ReportIndex index = reportService.getReportIndex(key);
        if (index == null || !index.isPublic()) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
    }
    
    private static LocalDate parseDateHelper(String dateStr) {
        if (isBlank(dateStr)) {
            return null;
        } else {
            try {
                return DateUtils.parseCalendarDate(dateStr);
            } catch (RuntimeException ex) {
                throw new BadRequestException("invalid date " + dateStr);
            }
        }
    }    
}
