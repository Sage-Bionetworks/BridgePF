package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.validators.EmailValidator;
import org.sagebionetworks.bridge.validators.EmailVerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.joda.time.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("authenticationService")
public class AuthenticationService {
    
    private final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private AccountDao accountDao;
    private UserConsentDao userConsentDao;
    private StudyConsentDao studyConsentDao;
    private ParticipantService participantService;
    
    private EmailVerificationValidator verificationValidator;
    private SignInValidator signInValidator;
    private PasswordResetValidator passwordResetValidator;
    private EmailValidator emailValidator;

    @Autowired
    final void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    final void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    final void setEmailVerificationValidator(EmailVerificationValidator validator) {
        this.verificationValidator = validator;
    }
    @Autowired
    final void setSignInValidator(SignInValidator validator) {
        this.signInValidator = validator;
    }
    @Autowired
    final void setPasswordResetValidator(PasswordResetValidator validator) {
        this.passwordResetValidator = validator;
    }
    @Autowired
    final void setEmailValidator(EmailValidator validator) {
        this.emailValidator = validator;
    }
    @Autowired
    final void setUserConsentService(UserConsentDao userConsentDao) {
        this.userConsentDao = userConsentDao;
    }
    @Autowired
    final void setStudyConsentService(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /**
     * This method returns the cached session for the user. A CriteriaContext object is not provided to the method, 
     * and the user's consent status is not re-calculated based on participation in one more more subpopulations. 
     * This only happens when calling session-constructing service methods (signIn and verifyEmail, both of which 
     * return newly constructed sessions).
     * @param sessionToken
     * @return session
     *      the persisted user session calculated on sign in or during verify email workflow
     */
    public UserSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return cacheProvider.getUserSession(sessionToken);
    }
    
    /**
     * This method re-constructs the session based on potential changes to the user. It is called after a user 
     * account is updated, and takes the updated CriteriaContext to calculate the current state of the user.
     * @param study
     *      the user's study
     * @param context
     *      an updated set of criteria for calculating the user's consent status
     * @param userId
     *      the ID of the user
     * @return
     *      newly created session object (not persisted)
     */
    public UserSession updateSession(Study study, CriteriaContext context, String userId) {
        checkNotNull(study);
        checkNotNull(context);
        checkArgument(isNotBlank(userId));
        
        Account account = accountDao.getAccount(study, userId);
        cacheProvider.removeSessionByUserId(userId);
        return getSessionFromAccount(study, context, account);
    }

    public UserSession signIn(Study study, CriteriaContext context, SignIn signIn) throws EntityNotFoundException {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(signIn);

        Validate.entityThrowingException(signInValidator, signIn);

        Account account = accountDao.authenticate(study, signIn);

        UserSession session = getSessionFromAccount(study, context, account);
        cacheProvider.setUserSession(session);
        
        return session;
    }

    public void signOut(final UserSession session) {
        if (session != null) {
            cacheProvider.removeSession(session);
        }
    }

    public IdentifierHolder signUp(Study study, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(participant);
        
        Validate.entityThrowingException(new StudyParticipantValidator(study, true), participant);
        
        try {
            // Since caller has no roles, no roles can be assigned on sign up.
            return participantService.createParticipant(study, NO_CALLER_ROLES, participant, true);
            
        } catch(EntityAlreadyExistsException e) {
            // Suppress this. Otherwise it the response reveals that the email has already been taken, 
            // and you can infer who is in the study from the response. Instead send a reset password 
            // request to the email address in case user has forgotten password and is trying to sign 
            // up again.
            Email email = new Email(study.getIdentifier(), participant.getEmail());
            requestResetPassword(study, email);
            logger.info("Sign up attempt for existing email address in study '"+study.getIdentifier()+"'");
        }
        return null;
    }

    public UserSession verifyEmail(Study study, CriteriaContext context, EmailVerification verification) throws ConsentRequiredException {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(verification);

        Validate.entityThrowingException(verificationValidator, verification);
        
        Account account = accountDao.verifyEmail(study, verification);
        UserSession session = getSessionFromAccount(study, context, account);
        cacheProvider.setUserSession(session);
        return session;
    }
    
    public void resendEmailVerification(StudyIdentifier studyIdentifier, Email email) {
        checkNotNull(studyIdentifier);
        checkNotNull(email);
        
        Validate.entityThrowingException(emailValidator, email);
        try {
            accountDao.resendEmailVerificationToken(studyIdentifier, email);    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            logger.info("Resend email verification for unregistered email in study '"+studyIdentifier.getIdentifier()+"'");
        }
    }

    public void requestResetPassword(Study study, Email email) throws BridgeServiceException {
        checkNotNull(study);
        checkNotNull(email);
        
        Validate.entityThrowingException(emailValidator, email);
        try {
            accountDao.requestResetPassword(study, email);    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            logger.info("Request reset password request for unregistered email in study '"+study.getIdentifier()+"'");
        }
    }

    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        checkNotNull(passwordReset);

        Validate.entityThrowingException(passwordResetValidator, passwordReset);
        
