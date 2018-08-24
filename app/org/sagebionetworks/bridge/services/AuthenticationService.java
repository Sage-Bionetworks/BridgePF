package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.PasswordGenerator;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.AccountIdValidator;
import org.sagebionetworks.bridge.validators.VerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableSet;

@Component("authenticationService")
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
    
    static final TypeReference<Tuple<String>> TUPLE_TYPE = new TypeReference<Tuple<String>>() {};
    
    public enum ChannelType {
        EMAIL,
        PHONE,
    }
    
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private AccountDao accountDao;
    private ParticipantService participantService;
    private StudyService studyService;
    private PasswordResetValidator passwordResetValidator;
    private AccountWorkflowService accountWorkflowService;
    private IntentService intentService;
    private ExternalIdService externalIdService;

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
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Autowired
    final void setPasswordResetValidator(PasswordResetValidator validator) {
        this.passwordResetValidator = validator;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    @Autowired
    final void setIntentToParticipateService(IntentService intentService) {
        this.intentService = intentService;
    }
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    /**
     * Sign in using a phone number and a token that was sent to that phone number via SMS. 
     */
    public UserSession phoneSignIn(CriteriaContext context, final SignIn signIn) {
        return channelSignIn(ChannelType.PHONE, context, signIn, SignInValidator.PHONE_SIGNIN);
    }
    
    /**
     * Sign in using an email address and a token that was supplied via a message to that email address. 
     */
    public UserSession emailSignIn(CriteriaContext context, final SignIn signIn) {
        return channelSignIn(ChannelType.EMAIL, context, signIn, SignInValidator.EMAIL_SIGNIN);
    }
    
    /**
     * This method returns the cached session for the user. A CriteriaContext object is not provided to the method, 
     * and the user's consent status is not re-calculated based on participation in one more more subpopulations. 
     * This only happens when calling session-constructing service methods (signIn and verifyEmail, both of which 
     * return newly constructed sessions).
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
     * account is updated, and takes the updated CriteriaContext to calculate the current state of the user. We 
     * do not rotate the reauthentication token just because the user updates their session.
     * @param study
     *      the user's study
     * @param context
     *      an updated set of criteria for calculating the user's consent status
     * @return
     *      newly created session object (not persisted)
     */
    public UserSession getSession(Study study, CriteriaContext context) {
        checkNotNull(study);
        checkNotNull(context);

        Account account = accountDao.getAccount(context.getAccountId());
        return getSessionFromAccount(study, context, account);
    }

    public UserSession signIn(Study study, CriteriaContext context, SignIn signIn) throws EntityNotFoundException {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(signIn);
        
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
        
        Account account = accountDao.authenticate(study, signIn);

        UserSession session = getSessionFromAccount(study, context, account);
        // Do not call sessionUpdateService as we assume system is in sync with the session on sign in
        
        if (!session.doesConsent() && intentService.registerIntentToParticipate(study, account)) {
            AccountId accountId = AccountId.forId(study.getIdentifier(), account.getId());
            account = accountDao.getAccount(accountId);
            session = getSessionFromAccount(study, context, account);
        }
        cacheProvider.setUserSession(session);
        
        if (!session.doesConsent() && !session.isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }        
        return session;
    }
    
    public UserSession reauthenticate(Study study, CriteriaContext context, SignIn signIn)
            throws EntityNotFoundException {
        checkNotNull(study);
        checkNotNull(context);
        checkNotNull(signIn);
        
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn);

        // Reauth token is a 21-character alphanumeric (upper, lower, numbers), generated by a SecureRandom. This is
        // 125 bits of entropy. To see if apps are sending the old reauth token after successfully reauthenticating, we
        // will call .hashCode() on the reauth token, mod 1000, and log it (about 10 bits). Even if attackers steal
        // this hash-mod from the logs, they still need to determine the remaining 115 bits of the reauth token (about
        // 19 alphanumeric characters worth), and they have 5 minutes to do it before the grace period expires.
        //
        // This is effectively equivalent to the app submitting an token identification token and a 19-character reauth
        // token, which is still reasonably secure.
        int reauthHashMod = signIn.getReauthToken().hashCode() % 1000;
        LOG.debug("Reauth token hash-mod " + reauthHashMod + " submitted in request " + BridgeUtils.getRequestId());

        CacheKey reauthCacheKey = CacheKey.reauthCacheKey(signIn.getReauthToken(), signIn.getStudyId());
        
        // First look to see if reauthCacheKey is in cache. If it is, return the existing session. This 
        // creates a grace period during which concurrent requests with the same reauth token will work.
        Tuple<String> persisedReauthTuple = cacheProvider.getObject(reauthCacheKey, TUPLE_TYPE);
        if (persisedReauthTuple != null) {
            String sessionToken = persisedReauthTuple.getLeft();
            String reauthToken = persisedReauthTuple.getRight();
            
            // Is it possible for this not to resolve to a session? Play it safe and check
            UserSession session = cacheProvider.getUserSession(sessionToken);
            if (session == null) {
                throw new EntityNotFoundException(Account.class);
            }
            session.setReauthToken(reauthToken);
            return session;
        }
        
        Account account = accountDao.reauthenticate(study, signIn);

        UserSession session = getSessionFromAccount(study, context, account);

        Tuple<String> reauthTuple = new Tuple<>(session.getSessionToken(), session.getReauthToken());
        cacheProvider.setUserSession(session);
        // Longer than the caching of the reauthentication token, which provides a grace period for sign ins.
        cacheProvider.setObject(reauthCacheKey, reauthTuple, BridgeConstants.REAUTH_TOKEN_GRACE_PERIOD_SECONDS);
        
        if (!session.doesConsent() && !session.isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }

    public void signOut(final UserSession session) {
        if (session != null) {
            AccountId accountId = AccountId.forId(session.getStudyIdentifier().getIdentifier(), session.getId());
            accountDao.deleteReauthToken(accountId);
            // session does not have the reauth token so the reauthToken-->sessionToken Redis entry cannot be 
            // removed, but once the reauth token is removed from the user table, the reauth token will no 
            // longer work (and is short-lived in the cache).
            cacheProvider.removeSession(session);
        } 
    }

    public IdentifierHolder signUp(Study study, StudyParticipant participant) {
        checkNotNull(study);
        checkNotNull(participant);
        
        // External ID accounts are created enabled, so we do not allow public API callers to enter random 
        // strings, they must be known and assignable. This logic is not applied to accounts created through 
        // the administrative APIs.
        if (BridgeUtils.isExternalIdAccount(participant) && !study.isExternalIdValidationEnabled()) {
            throw new UnauthorizedException("External ID management is not enabled for this study");
        }

        try {
            // Since caller has no roles, no roles can be assigned on sign up.
            return participantService.createParticipant(study, NO_CALLER_ROLES, participant, true);
        } catch(EntityAlreadyExistsException e) {
            // Suppress this and send an email to notify the user that the account already exists. From 
            // this call, we simply return a 200 the same as any other sign up. Otherwise the response 
            // reveals that the email has been taken.
            AccountId accountId = AccountId.forId(study.getIdentifier(), (String) e.getEntityKeys().get("userId"));
            accountWorkflowService.notifyAccountExists(study, accountId);
            LOG.info("Sign up attempt for existing email address in study '"+study.getIdentifier()+"'");
        }
        return null;
    }

    public void verifyChannel(ChannelType type, Verification verification) {
        checkNotNull(verification);

        Validate.entityThrowingException(VerificationValidator.INSTANCE, verification);
        Account account = accountWorkflowService.verifyChannel(type, verification);
        accountDao.verifyChannel(type, account);
    }
    
    public void resendVerification(ChannelType type, AccountId accountId) {
        checkNotNull(accountId);

        Validate.entityThrowingException(AccountIdValidator.getInstance(type), accountId);
        try {
            accountWorkflowService.resendVerificationToken(type, accountId);    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            LOG.info("Resend " + type.name() + " verification for unregistered email in study '"
                    + accountId.getStudyId() + "'");
        }
    }
    
    public void requestResetPassword(Study study, boolean isStudyAdmin, SignIn signIn) throws BridgeServiceException {
        checkNotNull(study);
        checkNotNull(signIn);
        
        // validate the data in signIn, then convert it to an account ID which we know will be valid.
        Validate.entityThrowingException(SignInValidator.REQUEST_RESET_PASSWORD, signIn);
        try {
            accountWorkflowService.requestResetPassword(study, isStudyAdmin, signIn.getAccountId());    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            LOG.info("Request reset password request for unregistered email in study '"+signIn.getStudyId()+"'");
        }
    }

    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        checkNotNull(passwordReset);

        Validate.entityThrowingException(passwordResetValidator, passwordReset);
        
        accountWorkflowService.resetPassword(passwordReset);
    }
    
    public GeneratedPassword generatePassword(Study study, String externalId, boolean createAccount) {
        checkNotNull(study);
        
        if (!study.isExternalIdValidationEnabled()) {
            throw new BadRequestException("External ID management disabled for this study");
        }
        if (StringUtils.isBlank(externalId)) {
            throw new BadRequestException("External ID is required");
        }
        ExternalIdentifier externalIdentifier = externalIdService.getExternalId(
                study.getStudyIdentifier(), externalId);        
        if (externalIdentifier == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        AccountId accountId = AccountId.forExternalId(study.getIdentifier(), externalId);
        Account account = accountDao.getAccount(accountId);
        
        // No account and user doesn't want to create it, treat as a 404
        if (account == null && !createAccount) {
            throw new EntityNotFoundException(Account.class);
        }

        String password = generatePassword(study.getPasswordPolicy().getMinLength());
        String userId;
        if (account == null) {
            // Create an account with password and external ID assigned. If the external ID has been 
            // assigned to another account, this creation will fail (external ID is a unique column).
            StudyParticipant participant = new StudyParticipant.Builder()
                    .withExternalId(externalId).withPassword(password).build();
            userId = participantService.createParticipant(
                    study, ImmutableSet.of(), participant, false).getIdentifier();
        } else {
            // Account exists, so rotate the password
            accountDao.changePassword(account, null, password);
            userId = account.getId();
        }
        // Return the password and the user ID in case the account was just created.
        return new GeneratedPassword(externalId, userId, password);
    }
    
    public String generatePassword(int policyLength) {
        return PasswordGenerator.INSTANCE.nextPassword(Math.max(32, policyLength));
    }
    
    private UserSession channelSignIn(ChannelType channelType, CriteriaContext context, SignIn signIn,
            Validator validator) {
        
        // Throws AuthenticationFailedException if the token is missing or incorrect
        AccountId accountId = accountWorkflowService.channelSignIn(channelType, context, signIn, validator);
        
        Account account = accountDao.getAccountAfterAuthentication(accountId);
        // This should be unlikely, but if someone deleted the account while the token was outstanding
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        // Update account state before we create the session, so it's accurate...
        accountDao.verifyChannel(channelType, account);

        Study study = studyService.getStudy(signIn.getStudyId());
        UserSession session = getSessionFromAccount(study, context, account);
        
        if (!session.doesConsent() && intentService.registerIntentToParticipate(study, account)) {
            account = accountDao.getAccount(accountId);
            session = getSessionFromAccount(study, context, account);
        }
        cacheProvider.setUserSession(session);
        if (!session.doesConsent() && !session.isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }

    /**
     * Constructs a session based on the user's account, participant, and request context. This is called by sign-in
     * APIs, which creates the session. Package-scoped for unit tests.
     */
    UserSession getSessionFromAccount(Study study, CriteriaContext context, Account account) {
        StudyParticipant participant = participantService.getParticipant(study, account, false);

        // If the user does not have a language persisted yet, now that we have a session, we can retrieve it 
        // from the context, add it to the user/session, and persist it.
        if (participant.getLanguages().isEmpty() && !context.getLanguages().isEmpty()) {
            participant = new StudyParticipant.Builder().copyOf(participant)
                    .withLanguages(context.getLanguages()).build();
            
            // Note that the context does not have the healthCode, you must use the participant
            accountDao.editAccount(study.getStudyIdentifier(), participant.getHealthCode(),
                    accountToEdit -> accountToEdit.setLanguages(context.getLanguages()));
        }

        // As per https://sagebionetworks.jira.com/browse/BRIDGE-2127, signing in should invalidate any old sessions.
        // This code is only called by signIn calls (signIn, reauth, channelSignIn, getSession() which is only called
        // by create user), and is called by _every_ signIn call and is responsible for creating the new session. This
        // is the best place to delete the old session (if it exists).
        cacheProvider.removeSessionByUserId(account.getId());

        // Create new session.
        UserSession session = new UserSession(participant);
        session.setSessionToken(BridgeUtils.generateGuid());
        session.setInternalSessionToken(BridgeUtils.generateGuid());
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment());
        session.setIpAddress(context.getIpAddress());
        session.setStudyIdentifier(study.getStudyIdentifier());
        session.setReauthToken(account.getReauthToken());
        
        CriteriaContext newContext = updateContextFromSession(context, session);
        session.setConsentStatuses(consentService.getConsentStatuses(newContext, account));
        
        return session;
    }

    // Sign-in methods contain a criteria context that includes no user information. After signing in, we need to
    // create an updated context with user info.
    private static CriteriaContext updateContextFromSession(CriteriaContext originalContext, UserSession session) {
        return new CriteriaContext.Builder()
                .withContext(originalContext)
                .withHealthCode(session.getHealthCode())
                .withLanguages(session.getParticipant().getLanguages())
                .withUserDataGroups(session.getParticipant().getDataGroups())
                .withUserId(session.getId())
                .build();
    }
}
