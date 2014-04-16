package controllers;

import models.JsonPayload;
import models.SignIn;
import models.SignUp;
import models.UserSession;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.services.AuthenticationService;

import play.mvc.*;

public class Authentication extends BaseController {

	private AuthenticationService authenticationService;

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public Result signIn() throws Exception {
		SignIn signIn = SignIn.fromJson(request().body().asJson());
		UserSession session = authenticationService.signIn(signIn.getUsername(), signIn.getPassword());
		session.setAuthenticated(true);
		response().setCookie(BridgeConstants.SESSION_TOKEN, session.getSessionToken());
		return jsonResult(new JsonPayload<UserSession>(session));
	}

	public Result signOut() throws Exception {
		String sessionToken = getSessionToken(true);
		authenticationService.signOut(sessionToken);
		response().discardCookie(BridgeConstants.SESSION_TOKEN);
		return jsonResult("Signed out.");
	}

	public Result resetPassword() throws Exception {
		SignUp signUp = SignUp.fromJson(request().body().asJson());
		authenticationService.resetPassword(signUp.getEmail());
		return jsonResult("An email has been sent allowing you to set a new password.");
	}
	
}
