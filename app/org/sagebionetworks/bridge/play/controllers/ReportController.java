package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.services.ReportService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;

public class ReportController extends BaseController {

    @Autowired
    ReportService reportService;
    
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    /**
     * Reports for specific individuals accessible only to consented study participants.
     */
    public Result getParticipantReportData(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                session.getStudyIdentifier(), identifier, session.getHealthCode(), startDate, endDate);
        
        return ok((JsonNode)MAPPER.valueToTree(results));
    }

    /**
     * Any authenticated user can get study reports, as some might be internal/administrative and some might 
     * be intended for end users, and these do not expose user-specific information.
     */
    public Result getStudyReportData(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();
        
        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService
                .getStudyReport(session.getStudyIdentifier(), identifier, startDate, endDate);
        
        return ok((JsonNode)MAPPER.valueToTree(results));
    }
    
    /**
     * Report participant data can be saved by developers or by worker processes. The JSON for these must 
     * include a healthCode field. This is validated when constructing the DataReportKey.
     */
    public Result saveParticipantReportData(String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        JsonNode node = requestToJSON(request());
        String healthCode = node.get("healthCode").asText();
        
        ReportData reportData = MAPPER.treeToValue(node, ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                healthCode, reportData);
        
        return createdResult("Report data saved.");
    }
    
    /**
     * Report study data can be saved by developers or by worker processes.
     */
    public Result saveStudyReportData(String identifier) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
     
        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveStudyReport(session.getStudyIdentifier(), identifier, reportData);
        
        return createdResult("Report data saved.");
    }
    
    /**
     * Developers and workers can delete participant report data. This is not performant for large data sets and 
     * should only be done during testing.
     */
    public Result deleteParticipantReportData(String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        reportService.deleteParticipantReport(session.getStudyIdentifier(), identifier, session.getHealthCode());
        
        return ok("Report deleted.");
    }
    
    /**
     * Developers and workers can delete study report data. This is not performant for large data sets and 
     * should only be done during testing.
     */
    public Result deleteStudyReportData(String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        reportService.deleteStudyReport(session.getStudyIdentifier(), identifier);
        
        return ok("Report deleted.");
    }
    
    private static LocalDate parseDateHelper(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
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
