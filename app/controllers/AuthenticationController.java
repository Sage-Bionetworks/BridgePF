package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Result;

public class AuthenticationController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private StudyControllerService studyControllerService;

    public void setStudyControllerService(StudyControllerService scs) {
        this.studyControllerService = scs;
    }

    public Result signIn() throws Exception {
        final long start = System.nanoTime();
        UserSession session = checkForSession();
        if (session != null) {
            setSessionToken(session.getSessionToken());
            return ok(constructJSON(new UserSessionInfo(session)));
        }
        Study study = studyControllerService.getStudyByHostname(request());
        SignIn signIn = SignIn.fromJson(request().body().asJson());
        session = authenticationService.signIn(study, signIn);
        setSessionToken(session.getSessionToken());
        Result result = ok(constructJSON(new UserSessionInfo(session)));
        final long end = System.nanoTime();
        logger.info("sign in controller " + (end - start));
        return result;
    }

    public Result signOut() throws Exception {
        UserSession session = checkForSession();
        if (session != null) {
            authenticationService.signOut(session.getSessionToken());
        }
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        return okResult("Signed out.");
    }

    public Result signUp() throws Exception {
        SignUp signUp = SignUp.fromJson(request().body().asJson());
        Study study = studyControllerService.getStudyByHostname(request());
        authenticationService.signUp(signUp, study);
        return okResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        Study study = studyControllerService.getStudyByHostname(request());
        EmailVerification ev = EmailVerification.fromJson(request().body().asJson());
        // In normal course of events (verify email, consent to research),
        // an exception is thrown. Code after this line will rarely execute
        UserSession session = authenticationService.verifyEmail(study, ev);
        setSessionToken(session.getSessionToken());
        return ok(constructJSON((new UserSessionInfo(session))));
    }

    public Result requestResetPassword() throws Exception {
        Email email = Email.fromJson(request().body().asJson());
        authenticationService.requestResetPassword(email);
        return okResult("An email has been sent allowing you to set a new password.");
    }

    public Result resetPassword() throws Exception {
        PasswordReset passwordReset = PasswordReset.fromJson(request().body().asJson());
        authenticationService.resetPassword(passwordReset);
        return okResult("Password has been changed.");
    }
}
