package controllers;

import models.UserSession;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.services.AuthenticationService;

import play.libs.Json;
import play.mvc.*;

@org.springframework.stereotype.Controller
public class ApplicationController extends BaseController {

	private AuthenticationService authenticationService;
	private BridgeConfig bridgeConfig;

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

	public Result redirectToApp() {
		return redirect("/");
	}

    public Result loadApp() throws Exception {
		String sessionToken = getSessionToken(false);
		UserSession session = authenticationService.getSession(sessionToken);
		session.setEnvironment(bridgeConfig.getEnvironment());
    	return ok(views.html.index.render(Json.toJson(session).toString()));
    }
    
}
