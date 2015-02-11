package controllers;

import static org.sagebionetworks.bridge.BridgeConstants.ASSETS_HOST;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;

import play.libs.Json;
import play.mvc.Result;

@org.springframework.stereotype.Controller
public class ApplicationController extends BaseController {

    private static final UserSession EMPTY_USER_SESSION = new UserSession();
    private static final String ASSETS_BUILD = "201501291830";

    public Result redirectToPublicApp() {
        return redirect("/");
    }

    public Result loadApp() throws Exception {
        return loadPublicApp();
    }

    public Result loadPublicApp() throws Exception {
        UserSessionInfo info = new UserSessionInfo(new UserSession());
        try {
            // Study study = getStudy(); don't need the entire study, so don't get it
            String studyIdentifier = getStudyIdentifier();
            if ("parkinson".equals(studyIdentifier)) {
                return ok(views.html.neurod.render(Json.toJson(info).toString(), ASSETS_HOST, ASSETS_BUILD));
            } else if ("api".equals(studyIdentifier)) {
                return ok(views.html.api.render(Json.toJson(info).toString(), ASSETS_HOST, ASSETS_BUILD));
            }
            String apiHost = "api" + BridgeConfigFactory.getConfig().getStudyHostnamePostfix();
            return ok(views.html.nosite.render("Unknown study", apiHost));
        } catch(EntityNotFoundException e) {
            return ok(views.html.api.render(Json.toJson(info).toString(), ASSETS_HOST, ASSETS_BUILD));
        }
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
        return ok(views.html.consent.render(Json.toJson(info).toString(), ASSETS_HOST, ASSETS_BUILD));
    }

    public Result preflight(String all) {
        response().setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "https://" + ASSETS_HOST);
        response().setHeader(ACCESS_CONTROL_ALLOW_METHODS, "HEAD, GET, OPTIONS, POST, PUT, DELETE");
        response().setHeader(ACCESS_CONTROL_ALLOW_HEADERS, "*");
        return ok();
    }

    public Result verifyEmail() {
        UserSessionInfo info = new UserSessionInfo(new UserSession());
        return ok(views.html.verifyEmail.render(Json.toJson(info).toString(), ASSETS_HOST, ASSETS_BUILD));
    }

    public Result resetPassword() {
        UserSessionInfo info = new UserSessionInfo(new UserSession());
        return ok(views.html.resetPassword.render(Json.toJson(info).toString(), ASSETS_HOST, ASSETS_BUILD));
    }
}
