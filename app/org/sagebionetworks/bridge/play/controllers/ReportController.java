package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.LinkedHashSet;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.ReportService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;

public class ReportController extends BaseController {

    private static final String NO_HEADERS_ERROR = "This request requires valid User-Agent and Accept-Language headers.";
    
    @Autowired
    ReportService reportService;
    
    @Autowired
    ParticipantService participantService;
    
    final void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }
    
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    /**
     * Reports for specific individuals accessible only to consented study participants.
     */
    public Result getParticipantReport(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedAndConsentedSession();
        checkHeaders();
        
        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService.getParticipantReport(
                session.getStudyIdentifier(), identifier, session.getHealthCode(), startDate, endDate);
        
        return ok((JsonNode)MAPPER.valueToTree(results));
    }
    
    /**
     * Report participant data can be saved by developers or by worker processes. The JSON for these must 
     * include a healthCode field. This is validated when constructing the DataReportKey.
     */
    public Result saveParticipantReport(String identifier, String userId) throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        
        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                participant.getHealthCode(), reportData);
        
        return createdResult("Report data saved.");
    }
    
    /**
     * Developers and workers can delete participant report data. This deletes all reports for all users. 
     * This is not performant for large data sets and should only be done during testing. 
     */
    public Result deleteParticipantReport(String identifier, String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        
        reportService.deleteParticipantReport(session.getStudyIdentifier(), identifier, participant.getHealthCode());
        
        return ok("Report deleted.");
    }
    
    /**
     * Any authenticated user can get study reports, as some might be internal/administrative and some might 
     * be intended for end users, and these do not expose user-specific information.
     */
    public Result getStudyReport(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();
        checkHeaders();
        
        LocalDate startDate = parseDateHelper(startDateString);
        LocalDate endDate = parseDateHelper(endDateString);
        
        DateRangeResourceList<? extends ReportData> results = reportService
                .getStudyReport(session.getStudyIdentifier(), identifier, startDate, endDate);
        
        return ok((JsonNode)MAPPER.valueToTree(results));
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
     * Developers and workers can delete study report data. This is not performant for large data sets and 
     * should only be done during testing.
     */
    public Result deleteStudyReport(String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        
        reportService.deleteStudyReport(session.getStudyIdentifier(), identifier);
        
        return ok("Report deleted.");
    }

    /**
     * Non-app clients are being created to display reports (specifically, web pages embedded in the apps). These 
     * have not passed along the headers needed to properly verify that the user is consented to view the reports. 
     * So in an exception to other endpoints, we <em>require</em> these headers for the GET calls for the reports.
     */
    private void checkHeaders() {
        ClientInfo info = getClientInfoFromUserAgentHeader();
        LinkedHashSet<String> languages = getLanguagesFromAcceptLanguageHeader();
        if (ClientInfo.UNKNOWN_CLIENT.equals(info) || languages.isEmpty()) {
            throw new BadRequestException(NO_HEADERS_ERROR);
        }
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
