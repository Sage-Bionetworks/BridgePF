package controllers;

import models.JsonPayload;
import models.SignIn;
import models.SignUp;
import models.UserSession;

import org.sagebionetworks.bridge.BridgeConstants;

import play.mvc.*;

public class AuthenticationController extends BaseController {

	public Result signIn() throws Exception {
		SignIn signIn = SignIn.fromJson(request().body().asJson());
		UserSession session = authenticationService.signIn(signIn.getUsername(), signIn.getPassword());
		response().setCookie(BridgeConstants.SESSION_TOKEN_HEADER, session.getSessionToken());
		return jsonResult(new JsonPayload<UserSession>(session));
	}

	public Result signOut() throws Exception {
		String sessionToken = getSessionToken(false);
		authenticationService.signOut(sessionToken);
		response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
		return jsonResult("Signed out.");
	}

	public Result requestResetPassword() throws Exception {
		SignUp signUp = SignUp.fromJson(request().body().asJson());
		authenticationService.requestResetPassword(signUp.getEmail());
		return jsonResult("An email has been sent allowing you to set a new password.");
	}
	
	public Result resetPassword() throws Exception {
		String sessionToken = getSessionToken(true);
		SignIn signIn = SignIn.fromJson(request().body().asJson());
		authenticationService.resetPassword(sessionToken, signIn.getPassword());
		return jsonResult("Password has been changed.");
	}
	
	public Result consentToResearch() throws Exception {
		String sessionToken = getSessionToken(true);
		authenticationService.consentToResearch(sessionToken);
		return jsonResult("Consent to research has been recorded.");
	}
	
}
