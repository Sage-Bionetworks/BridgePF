package controllers;

import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.JsonUtils;
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

import com.fasterxml.jackson.databind.JsonNode;

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
            JsonNode json = requestToJSON(request());
            SignIn signIn = SignIn.fromJson(json);
            Study study = getStudyOrThrowException(json);
            
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
        JsonNode json = requestToJSON(request());
        SignUp signUp = SignUp.fromJson(json, false);
        Study study = getStudyOrThrowException(json);

        authenticationService.signUp(study, signUp, true);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        JsonNode json = requestToJSON(request());
        EmailVerification emailVerification = EmailVerification.fromJson(json);
        Study study = getStudyOrThrowException(json);
        
        // In normal course of events (verify email, consent to research),
        // an exception is thrown. Code after this line will rarely execute
        UserSession session = authenticationService.verifyEmail(study, emailVerification);
        setSessionToken(session.getSessionToken());
        return okResult(new UserSessionInfo(session));
    }
    
    public Result resendEmailVerification() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = Email.fromJson(json);
        StudyIdentifier studyIdentifier = getStudyIdentifierOrThrowException(json);
        
        authenticationService.resendEmailVerification(studyIdentifier, email);
        return okResult("A request to verify an email address was re-sent.");
    }

    public Result requestResetPassword() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = Email.fromJson(json);
        Study study = getStudyOrThrowException(json);
        
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
    @SuppressWarnings("deprecation")
    private Study getStudyOrThrowException(JsonNode node) {
        String studyId = JsonUtils.asText(node, STUDY_PROPERTY);
        if (isNotBlank(studyId)) {
            return studyService.getStudy(studyId);
        }
        studyId = getStudyIdentifier();
        if (studyId != null) {
            return studyService.getStudy(studyId);
        }
        throw new EntityNotFoundException(Study.class);
    }
    
    @SuppressWarnings("deprecation")
    private StudyIdentifier getStudyIdentifierOrThrowException(JsonNode node) {
        String studyId = JsonUtils.asText(node, STUDY_PROPERTY);
        if (isNotBlank(studyId)) {
            return new StudyIdentifierImpl(studyId);
        }
        studyId = getStudyIdentifier();
        if (studyId != null) {
            return new StudyIdentifierImpl(studyId);
        }
        throw new EntityNotFoundException(Study.class);
    }
}
