package org.sagebionetworks.bridge.play.controllers;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Identifier;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

@Controller
public class AuthenticationController extends BaseController {

    private AccountWorkflowService accountWorkflowService;
    
    @Autowired
    final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    
    public Result requestEmailSignIn() { 
        SignIn signInRequest = parseJson(request(), SignIn.class);
        
        accountWorkflowService.requestEmailSignIn(signInRequest);
        
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
        UserSession session = null;
        try {
            session = authenticationService.emailSignIn(context, signInRequest);
        } catch(ConsentRequiredException e) {
            setCookieAndRecordMetrics(e.getUserSession());
            throw e;
        }
        setCookieAndRecordMetrics(session);

        return okResult(UserSessionInfo.toJSON(session));
    }

    public Result requestPhoneSignIn() {
        SignIn signInRequest = parseJson(request(), SignIn.class);
        
        accountWorkflowService.requestPhoneSignIn(signInRequest);

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
        
        UserSession session = null;
        try {
            session = authenticationService.phoneSignIn(context, signInRequest);
        } catch(ConsentRequiredException e) {
            setCookieAndRecordMetrics(e.getUserSession());
            throw e;
        }
        setCookieAndRecordMetrics(session);

        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result signIn() throws Exception {
        UserSession session = getSessionIfItExists();
        if (session == null) {
            SignIn signIn = parseJson(request(), SignIn.class);
            Study study = studyService.getStudy(signIn.getStudyId());
            verifySupportedVersionOrThrowException(study);

            CriteriaContext context = getCriteriaContext(study.getStudyIdentifier());
            
            try {
                session = authenticationService.signIn(study, context, signIn);
            } catch(ConsentRequiredException e) {
                setCookieAndRecordMetrics(e.getUserSession());
                throw e;
            }
        }
        setCookieAndRecordMetrics(session);
        return okResult(UserSessionInfo.toJSON(session));
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
        
        setCookieAndRecordMetrics(session);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    @Deprecated
    public Result signInV3() throws Exception {
        // Email based sign in with throw UnauthorizedException if email-only sign in is disabled.
        // This maintains backwards compatibility for older clients.
        try {
            return signIn();
        } catch(UnauthorizedException e) {
            throw new EntityNotFoundException(Account.class);
        }
    }

    @Deprecated
    @BodyParser.Of(BodyParser.Empty.class)
    public Result signOut() throws Exception {
        final UserSession session = getSessionIfItExists();
        // Always set, even if we eventually decide to return an error code when there's no session
        if (session != null) {
            authenticationService.signOut(session);
        }
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        return okResult("Signed out.");
    }

    @BodyParser.Of(BodyParser.Empty.class)
    public Result signOutV4() throws Exception {
        final UserSession session = getSessionIfItExists();
        // Always set, even if we eventually decide to return an error code when there's no session
        response().discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        response().setHeader(BridgeConstants.CLEAR_SITE_DATA_HEADER, BridgeConstants.CLEAR_SITE_DATA_VALUE);
        if (session != null) {
            authenticationService.signOut(session);
        } else {
            throw new BadRequestException("Not signed in");
        }
        return okResult("Signed out.");
    }
    
    public Result signUp() throws Exception {
        JsonNode node = requestToJSON(request());
        StudyParticipant participant = MAPPER.treeToValue(node, StudyParticipant.class);
        
        boolean checkForConsent = JsonUtils.asBoolean(node, "checkForConsent");
        
        String studyId = JsonUtils.asText(node, BridgeConstants.STUDY_PROPERTY);
        Study study = getStudyOrThrowException(studyId);
        authenticationService.signUp(study, participant, checkForConsent);
        return createdResult("Signed up.");
    }

    public Result verifyEmail() throws Exception {
        Verification verification = parseJson(request(), Verification.class);

        authenticationService.verifyChannel(ChannelType.EMAIL, verification);
        
        return okResult("Email address verified.");
    }

    public Result resendEmailVerification() throws Exception {
        Identifier identifier = parseJson(request(), Identifier.class);
        getStudyOrThrowException(identifier.getStudyIdentifier());
        
        authenticationService.resendVerification(ChannelType.EMAIL, identifier);
        return okResult("If registered with the study, we'll email you instructions on how to verify your account.");
    }

    public Result verifyPhone() throws Exception {
        Verification verification = parseJson(request(), Verification.class);

        authenticationService.verifyChannel(ChannelType.PHONE, verification);
        
        return okResult("Phone number verified.");
    }

    public Result resendPhoneVerification() throws Exception {
        Identifier identifier = parseJson(request(), Identifier.class);
        getStudyOrThrowException(identifier.getStudyIdentifier());
        
        authenticationService.resendVerification(ChannelType.PHONE, identifier);
        return okResult("If registered with the study, we'll send an SMS message to your phone.");
    }
    
    public Result requestResetPassword() throws Exception {
        SignIn signIn = parseJson(request(), SignIn.class);
        
        Study study = studyService.getStudy(signIn.getStudyId());
        verifySupportedVersionOrThrowException(study);
        
        authenticationService.requestResetPassword(study, signIn);

        return okResult("If registered with the study, we'll send you instructions on how to change your password.");
    }

    public Result resetPassword() throws Exception {
        PasswordReset passwordReset = parseJson(request(), PasswordReset.class);
        getStudyOrThrowException(passwordReset.getStudyIdentifier());
        authenticationService.resetPassword(passwordReset);
        return okResult("Password has been changed.");
    }

    private void setCookieAndRecordMetrics(UserSession session) {
        writeSessionInfoToMetrics(session);  
        // We have removed the cookie in the past, only to find out that clients were unknowingly
        // depending on the cookie to preserve the session token. So it remains.
        boolean useSsl = bridgeConfig.getEnvironment() != Environment.LOCAL;
        response().setCookie(BridgeConstants.SESSION_TOKEN_HEADER, session.getSessionToken(),
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/",
                bridgeConfig.get("domain"), useSsl, useSsl);
        
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
    private Study getStudyOrThrowException(StudyIdentifier studyId) {
        Study study = studyService.getStudy(studyId);
        verifySupportedVersionOrThrowException(study);
        return study;
    }
    
    private Study getStudyOrThrowException(String studyId) {
        Study study = studyService.getStudy(studyId);
        verifySupportedVersionOrThrowException(study);
        return study;
    }
}
