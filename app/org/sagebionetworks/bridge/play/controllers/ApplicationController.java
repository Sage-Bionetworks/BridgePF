package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.ASSETS_HOST;
import static org.apache.commons.lang3.StringUtils.isBlank;

import org.apache.commons.lang3.StringEscapeUtils;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;

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
            throw new BadRequestException("studyId parameter is required");
        }
        if (isBlank(email)) {
            throw new BadRequestException("email parameter is required");
        }
        if (isBlank(token)) {
            throw new BadRequestException("token parameter is required");
        }
        Study study = studyService.getStudy(studyId);
        
        CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
        
        UserSession session = authenticationService.emailSignIn(study, context, email, token);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
}
