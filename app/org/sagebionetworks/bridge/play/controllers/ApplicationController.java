package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.ASSETS_HOST;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.springframework.stereotype.Controller;

import play.libs.Json;
import play.mvc.Result;

@Controller
public class ApplicationController extends BaseController {

    private static final UserSessionInfo EMPTY_USER_SESSION = new UserSessionInfo(new UserSession());
    private static final String ASSETS_BUILD = "201501291830";

    public Result loadApp() throws Exception {
        return ok(views.html.index.render());
    }

    public Result verifyEmail() {
        return ok(views.html.verifyEmail.render(Json.toJson(EMPTY_USER_SESSION).toString(), ASSETS_HOST, ASSETS_BUILD));
    }

    public Result resetPassword() {
        return ok(views.html.resetPassword.render(Json.toJson(EMPTY_USER_SESSION).toString(), ASSETS_HOST, ASSETS_BUILD));
    }

    // NOTE: I don't see this getting called...
    public Result preflight(String all) {
        response().setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response().setHeader(ACCESS_CONTROL_ALLOW_METHODS, "HEAD, GET, OPTIONS, POST, PUT, DELETE");
        // Accept,  Accept-Language, Content-Language and Content-Type for normal HTML types are all allowed by default.
        // We add Content-Type to specify JSON. We can add other headers here when needed, but wildcards don't always 
        // seem to work, it's browser-dependent.
        response().setHeader(ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, User-Agent, Bridge-Session");
        return ok();
    }
}
