package org.sagebionetworks.bridge.play.controllers;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_PROPERTY;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

@Controller
public class AuthenticationController extends BaseController {

    public Result requestEmailSignIn() { 
        SignIn signInRequest = parseJson(request(), SignIn.class);
        
        authenticationService.requestEmailSignIn(signInRequest);
        
        return acceptedResult("Email sent.");
    }
    
    public Result emailSignIn() { 
        SignIn signInRequest = parseJson(request(), SignIn.class);

        if (isBlank(signInRequest.getStudyId())) {
            throw new BadRequestException("Study identifier is required.");
        }
        Study study = studyService.getStudy(signInRequest.getStudyId());
        verifySupportedVersionOrThrowException(study);
        
        CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
        
        UserSession session = authenticationService.emailSignIn(context, signInRequest);
        logAuthenticationSuccess(session);

        return okResult(UserSessionInfo.toJSON(session));
    }

    public Result requestPhoneSignIn() {
        SignIn signInRequest = parseJson(request(), SignIn.class);
        
        authenticationService.requestPhoneSignIn(signInRequest);

        return acceptedResult("Message sent.");
    }

    public Result phoneSignIn() {
        SignIn signInRequest = parseJson(request(), SignIn.class);

        if (isBlank(signInRequest.getStudyId())) {
            throw new BadRequestException("Study identifier is required.");
        }
        Study study = studyService.getStudy(signInRequest.getStudyId());
        verifySupportedVersionOrThrowException(study);
        
        CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
        
        UserSession session = authenticationService.phoneSignIn(context, signInRequest);
        logAuthenticationSuccess(session);

        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result signInV4() throws Exception {
        return signInWithRetry(5);
    }

    public Result reauthenticate() throws Exception {
        SignIn signInRequest = parseJson(request(), SignIn.class);

        if (isBlank(signInRequest.getStudyId())) {
            throw new BadRequestException("Study identifier is required.");
        }
        Study study = studyService.getStudy(signInRequest.getStudyId());
        verifySupportedVersionOrThrowException(study);
        
        CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
        UserSession session = authenticationService.reauthenticate(study, context, signInRequest);
        
        logAuthenticationSuccess(session);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result signInV3() throws Exception {
        try {
            return signInV4();
        } catch(UnauthorizedException e) {
            throw new EntityNotFoundException(Account.class);
        }
    }

    @BodyParser.Of(BodyParser.Empty.class)
    public Result signOut() throws Exception {
        final UserSession session = getSessionIfItExists();
        if (session != null) {
            authenticationService.signOut(session);
        }
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        return okResult("Signed out.");
    }

    public Result signUp() throws Exception {
        JsonNode node = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);
        
        boolean checkForConsent = JsonUtils.asBoolean(node, "checkForConsent");
        
        Study study = getStudyOrThrowException(node);
        authenticationService.signUp(study, participant, checkForConsent);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        EmailVerification emailVerification = parseJson(request(), EmailVerification.class);

        authenticationService.verifyEmail(emailVerification);
        
        return okResult("Email address verified.");
    }

    public Result resendEmailVerification() throws Exception {
        JsonNode json = requestToJSON(request());
        Email email = parseJson(request(), Email.class);
        Study study = getStudyOrThrowException(json);
        authenticationService.resendEmailVerification(study.getStudyIdentifier(), email);
        return okResult("If registered with the study, we'll email you instructions on how to verify your account.");
    }

    public Result requestResetPassword() throws Exception {
        SignIn signIn = parseJson(request(), SignIn.class);
        
        Study study = studyService.getStudy(signIn.getStudyId());
        verifySupportedVersionOrThrowException(study);
        
        authenticationService.requestResetPassword(study, signIn);

        return okResult("If registered with the study, we'll send you instructions on how to change your password.");
    }

    public Result resetPassword() throws Exception {
        JsonNode json = requestToJSON(request());
        PasswordReset passwordReset = parseJson(request(), PasswordReset.class);
        getStudyOrThrowException(json);
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
            SignIn signIn = parseJson(request(), SignIn.class);
            Study study = studyService.getStudy(signIn.getStudyId());
            verifySupportedVersionOrThrowException(study);

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
        }
        logAuthenticationSuccess(session);

        // You can proceed if 1) you're some kind of system administrator (developer, researcher), or 2)
        // you've consented to research.
        if (!session.doesConsent() && !session.isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return okResult(UserSessionInfo.toJSON(session));
    }

    private void logAuthenticationSuccess(UserSession session) {
        writeSessionInfoToMetrics(session);  
        // We have removed the cookie in the past, only to find out that clients were unknowingly
        // depending on the cookie to preserve the session token. So it remains.
        response().setCookie(BridgeConstants.SESSION_TOKEN_HEADER, session.getSessionToken(),
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
        
        RequestInfo requestInfo = getRequestInfoBuilder(session)
                .withSignedInOn(DateUtils.getCurrentDateTime()).build();
        cacheProvider.updateRequestInfo(requestInfo);
    }

    /**
     * Unauthenticated calls that require a study (most of the calls not requiring authentication, including this one),
     * should include the study identifier as part of the JSON payload. This call handles such JSON and converts it to a
     * study. As a fallback for existing clients, it also looks for the study information in the query string or
     * headers. If the study cannot be found in any of these places, it throws an exception, because the API will not
     * work correctly without it.
     */
    private Study getStudyOrThrowException(JsonNode node) {
        String studyId = getStudyStringOrThrowException(node);
        Study study = studyService.getStudy(studyId);
        verifySupportedVersionOrThrowException(study);
        return study;
    }

    private String getStudyStringOrThrowException(JsonNode node) {
        String studyId = JsonUtils.asText(node, STUDY_PROPERTY);
        if (isNotBlank(studyId)) {
            return studyId;
        }
        throw new EntityNotFoundException(Study.class);
    }
}
