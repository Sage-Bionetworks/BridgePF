package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.JsonNode;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.UserAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.Roles.ADMIN;

@Controller
public class UserManagementController extends BaseController {

    private static final String EMAIL_REQUIRED = "User email is required.";
    private static final String CONSENT_FIELD = "consent";

    private UserAdminService userAdminService;

    @Autowired
    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public Result createUser() throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        JsonNode node = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        userAdminService.createUser(participant, study, null, false, consent);

        return createdResult("User created.");
    }

    public Result deleteUser(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        userAdminService.deleteUser(study, userId);
        
        return okResult("User deleted.");
    }
    
    public Result deleteUser2(String email) throws Exception {
        if (isBlank(email)) {
            throw new BadRequestException(EMAIL_REQUIRED);
        }
        return deleteUser(email);
    }

}
