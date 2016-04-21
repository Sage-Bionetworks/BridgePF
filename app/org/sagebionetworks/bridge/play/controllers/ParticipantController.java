package org.sagebionetworks.bridge.play.controllers;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;

import play.mvc.Result;

@Controller
public class ParticipantController extends BaseController {
    
    private static final String EMAIL_REQUIRED = "Participant email is required.";
    private ParticipantService participantService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    public Result getParticipants(String offsetByString, String pageSizeString, String emailFilter) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        Study study = studyService.getStudy(session.getStudyIdentifier());
        int offsetBy = getIntOrDefault(offsetByString, 0);
        int pageSize = getIntOrDefault(pageSizeString, API_DEFAULT_PAGE_SIZE);
        
        PagedResourceList<AccountSummary> page = participantService.getPagedAccountSummaries(study, offsetBy, pageSize, emailFilter);
        return okResult(page);
    }
    
    public Result createParticipant() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        
        IdentifierHolder holder = participantService.createParticipant(study, participant);
        
        return createdResult(holder);
    }
    
    public Result getParticipant(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        StudyParticipant participant = participantService.getParticipant(study, userId);
        return okResult(participant);
    }
    
    public Result updateParticipant(String userId) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        // Just stop right here because something is wrong
        if (participant.getId() != null && !userId.equals(participant.getId())) {
            throw new BadRequestException("ID in JSON does not match email in URL.");
        }
        participantService.updateParticipant(study, userId, participant);
        
        return okResult("Participant updated.");
    }
    
    public Result signOut(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        participantService.signUserOut(study, userId);

        return okResult("User signed out.");
    }

    public Result getParticipant2(String email) {
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        return getParticipant(email);
    }
    
    public Result updateParticipant2(String email) {
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        return updateParticipant(email);
    }
    
    public Result signOut2(String email) throws Exception {
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        return signOut(email);
    }

    private int getIntOrDefault(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return parseInt(value);
        } catch(NumberFormatException e) {
            throw new BadRequestException(value + " is not an integer");
        }
    }

}
