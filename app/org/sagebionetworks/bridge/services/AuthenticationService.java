package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.validators.EmailValidator;
import org.sagebionetworks.bridge.validators.EmailVerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.StudyParticipantValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component("authenticationService")
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
    
    public static enum ChannelType {
        EMAIL,
        PHONE;
    }
    
    private static final String EMAIL_SIGNIN_REQUEST_KEY = "%s:%s:signInRequest";
    private static final String PHONE_SIGNIN_REQUEST_KEY = "%s:%s:phoneSignInRequest";
    private static final int SESSION_SIGNIN_TIMEOUT = 60*5; // 5 minutes
    
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private AccountDao accountDao;
    private ParticipantService participantService;
    private SendMailService sendMailService;
    private StudyService studyService;
    private PasswordResetValidator passwordResetValidator;
    private AccountWorkflowService accountWorkflowService;
    private IntentService intentService;
    private NotificationsService notificationsService;
    private final AtomicLong emailSignInRequestInMillis = new AtomicLong(200L);
    private final AtomicLong phoneSignInRequestInMillis = new AtomicLong(200L);

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
    final void setPasswordResetValidator(PasswordResetValidator validator) {
        this.passwordResetValidator = validator;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
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
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    final AtomicLong getEmailSignInRequestInMillis() {
        return emailSignInRequestInMillis;
    }
    final AtomicLong getPhoneSignInRequestInMillis() {
        return phoneSignInRequestInMillis;
    }
    
    /**
     * Request a token to be sent via SMS to the user, that can be used to start a session on the Bridge server.
     */
    public void requestPhoneSignIn(final SignIn signIn) {
        requestChannelSignIn(ChannelType.PHONE, signIn, SignInValidator.PHONE_SIGNIN_REQUEST, phoneSignInRequestInMillis, () -> {
            return getPhoneSignInCacheKey(signIn.getPhone(), signIn.getStudyId());
        }, () -> {
            return getPhoneToken();
        }, (study, token) -> {
            // Put a space in the token so it's easier to enter into the UI
            String formattedToken = token.substring(0,3) + "-" + token.substring(3,6); 
            String appName = (study.getShortName() != null) ? study.getShortName() : "Bridge";
            String message = "Enter " + formattedToken + " to sign in to " + appName;
            
            notificationsService.sendSMSMessage(study.getStudyIdentifier(), signIn.getPhone(), message);
        });
    }
    
    /**
     * Sign in using a phone number and a token that was sent to that phone number via SMS. 
     */
    public UserSession phoneSignIn(CriteriaContext context, final SignIn signIn) {
        return channelSignIn(ChannelType.PHONE, context, signIn, SignInValidator.PHONE_SIGNIN, () -> {
            return getPhoneSignInCacheKey(signIn.getPhone(), signIn.getStudyId());
        });
    }
    
    /**
     * Request a token to be sent via a link in an email message, that can be used to start a session on the Bridge server. 
     * The installed application should intercept this link in order to complete the transaction within the app, where the 
     * returned session can be captured. If the link is not captured, it retrieves a test page on the Bridge server as 
     * configured by default. That test page will complete the transaction and return a session token.
     */
    public void requestEmailSignIn(final SignIn signIn) {
        requestChannelSignIn(ChannelType.EMAIL, signIn, SignInValidator.EMAIL_SIGNIN_REQUEST, emailSignInRequestInMillis, () -> {
            return getEmailSignInCacheKey(signIn.getEmail(), signIn.getStudyId());
        }, () -> {
            return getEmailToken();
        }, (study, token) -> {
            BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withEmailTemplate(study.getEmailSignInTemplate())
                .withStudy(study)
                .withRecipientEmail(signIn.getEmail())
                .withToken("email", BridgeUtils.encodeURIComponent(signIn.getEmail()))
                .withToken("token", token).build();
            sendMailService.sendEmail(provider);
        });
    }
    
    /**
     * Sign in using an email address and a token that was supplied via a message to that email address. 
     */
    public UserSession emailSignIn(CriteriaContext context, final SignIn signIn) {
        return channelSignIn(ChannelType.EMAIL, context, signIn, SignInValidator.EMAIL_SIGNIN, () -> {
            return getEmailSignInCacheKey(signIn.getEmail(), signIn.getStudyId());
        });
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

        Account account = accountDao.reauthenticate(study, signIn);

        // Force recreation of the session, including the session token
        cacheProvider.removeSessionByUserId(account.getId());
        UserSession session = getSessionFromAccount(study, context, account);
        cacheProvider.setUserSession(session);
        
        if (!session.doesConsent() && !session.isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }

    public void signOut(final UserSession session) {
        if (session != null) {
            AccountId accountId = AccountId.forId(session.getStudyIdentifier().getIdentifier(), session.getId());
            accountDao.signOut(accountId);
            cacheProvider.removeSession(session);
        }
    }

    public IdentifierHolder signUp(Study study, StudyParticipant participant, boolean checkForConsent) {
        checkNotNull(study);
        checkNotNull(participant);
        
        Validate.entityThrowingException(new StudyParticipantValidator(study, true), participant);
        
        try {
            // Since caller has no roles, no roles can be assigned on sign up.
            IdentifierHolder holder = participantService.createParticipant(study, NO_CALLER_ROLES, participant, true);
            if (checkForConsent) {
                // Check to see if this user has saved consent records for the study. Consent them now, so sign in  
                // will not return 412 (consent required). Need to retrieve the full participant record (w/ healthCode).
                StudyParticipant updatedParticipant = participantService.getParticipant(study, holder.getIdentifier(), false);
                intentService.registerIntentToParticipate(study, updatedParticipant);
            }
            return holder;
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

    public void verifyEmail(EmailVerification verification) {
        checkNotNull(verification);

        Validate.entityThrowingException(EmailVerificationValidator.INSTANCE, verification);
        Account account = accountWorkflowService.verifyEmail(verification);
        accountDao.verifyEmail(account);
    }
    
    public void resendEmailVerification(StudyIdentifier studyIdentifier, Email email) {
        checkNotNull(studyIdentifier);
        checkNotNull(email);
        
        Validate.entityThrowingException(EmailValidator.INSTANCE, email);
        try {
            AccountId accountId = AccountId.forEmail(studyIdentifier.getIdentifier(), email.getEmail());
            accountWorkflowService.resendEmailVerificationToken(accountId);    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            LOG.info("Resend email verification for unregistered email in study '"+studyIdentifier.getIdentifier()+"'");
        }
    }

    public void requestResetPassword(Study study, SignIn signIn) throws BridgeServiceException {
        checkNotNull(study);
        checkNotNull(signIn);
        
        // validate the data in signIn, then convert it to an account ID which we know will be valid.
        Validate.entityThrowingException(SignInValidator.REQUEST_RESET_PASSWORD, signIn);
        try {
            accountWorkflowService.requestResetPassword(study, signIn.getAccountId());    
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
    
    protected String getEmailToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }
    
    protected String getPhoneToken() {
        return SecureTokenGenerator.PHONE_CODE_INSTANCE.nextToken();
    }

    private void requestChannelSignIn(ChannelType channelType, SignIn signIn, Validator validator,
            AtomicLong atomicLong, Supplier<String> cacheKeySupplier, Supplier<String> tokenSupplier,
            BiConsumer<Study, String> messageSender) {
        long startTime = System.currentTimeMillis();
        Validate.entityThrowingException(validator, signIn);

        // We use the study so it's existence is verified. We retrieve the account so we verify it
        // exists as well. If the token is returned to the server, we can safely use the credentials 
        // in the persisted SignIn object.        
        Study study = studyService.getStudy(signIn.getStudyId());

        // Do we want the same flag for phone? Do we want to eliminate this flag?
        if (channelType == ChannelType.EMAIL && !study.isEmailSignInEnabled()) {
            throw new UnauthorizedException("Email-based sign in not enabled for study: " + study.getName());
        }

        String cacheKey = cacheKeySupplier.get();
        // check that the account exists, return quietly if not to prevent account enumeration attacks
        if (accountDao.getAccount(signIn.getAccountId()) == null) {
            try {
                // The not found case returns *much* faster than the normal case. To prevent account enumeration 
                // attacks, measure time of a successful case and delay for that period before returning.
                TimeUnit.MILLISECONDS.sleep(atomicLong.get());            
            } catch(InterruptedException e) {
                // Just return, the thread was killed by the connection, the server died, etc.
            }
            return;
        }
        String token = cacheProvider.getObject(cacheKey, String.class);
        if (token == null) {
            token = tokenSupplier.get();
            cacheProvider.setObject(cacheKey, token, SESSION_SIGNIN_TIMEOUT);
        }

        messageSender.accept(study, token);
        atomicLong.set(System.currentTimeMillis()-startTime);
    }
    
    private UserSession channelSignIn(ChannelType channelType, CriteriaContext context, SignIn signIn,
            Validator validator, Supplier<String> cacheKeySupplier) {
        Validate.entityThrowingException(validator, signIn);
        
        Study study = studyService.getStudy(signIn.getStudyId());
        String cacheKey = cacheKeySupplier.get();
        
        String storedToken = cacheProvider.getObject(cacheKey, String.class);
        if (storedToken == null || !storedToken.equals(signIn.getToken())) {
            throw new AuthenticationFailedException();
        }
        // Consume the key regardless of what happens
        cacheProvider.removeObject(cacheKey);
        
        Account account = accountDao.getAccountAfterAuthentication(signIn.getAccountId());
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        // Update account state before we create the session, so it's accurate...
        accountDao.verifyChannel(channelType, account);

        UserSession session = getSessionFromAccount(study, context, account);
        cacheProvider.setUserSession(session);
        
        if (!session.doesConsent() && !session.isInRole(Roles.ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return session;
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
        session.setReauthToken(account.getReauthToken());
        
        CriteriaContext newContext = new CriteriaContext.Builder()
                .withContext(context)
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withLanguages(session.getParticipant().getLanguages())
                .withUserDataGroups(session.getParticipant().getDataGroups())
                .build();
        
        session.setConsentStatuses(consentService.getConsentStatuses(newContext));
        
        return session;
    }
    
    private String getPhoneSignInCacheKey(Phone phone, String studyId) {
        return String.format(PHONE_SIGNIN_REQUEST_KEY, phone.getNumber(), studyId);
    }
    
    private String getEmailSignInCacheKey(String email, String studyId) {
        return String.format(EMAIL_SIGNIN_REQUEST_KEY, email, studyId);
    }
}
