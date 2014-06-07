package controllers;

import models.JsonPayload;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

import play.mvc.*;

public class AuthenticationController extends BaseController {

    private StudyControllerService studyControllerService;
    
    public void setStudyControllerService(StudyControllerService scs) {
        this.studyControllerService = scs;
    }
    
	public Result signIn() throws Exception {
        String sessionToken = getSessionToken(false);
        authenticationService.signOut(sessionToken);

	    Study study = studyControllerService.getStudyByHostname(request());
		SignIn signIn = SignIn.fromJson(request().body().asJson());
		UserSession session = authenticationService.signIn(study, signIn);
		response().setCookie(BridgeConstants.SESSION_TOKEN_HEADER, session.getSessionToken());
		return jsonResult(new JsonPayload<UserSession>(session));
	}

	public Result signOut() throws Exception {
		String sessionToken = getSessionToken(false);
		authenticationService.signOut(sessionToken);
		response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
		return jsonResult("Signed out.");
	}

	public Result signUp() throws Exception {
        SignUp signUp = SignUp.fromJson(request().body().asJson());
        authenticationService.signUp(signUp);
	    return jsonResult("Signed up.");
	}
	
	public Result verifyEmail() throws Exception {
	    EmailVerification ev = EmailVerification.fromJson(request().body().asJson());
	    authenticationService.verifyEmail(ev);
	    return jsonResult("Email verified.");
	}
	
	public Result requestResetPassword() throws Exception {
		Email email = Email.fromJson(request().body().asJson());
		authenticationService.requestResetPassword(email);
		return jsonResult("An email has been sent allowing you to set a new password.");
	}
	
	public Result resetPassword() throws Exception {
	    PasswordReset passwordReset = PasswordReset.fromJson(request().body().asJson());
		authenticationService.resetPassword(passwordReset);
		return jsonResult("Password has been changed.");
	}
	
	public Result consentToResearch() throws Exception {
		String sessionToken = getSessionToken(true);
		authenticationService.consentToResearch(sessionToken);
		return jsonResult("Consent to research has been recorded.");
	}
	
}
