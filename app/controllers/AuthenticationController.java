package controllers;

import models.JsonPayload;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;

import play.mvc.*;

public class AuthenticationController extends BaseController {

    private StudyControllerService studyControllerService;
    
    public void setStudyControllerService(StudyControllerService scs) {
        this.studyControllerService = scs;
    }
    
    public Result signIn() throws Exception {
        UserSession session = checkForSession();
        if (session != null) {
            setSessionToken(session.getSessionToken());
            return jsonResult(new JsonPayload<UserSessionInfo>(new UserSessionInfo(session)));
        }
        Study study = studyControllerService.getStudyByHostname(request());
        SignIn signIn = SignIn.fromJson(request().body().asJson());
        session = authenticationService.signIn(study, signIn);
        setSessionToken(session.getSessionToken());
        return jsonResult(new JsonPayload<UserSessionInfo>(new UserSessionInfo(session)));
    }

    public Result signOut() throws Exception {
        UserSession session = checkForSession();
        if (session != null) {
            authenticationService.signOut(session.getSessionToken());    
        }
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        return jsonResult("Signed out.");
    }

    public Result signUp() throws Exception {
        SignUp signUp = SignUp.fromJson(request().body().asJson());
        Study study = studyControllerService.getStudyByHostname(request());
        authenticationService.signUp(signUp, study);
        return jsonResult("Signed up.");
    }
    
    public Result verifyEmail() throws Exception {
        Study study = studyControllerService.getStudyByHostname(request());
        EmailVerification ev = EmailVerification.fromJson(request().body().asJson());
        // In normal course of events (verify email, consent to research), 
        // an exception is thrown. Code after this line will rarely execute
        UserSession session = authenticationService.verifyEmail(study, ev);
        setSessionToken(session.getSessionToken());
        return jsonResult(new JsonPayload<UserSessionInfo>(new UserSessionInfo(session)));
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
        UserSession session = checkForSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        authenticationService.consentToResearch(session.getSessionToken());
        return jsonResult("Consent to research has been recorded.");
    }
    
}
