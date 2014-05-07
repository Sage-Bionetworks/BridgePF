package controllers;

import models.UserSession;

import org.sagebionetworks.bridge.context.BridgeContext;
import org.sagebionetworks.bridge.services.AuthenticationService;

import play.libs.Json;
import play.mvc.*;

@org.springframework.stereotype.Controller
public class ApplicationController extends BaseController {

	private AuthenticationService authenticationService;

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public Result redirectToApp() {
		return redirect("/");
	}
	
    public Result loadApp() throws Exception {
		String sessionToken = getSessionToken(false);
		UserSession session = authenticationService.getSession(sessionToken);
		session.setEnvironment(new BridgeContext().getEnvironment());
    	return ok(views.html.index.render(Json.toJson(session).toString()));
    }
    
}
