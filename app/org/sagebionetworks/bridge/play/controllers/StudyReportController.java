package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.BridgeUtils.getLocalDateOrDefault;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ReportService;

import play.mvc.Result;

/**
 * <p>Permissions for study reports are more complicated than other controllers:</p>
 * <p><b>Study Reports</b></p>
 * <ul>
 *   <li>any authenticated user can get the study identifiers (indices)</li>
 *   <li>any authenticated user can see a study report</li>  
 *   <li>developers/workers can add/delete</li>
 * </ul>
 */
@Controller
public class StudyReportController extends BaseController {
    
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
     * Get a list of the identifiers used for reports in this study. For backwards compatibility this method 
     * takes an argument and can return participants, but there is now a separate endpoint for that.
     */
    public Result listStudyReportIndices(String type) throws Exception {
        UserSession session = getAuthenticatedSession();
        ReportType reportType = ("participant".equals(type)) ? ReportType.PARTICIPANT : ReportType.STUDY;
        
        ReportTypeResourceList<? extends ReportIndex> indices = reportService.getReportIndices(session.getStudyIdentifier(), reportType);
        return okResult(indices);
    }
    
    /**
     * Any authenticated user can get study reports, as some might be internal/administrative and some might 
     * be intended for end users, and these do not expose user-specific information.
     */
    public Result getStudyReport(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();
        
        LocalDate startDate = getLocalDateOrDefault(startDateString, null);
        LocalDate endDate = getLocalDateOrDefault(endDateString, null);
        
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

        LocalDate startDate = getLocalDateOrDefault(startDateString, null);
        LocalDate endDate = getLocalDateOrDefault(endDateString, null);
        
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
    public Result saveStudyReportForWorker(String studyIdString, String identifier) throws Exception {
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
        LocalDate date = getLocalDateOrDefault(dateString, null);
        
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
    
}
