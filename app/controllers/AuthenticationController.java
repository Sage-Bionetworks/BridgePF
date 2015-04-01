package controllers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;

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
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

@Controller("authenticationController")
public class AuthenticationController extends BaseController {

    public Result signIn() throws Exception {
        UserSession session = getSessionIfItExists();
        if (session != null) {
            setSessionToken(session.getSessionToken());
            return okResult(new UserSessionInfo(session));
        }
        try {
            JsonNode json = requestToJSON(request());
            SignIn signIn = parseJson(request(), SignIn.class);
            Study study = getStudyOrThrowException(json);
            
            session = authenticationService.signIn(study, signIn);
            setSessionToken(session.getSessionToken());
            Result result = okResult(new UserSessionInfo(session));
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
        signUp.getRoles().clear();
        
        Study study = getStudyOrThrowException(json);

        authenticationService.signUp(study, signUp, true);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        JsonNode json = requestToJSON(request());
        EmailVerification emailVerification = parseJson(request(), EmailVerification.class);
        Study study = getStudyOrThrowException(json);
        
        // In normal course of events (verify email, consent to research),
        // an exception is thrown. Code after this line will rarely execute
        UserSession session = authenticationService.verifyEmail(study, emailVerification);
        setSessionToken(session.getSessionToken());
        return okResult(new UserSessionInfo(session));
    }
    
    public Result resendEmailVerification() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = parseJson(request(), Email.class);
        StudyIdentifier studyIdentifier = getStudyIdentifierOrThrowException(json);
        
        authenticationService.resendEmailVerification(studyIdentifier, email);
        return okResult("A request to verify an email address was re-sent.");
    }

    public Result requestResetPassword() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = parseJson(request(), Email.class);
        Study study = getStudyOrThrowException(json);
        
        authenticationService.requestResetPassword(study, email);
        return okResult("An email has been sent allowing you to set a new password.");
    }

    public Result resetPassword() throws Exception {
        PasswordReset passwordReset = parseJson(request(), PasswordReset.class);
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
    private Study getStudyOrThrowException(JsonNode node) {
        String studyId = getStudyStringOrThrowException(node);
        return studyService.getStudy(studyId);
    }
    
    private StudyIdentifier getStudyIdentifierOrThrowException(JsonNode node) {
        String studyId = getStudyStringOrThrowException(node);
        return new StudyIdentifierImpl(studyId);
    }
    
    @SuppressWarnings("deprecation")
    private String getStudyStringOrThrowException(JsonNode node) {
        String studyId = JsonUtils.asText(node, STUDY_PROPERTY);
        if (isNotBlank(studyId)) {
            return studyId;
        }
        studyId = getStudyIdentifier();
        if (studyId != null) {
            return studyId;
        }
        throw new EntityNotFoundException(Study.class);
    }
}
