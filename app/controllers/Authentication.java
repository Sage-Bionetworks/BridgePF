package controllers;

import models.JsonPayload;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.repo.model.UserProfile;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.*;

public class Authentication extends BaseController {

	private AuthenticationService authenticationService;

	public void setAuthenticationService(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	public Result signIn() throws Exception {
		// I cannot find a way, in Play, to convert the JSON payload to a bound Java object.
		// serving as a form object. And I'm pretty sure the problem here is Play, not me.
		// There are alternative code paths, but none work.
		JsonNode body = request().body().asJson();
		String username = body.get("username").asText();
		String password = body.get("password").asText();
		
		String sessionToken = authenticationService.signIn(username, password);
		response().setCookie(BridgeConstants.SESSION_TOKEN, sessionToken);

		return jsonResult(new JsonPayload<String>("SessionToken", sessionToken));
	}

	public Result signOut() throws Exception {
		String sessionToken = getSessionToken();
		authenticationService.signOut(sessionToken);
		response().discardCookie(BridgeConstants.SESSION_TOKEN);
		return jsonResult("Signed out.");
	}

	public Result resetPassword() throws Exception {
		JsonNode body = request().body().asJson();
		String email = body.get("email").asText();
		authenticationService.resetPassword(email);
		response().discardCookie(BridgeConstants.SESSION_TOKEN);
		return jsonResult("An email has been sent allowing you to set a new password.");
	}
	
	public Result getUserProfile() throws Exception {
		String sessionToken = getSessionToken();
		UserProfile profile = authenticationService.getUserProfile(sessionToken);
		
		return jsonResult(new JsonPayload<UserProfile>(profile));
	}
}
