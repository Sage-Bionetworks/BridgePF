package controllers;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.sagebionetworks.bridge.services.AuthenticationService;

import play.libs.Json;
import play.mvc.*;

@org.springframework.stereotype.Controller
public class ApplicationController extends BaseController {

    private AuthenticationService authenticationService;
    private StudyControllerService studyControllerService;

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
    
    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public Result redirectToApp() {
        return redirect("/app/");
    }

    public Result loadApp() throws Exception {
        String sessionToken = getSessionToken(false);
        UserSession session = authenticationService.getSession(sessionToken);
        UserSessionInfo info = new UserSessionInfo(session);
        
        return ok(views.html.index.render(Json.toJson(info).toString()));
    }
    
    public Result redirectToPublicApp() {
        return redirect("/");
    }

    public Result loadPublicApp() throws Exception {
        String sessionToken = getSessionToken(false);
        UserSession session = authenticationService.getSession(sessionToken);
        UserSessionInfo info = new UserSessionInfo(session);
        
        // There's probably a non-crappy way of doing this in Play, but I couldn't find it.
        Study study = studyControllerService.getStudyByHostname(request());
        if ("neurod".equals(study.getKey())) {
            return ok(views.html.neurod.render(Json.toJson(info).toString()));    
        }
        // For now, go to neurod that's all we have. Create an error page.
        return ok(views.html.index.render(Json.toJson(info).toString()));
    }
    
}
