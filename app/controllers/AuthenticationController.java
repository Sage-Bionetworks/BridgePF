package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.mvc.Result;

public class AuthenticationController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    public Result signIn() throws Exception {
        final long start = System.nanoTime();
        UserSession session = getSessionIfItExists();
        if (session != null) {
            setSessionToken(session.getSessionToken());
            return okResult(new UserSessionInfo(session));
        }
        try {
            Study study = getStudy();
            SignIn signIn = SignIn.fromJson(requestToJSON(request()));
            session = authenticationService.signIn(study, signIn);
            setSessionToken(session.getSessionToken());
            Result result = okResult(new UserSessionInfo(session));
            final long end = System.nanoTime();
            logger.info("sign in controller " + (end - start));
            return result;
        } catch(ConsentRequiredException e) {
            setSessionToken(e.getUserSession().getSessionToken());
            throw e;
        }
    }

    public Result signOut() throws Exception {
        UserSession session = getSessionIfItExists();
        if (session != null) {
            authenticationService.signOut(session.getSessionToken());
        }
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        return okResult("Signed out.");
    }

    public Result signUp() throws Exception {
        SignUp signUp = SignUp.fromJson(requestToJSON(request()), false);
        Study study = getStudy();
        authenticationService.signUp(signUp, study, true);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        Study study = getStudy();
        EmailVerification ev = EmailVerification.fromJson(requestToJSON(request()));
        // In normal course of events (verify email, consent to research),
        // an exception is thrown. Code after this line will rarely execute
        UserSession session = authenticationService.verifyEmail(study, ev);
        setSessionToken(session.getSessionToken());
        return okResult(new UserSessionInfo(session));
    }
    
    public Result resendEmailVerification() throws Exception {
        Study study = getStudy();
        Email email = Email.fromJson(requestToJSON(request()));
        
        authenticationService.resendEmailVerification(study, email);
        return okResult("A request to verify an email address was re-sent.");
    }

    public Result requestResetPassword() throws Exception {
        Email email = Email.fromJson(requestToJSON(request()));
        authenticationService.requestResetPassword(email);
        return okResult("An email has been sent allowing you to set a new password.");
    }

    public Result resetPassword() throws Exception {
        PasswordReset passwordReset = PasswordReset.fromJson(requestToJSON(request()));
        authenticationService.resetPassword(passwordReset);
        return okResult("Password has been changed.");
    }
}
