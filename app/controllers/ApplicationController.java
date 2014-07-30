package controllers;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;

import play.libs.Json;
import play.mvc.*;

@org.springframework.stereotype.Controller
public class ApplicationController extends BaseController {

    private StudyControllerService studyControllerService;

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public Result redirectToApp() {
        return redirect("/app/");
    }

    public Result loadApp() throws Exception {
        UserSession session = checkForSession();
        if (session == null) {
            session = new UserSession();
        }
        UserSessionInfo info = new UserSessionInfo(session);
        Study study = studyControllerService.getStudyByHostname(request());
        return ok(views.html.index.render(Json.toJson(info).toString(), study.getName()));
    }

    public Result redirectToPublicApp() {
        return redirect("/");
    }

    public Result loadPublicApp() throws Exception {
        UserSessionInfo info = new UserSessionInfo(new UserSession());

        // There's probably a non-crappy way of doing this in Play, but I couldn't find it.
        Study study = studyControllerService.getStudyByHostname(request());
        if (study == null || "neurod".equals(study.getKey())) {
            return ok(views.html.neurod.render(Json.toJson(info).toString()));    
        }
        throw new BridgeServiceException("Cannot determine your study from the host name: " + studyControllerService.getHostname(request()), 400);
    }

    public Result loadConsent(String sessionToken) throws Exception {
        if (sessionToken != null) {
            setSessionToken(sessionToken);    
        }
        UserSession session = checkForSession();
        if (session == null) {
            session = new UserSession();
        }
        UserSessionInfo info = new UserSessionInfo(session);
        return ok(views.html.consent.render(Json.toJson(info).toString()));
    }

    public Result redirectToConsent() {
        return redirect("/consent/");
    }
}
