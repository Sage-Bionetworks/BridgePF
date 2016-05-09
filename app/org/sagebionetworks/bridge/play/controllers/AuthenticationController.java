package org.sagebionetworks.bridge.play.controllers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

@Controller
public class AuthenticationController extends BaseController {

    public Result signIn() throws Exception {
        return signInWithRetry(5);
    }

    public Result signOut() throws Exception {
        final UserSession session = getSessionIfItExists();
        if (session != null) {
            authenticationService.signOut(session);
        }
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        return okResult("Signed out.");
    }

    public Result signUp() throws Exception {
        JsonNode json = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        
        Study study = getStudyOrThrowException(json);
        authenticationService.signUp(study, participant);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        JsonNode json = requestToJSON(request());
        EmailVerification emailVerification = parseJson(request(), EmailVerification.class);
        Study study = getStudyOrThrowException(json);

        CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
        
        UserSession session = authenticationService.verifyEmail(study, context, emailVerification);
        writeSessionInfoToMetrics(session);
        setSessionToken(session.getSessionToken());

        // In normal course of events (verify email, consent to research),
        // an exception is thrown. Code after this line will rarely execute
        if (!session.getUser().doesConsent()) {
            throw new ConsentRequiredException(session);
        }

        return okResult(UserSessionInfo.toJSON(session));
    }

    public Result resendEmailVerification() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = parseJson(request(), Email.class);
        StudyIdentifier studyIdentifier = getStudyIdentifierOrThrowException(json);
        authenticationService.resendEmailVerification(studyIdentifier, email);
        return okResult("If registered with the study, we'll email you instructions on how to verify your account.");
    }

    public Result requestResetPassword() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = parseJson(request(), Email.class);
        Study study = getStudyOrThrowException(json);
        authenticationService.requestResetPassword(study, email);
        return okResult("If registered with the study, we'll email you instructions on how to change your password.");
    }

    public Result resetPassword() throws Exception {
        PasswordReset passwordReset = parseJson(request(), PasswordReset.class);
        authenticationService.resetPassword(passwordReset);
        return okResult("Password has been changed.");
    }

    /**
     * Retries sign-in on lock.
     *
     * @param retryCounter the number of retries, excluding the initial try
     */
    private Result signInWithRetry(int retryCounter) throws Exception {
        UserSession session = getSessionIfItExists();
        if (session == null) {
            JsonNode json = requestToJSON(request());
            SignIn signIn = parseJson(request(), SignIn.class);
            Study study = getStudyOrThrowException(json);

            CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
            
            try {
                session = authenticationService.signIn(study, context, signIn);
            } catch (ConcurrentModificationException e) {
                if (retryCounter > 0) {
                    final long retryDelayInMillis = 200;
                    Thread.sleep(retryDelayInMillis);
                    return signInWithRetry(retryCounter - 1);
                }
                throw e;
            }
            writeSessionInfoToMetrics(session);
        }

        // Set session token. This way, even if we get a ConsentRequiredException, users are still able to sign consent
        setSessionToken(session.getSessionToken());

        // You can proceed if 1) you're some kind of system administrator (developer, researcher), or 2)
        // you've consented to research.
        if (!session.getUser().doesConsent() && !session.getUser().isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }

        return okResult(UserSessionInfo.toJSON(session));
    }

    /**
     * Unauthenticated calls that require a study (most of the calls not requiring authentication, including this one),
     * should include the study identifier as part of the JSON payload. This call handles such JSON and converts it to a
     * study. As a fallback for existing clients, it also looks for the study information in the query string or
     * headers. If the study cannot be found in any of these places, it throws an exception, because the API will not
     * work correctly without it.
     * 
     * @param node
     * @return
     */
    private Study getStudyOrThrowException(JsonNode node) {
        String studyId = getStudyStringOrThrowException(node);
        Study study = studyService.getStudy(studyId);
        verifySupportedVersionOrThrowException(study);
        return study;
    }

    private StudyIdentifier getStudyIdentifierOrThrowException(JsonNode node) {
        String studyId = getStudyStringOrThrowException(node);
        return new StudyIdentifierImpl(studyId);
    }

    private String getStudyStringOrThrowException(JsonNode node) {
        String studyId = JsonUtils.asText(node, STUDY_PROPERTY);
        if (isNotBlank(studyId)) {
            return studyId;
        }
        throw new EntityNotFoundException(Study.class);
    }
}
