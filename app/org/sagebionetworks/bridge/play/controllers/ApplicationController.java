package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.ASSETS_HOST;
import static org.apache.commons.lang3.StringUtils.isBlank;

import org.apache.commons.lang3.StringEscapeUtils;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class ApplicationController extends BaseController {

    private static final String ASSETS_BUILD = "201501291830";

    public Result loadApp() throws Exception {
        return ok(views.html.index.render());
    }

    public Result verifyEmail(String studyId) {
        if (isBlank(studyId)) {
            throw new BadRequestException("study parameter is required");
        }
        Study study = studyService.getStudy(studyId);
        return ok(views.html.verifyEmail.render(ASSETS_HOST, ASSETS_BUILD,
                StringEscapeUtils.escapeHtml4(study.getName()), study.getSupportEmail()));
    }

    public Result resetPassword(String studyId) {
        if (isBlank(studyId)) {
            throw new BadRequestException("studyId parameter is required");
        }
        Study study = studyService.getStudy(studyId);
        return ok(views.html.resetPassword.render(ASSETS_HOST, ASSETS_BUILD,
                StringEscapeUtils.escapeHtml4(study.getName()), study.getSupportEmail()));
    }
    
    public Result startSession(String studyId, String email, String token) {
        if (isBlank(studyId)) {
            throw new BadRequestException("study ID is required");
        }
        SignIn signIn = new SignIn(studyId, email, null, token);
        
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(studyId);
        CriteriaContext context = getCriteriaContext(studyIdentifier);
        UserSession session = authenticationService.emailSignIn(context, signIn);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
}
