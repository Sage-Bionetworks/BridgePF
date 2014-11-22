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
        
        // We need to default to a study or the integration tests in Play will fail 
        // (localhost:3333 won't match anything).
        try {
            Study study = studyService.getStudyByHostname(getHostname());
            if ("pd".equals(study.getIdentifier()) || "neurod".equals(study.getIdentifier()) || "parkinson".equals(study.getIdentifier())) {
                return ok(views.html.neurod.render(Json.toJson(info).toString()));    
            }
        } catch(EntityNotFoundException e) {
            // Go with the API study
        }
        return ok(views.html.api.render(Json.toJson(info).toString()));
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
