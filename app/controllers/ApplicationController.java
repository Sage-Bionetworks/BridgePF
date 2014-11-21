package controllers;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;

import play.libs.Json;
import play.mvc.Result;

@org.springframework.stereotype.Controller
public class ApplicationController extends BaseController {

    private static final UserSession EMPTY_USER_SESSION = new UserSession();

    public Result redirectToApp() {
        return redirect("/app/");
    }

    public Result loadApp() throws Exception {
        UserSession session = getSessionIfItExists();
        if (session == null) {
            session = new UserSession();
        }
        UserSessionInfo info = new UserSessionInfo(session);
        Study study = studyService.getStudyByHostname(getHostname());
        return ok(views.html.index.render(Json.toJson(info).toString(), study.getName()));
    }

    public Result redirectToPublicApp() {
        return redirect("/");
    }

    public Result loadPublicApp() throws Exception {
        UserSessionInfo info = new UserSessionInfo(new UserSession());
        
        // There's probably a non-crappy way of doing this in Play, but I couldn't find it.
        Study study = studyService.getStudyByHostname(getHostname());
        if (study == null || "pd".equals(study.getKey()) || "neurod".equals(study.getKey()) || "parkinson".equals(study.getKey())) {
            return ok(views.html.neurod.render(Json.toJson(info).toString()));    
        } else if ("api".equals(study.getKey())) {
            return ok(views.html.api.render(Json.toJson(info).toString()));
        }
        throw new EntityNotFoundException(Study.class, "Cannot determine study from the host name: " + getHostname());
    }
    
    public Result loadConsent(String sessionToken) throws Exception {
        UserSession session = null;
        if (sessionToken != null) {
            session = cacheProvider.getUserSession(sessionToken);    
        }
        if (session == null) {
            session = EMPTY_USER_SESSION;
        }
        UserSessionInfo info = new UserSessionInfo(session);
        return ok(views.html.consent.render(Json.toJson(info).toString()));
    }

    public Result redirectToConsent() {
        return redirect("/consent/");
    }
}
