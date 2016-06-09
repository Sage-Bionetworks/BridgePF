package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.LinkedHashSet;
import java.util.List;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ReportService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;

@Controller
public class ReportController extends BaseController {

    private static final String NO_HEADERS_ERROR = "This request requires valid User-Agent and Accept-Language headers.";
    
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
     * Get a list of the identifiers used for participant reports in this study.
     */
    public Result getParticipantReportIndices() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<? extends ReportIndex> indices = reportService.getReportIndices(
                session.getStudyIdentifier(), ReportType.PARTICIPANT);
        return okResult(indices);
    }

    /**
     * Reports for specific individuals accessible to either consented study participants, or to researchers.
     */
    public Result getParticipantReport(String identifier, String startDateString, String endDateString) {
        UserSession session = getAuthenticatedSession();
        if (!session.isInRole(RESEARCHER) && !session.doesConsent()) {
            throw new UnauthorizedException();
        }
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
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountDao.getAccount(study, userId);
        
        ReportData reportData = parseJson(request(), ReportData.class);
        reportData.setKey(null); // set in service, but just so no future use depends on it
        
        reportService.saveParticipantReport(session.getStudyIdentifier(), identifier, 
                account.getHealthCode(), reportData);
        
        return createdResult("Report data saved.");
    }
    
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
     * Developers and workers can delete participant report data. This deletes all reports for all users. 
     * This is not performant for large data sets and should only be done during testing. 
     */
    public Result deleteParticipantReport(String identifier, String userId) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        Account account = accountDao.getAccount(study, userId);
        
        reportService.deleteParticipantReport(session.getStudyIdentifier(), identifier, account.getHealthCode());
        
        return okResult("Report deleted.");
    }
    
    /**
     * Get a list of the identifiers used for participant reports in this study.
     */
    public Result getStudyReportIndices() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        List<? extends ReportIndex> indices = reportService.getReportIndices(
                session.getStudyIdentifier(), ReportType.STUDY);
        return okResult(indices);
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
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        reportService.deleteStudyReport(session.getStudyIdentifier(), identifier);
        
        return okResult("Report deleted.");
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
