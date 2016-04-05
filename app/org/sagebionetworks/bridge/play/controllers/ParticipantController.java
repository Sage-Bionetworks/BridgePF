package org.sagebionetworks.bridge.play.controllers;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Map;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserProfile;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;

import play.mvc.Result;

@Controller
public class ParticipantController extends BaseController {
    
    private static final String EMAIL_REQUIRED = "Participant email is required.";
    private ParticipantService participantService;
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    public Result getParticipant(String email) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        
        StudyParticipant participant = participantService.getParticipant(study, email);
        return okResult(participant);
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
        
        participantService.createParticipant(study, participant);
        
        return createdResult("Participant created.");
    }
    
    public Result updateParticipant(String email) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        // Just stop right here because something is wrong
        if (participant.getEmail() != null && !participant.getEmail().equals(email)) {
            throw new BadRequestException("Email in JSON does not match email query parameter.");
        }
        participantService.updateParticipant(study, email, participant);
        
        return okResult("Participant updated.");
    }
    
    // Already. will remove as soon as replacement is rolled out as I've already implemented this in SDK/Researcher UI
    @Deprecated
    public Result updateParticipantOptions(String email) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        
        JsonNode node = requestToJSON(request());
        
        Map<ParticipantOption,String> options = Maps.newHashMap();
        for (ParticipantOption option : ParticipantOption.values()) {
            JsonNode fieldNode = node.get(option.getFieldName());
            if (fieldNode != null) {
                String value = option.deserialize(fieldNode);
                options.put(option, value);
            }
        }
        participantService.updateParticipantOptions(study, email, options);
        
        return okResult("Participant options updated.");
    }
    
    // Already. will remove as soon as replacement is rolled out as I've already implemented this in SDK/Researcher UI
    @Deprecated 
    public Result updateProfile(String email) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        
        UserProfile profile = UserProfile.fromJson(study.getUserProfileAttributes(), requestToJSON(request()));
        
        participantService.updateProfile(study, email, profile);
        return okResult("User profile updated.");
    }
    
    public Result signOut(String email) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        participantService.signUserOut(study, email);

        return okResult("User signed out.");
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
