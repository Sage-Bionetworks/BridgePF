package controllers;

import models.JsonPayload;
import models.SignIn;
import models.SignUp;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.auth.Session;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.*;

public class Authentication extends BaseController {

	private AuthenticationService authenticationService;

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public Result signIn() throws Exception {
		SignIn signIn = SignIn.fromJson(request().body().asJson());
		Session session = authenticationService.signIn(signIn.getUsername(), signIn.getPassword());
		response().setCookie(BridgeConstants.SESSION_TOKEN, session.getSessionToken());
		return jsonResult(new JsonPayload<Session>(session));
	}

	public Result signOut() throws Exception {
		String sessionToken = getSessionToken();
		authenticationService.signOut(sessionToken);
		response().discardCookie(BridgeConstants.SESSION_TOKEN);
		return jsonResult("Signed out.");
	}

	public Result resetPassword() throws Exception {
		SignUp signUp = SignUp.fromJson(request().body().asJson());
		authenticationService.resetPassword(signUp.getEmail());
		return jsonResult("An email has been sent allowing you to set a new password.");
	}
	
	public Result getUserProfile() throws Exception {
		String sessionToken = getSessionToken();
		UserProfile profile = authenticationService.getUserProfile(sessionToken);
		return jsonResult(new JsonPayload<UserProfile>(profile));
	}
}