        accountDao.resetPassword(passwordReset);
    }
    
    /**
     * Early in the production lifetime of the application, some accounts were created that have a signature 
     * in Stormpath, but no record in DynamoDB. Some other records had a DynamoDB record in an earlier version 
     * of the table that were not successfully migrated to a later version of the table. These folks should 
     * be in the study but are getting 412s (not consented) from the server. We examine their records on sign 
     * in, and if there is a signature and the consent status does not record the user as consented, we 
     * create the DynamoDB record. (The signatures are from Stormpath and the consent status records are 
     * constructed from DynamoDB).
     */
    private void repairConsents(Account account, UserSession session, CriteriaContext context){
        boolean repaired = false;
        
        // If there is an active signature, there should be a consent record that has not been withdrawn.
        // If there is no consent record (entity not found), or it contains a withdrawal timestamp, 
        // then we need to create a new consent. Even if the consent is the same in every way except it 
        // contains no withdrawal timestamp. 
        Map<SubpopulationGuid,ConsentStatus> statuses = session.getConsentStatuses();
        for (Map.Entry<SubpopulationGuid,ConsentStatus> entry : statuses.entrySet()) {
            
            ConsentSignature activeSignature = account.getActiveConsentSignature(entry.getKey());
            boolean isDynamoRecordMissing = isDynamoRecordMissing(session.getHealthCode(), entry.getKey(), activeSignature);
            
            // The issue is that there is a correctly withdrawn DDB record, but there's an active signature with no withdrawal date.
            if (isDynamoRecordMissing) {
                repairConsent(session, entry.getKey(), activeSignature);
                repaired = true;
            }
        }
        // Recreate these records because they were created from DDB which we've established does not match the signatures
        if (repaired) {
            session.setConsentStatuses(consentService.getConsentStatuses(context));
        }
    }
    
    /**
     * If there's a signed consent signature, is there a matching UserConsent record? The record must exist and not be 
     * withdrawn. 
     * @param healthCode
     * @param subpopGuid
     * @param signature
     * @return
     */
    private boolean isDynamoRecordMissing(String healthCode, SubpopulationGuid subpopGuid, ConsentSignature signature) {
        // There's no signature, so no missing record.
        if (signature == null) {
            return false;
        }
        try {
            UserConsent consent = userConsentDao.getUserConsent(healthCode, subpopGuid, signature.getSignedOn());
            // The signature should never have a withdrewOn value at this point, but for clarity:
            if (signature.getWithdrewOn() == null && consent.getWithdrewOn() != null) {
                return true;
            }
            // Both records exist, both are either withdrawn (shouldn't be possible) or not withdrawn, so it's not missing.
            return false;
        } catch(EntityNotFoundException e) {
            // There's no record, that's bad
        }
        return true;
    }
    
    /**
     * If the signature exists but consentStatus says the user is not consented, then the record is missing in
     * DDB. We need to create it.
     */
    private void repairConsent(UserSession session, SubpopulationGuid subpopGuid, ConsentSignature activeSignature) {
        logger.info("Signature found without a matching user consent record. Adding consent for " + session.getId());
        long signedOn = activeSignature.getSignedOn();
        if (signedOn == 0L) {
            signedOn = DateTimeUtils.currentTimeMillis(); // this is so old we did not record a signing date...
        }
        long consentCreatedOn = studyConsentDao.getActiveConsent(subpopGuid).getCreatedOn(); 
        userConsentDao.giveConsent(session.getHealthCode(), subpopGuid, consentCreatedOn, signedOn);
    }
    
    private UserSession getSessionFromAccount(Study study, CriteriaContext context, Account account) {
        StudyParticipant participant = participantService.getParticipant(study, account, false);
        
        // If the user does not have a language persisted yet, now that we have a session, we can retrieve it 
        // from the context, add it to the user/session, and persist it.
        if (participant.getLanguages().isEmpty() && !context.getLanguages().isEmpty()) {
            participant = new StudyParticipant.Builder().copyOf(participant)
                    .withLanguages(context.getLanguages()).build();
            optionsService.setOrderedStringSet(study, account.getHealthCode(), LANGUAGES, context.getLanguages());
        }
        
        UserSession session = new UserSession(participant);
        // The check for an existing session just prevents resetting the session tokens, the rest of the 
        // session is refreshed. This may change when we expire sessions correctly (currently they are held 
        // for a long time in memory), but this emulates earlier behavior.
        UserSession existingSession = cacheProvider.getUserSessionByUserId(account.getId());
        if (existingSession != null) {
            session.setSessionToken(existingSession.getSessionToken());
            session.setInternalSessionToken(existingSession.getInternalSessionToken());
        } else {
            session.setSessionToken(BridgeUtils.generateGuid());
            session.setInternalSessionToken(BridgeUtils.generateGuid());
        }
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment());
        session.setStudyIdentifier(study.getStudyIdentifier());
        
        CriteriaContext newContext = new CriteriaContext.Builder()
                .withContext(context)
                .withHealthCode(session.getHealthCode()) 
                .withLanguages(session.getParticipant().getLanguages())
                .withUserDataGroups(session.getParticipant().getDataGroups())
                .build();
        
        session.setConsentStatuses(consentService.getConsentStatuses(newContext));
        //repairConsents(account, session, newContext);
        
        return session;
    }
}
