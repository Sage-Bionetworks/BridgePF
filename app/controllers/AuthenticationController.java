package controllers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller("authenticationController")
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
            Study study = studyService.getStudy(getStudyIdentifier());
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
        Study study = studyService.getStudy(getStudyIdentifier());
        authenticationService.signUp(study, signUp, true);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        Study study = studyService.getStudy(getStudyIdentifier());
        EmailVerification ev = EmailVerification.fromJson(requestToJSON(request()));
        // In normal course of events (verify email, consent to research),
        // an exception is thrown. Code after this line will rarely execute
        UserSession session = authenticationService.verifyEmail(study, ev);
        setSessionToken(session.getSessionToken());
        return okResult(new UserSessionInfo(session));
    }
    
    public Result resendEmailVerification() throws Exception {
        Email email = Email.fromJson(requestToJSON(request()));
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(getStudyIdentifier());
        
        authenticationService.resendEmailVerification(studyIdentifier, email);
        return okResult("A request to verify an email address was re-sent.");
    }

    public Result requestResetPassword() throws Exception {
        Email email = Email.fromJson(requestToJSON(request()));
        Study study = getStudyOrThrowException(email);
        
        authenticationService.requestResetPassword(study, email);
        return okResult("An email has been sent allowing you to set a new password.");
    }

    public Result resetPassword() throws Exception {
        PasswordReset passwordReset = PasswordReset.fromJson(requestToJSON(request()));
        authenticationService.resetPassword(passwordReset);
        return okResult("Password has been changed.");
    }
    
    /**
     * Unauthenticated calls that require a study (most of the calls not requiring authentication, including this one),
     * should include the study identifier as part of the JSON payload. This call handles such JSON and converts it to a
     * study. As a fallback for existing clients, it also looks for the study information in the query string or
     * headers. If the study cannot be found in any of these places, it throws an exception, because the API will not
     * work correctly without it.
     * 
     * @param email
     * @return
     */
    private Study getStudyOrThrowException(Email email) {
        if (email.getStudyIdentifier() != null) {
            return studyService.getStudy(email.getStudyIdentifier());
        }
        String studyString = getStudyIdentifier();
        if (studyString != null) {
            return studyService.getStudy(studyString);
        }
        throw new EntityNotFoundException(Study.class);
    }
}
