package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.sagebionetworks.bridge.validators.EmailValidator;
import org.sagebionetworks.bridge.validators.EmailVerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.SignUpValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("authenticationService")
public class AuthenticationServiceImpl implements AuthenticationService {
    
    private final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private final int LOCK_EXPIRE_IN_SECONDS = 5;

    private DistributedLockDao lockDao;
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private AccountDao accountDao;
    private HealthCodeService healthCodeService;
    private EmailVerificationValidator verificationValidator;
    private SignInValidator signInValidator;
    private PasswordResetValidator passwordResetValidator;
    private EmailValidator emailValidator;

    @Autowired
    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    @Autowired
    public void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    @Autowired
    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    @Autowired
    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }
    @Autowired
    public void setEmailVerificationValidator(EmailVerificationValidator validator) {
        this.verificationValidator = validator;
    }
    @Autowired
    public void setSignInValidator(SignInValidator validator) {
        this.signInValidator = validator;
    }
    @Autowired
    public void setPasswordResetValidator(PasswordResetValidator validator) {
        this.passwordResetValidator = validator;
    }
    @Autowired
    public void setEmailValidator(EmailValidator validator) {
        this.emailValidator = validator;
    }

    @Override
    public UserSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return cacheProvider.getUserSession(sessionToken);
    }

    @Override
    public UserSession signIn(Study study, SignIn signIn) throws ConsentRequiredException, EntityNotFoundException {

        checkNotNull(study, "Study cannot be null");
        checkNotNull(signIn, "Sign in cannot be null");
        Validate.entityThrowingException(signInValidator, signIn);

        final String signInLock = study.getIdentifier() + RedisKey.SEPARATOR + signIn.getUsername();
        String lockId = null;
        try {
            lockId = lockDao.acquireLock(SignIn.class, signInLock, LOCK_EXPIRE_IN_SECONDS);
            Account account = accountDao.authenticate(study, signIn);
            UserSession session = getSessionFromAccount(study, account);
            cacheProvider.setUserSession(session);
            if (!session.getUser().doesConsent()) {
                throw new ConsentRequiredException(session);
            }
            return session;
        } finally {
            if (lockId != null) {
                lockDao.releaseLock(SignIn.class, signInLock, lockId);
            }
        }
    }

    @Override
    public void signOut(final UserSession session) {
        if (session != null) {
            cacheProvider.removeSession(session);
        }
    }

    @Override
    public void signUp(Study study, SignUp signUp, boolean isAnonSignUp) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signUp, "Sign up cannot be null");
        
        Validate.entityThrowingException(new SignUpValidator(study.getPasswordPolicy()), signUp);
        
        String lockId = null;
        try {
            lockId = lockDao.acquireLock(SignUp.class, signUp.getEmail(), LOCK_EXPIRE_IN_SECONDS);
            if (consentService.isStudyAtEnrollmentLimit(study)) {
                throw new StudyLimitExceededException(study);
            }
            accountDao.signUp(study, signUp, isAnonSignUp);
        } catch(EntityAlreadyExistsException e) {
            // Suppress this. Otherwise it the response reveals that the email has already been taken, 
            // and you can infer who is in the study from the response. Instead send a reset password 
            // request to the email address in case user has forgotten password and is trying to sign 
            // up again. Non-anonymous sign ups (sign ups done by admins on behalf of users) still get a 404
            if (isAnonSignUp) {
                Email email = new Email(study.getIdentifier(), signUp.getEmail());
                requestResetPassword(study, email);
                logger.info("Sign up attempt for existing email address in study '"+study.getIdentifier()+"'");
            } else {
                throw e;
            }
        } finally {
            lockDao.releaseLock(SignUp.class, signUp.getEmail(), lockId);
        }
    }

    @Override
    public UserSession verifyEmail(Study study, EmailVerification verification) throws ConsentRequiredException {
        checkNotNull(verification, "Verification object cannot be null");

        Validate.entityThrowingException(verificationValidator, verification);
        
        Account account = accountDao.verifyEmail(study, verification);
        UserSession session = getSessionFromAccount(study, account);
        cacheProvider.setUserSession(session);

        if (!session.getUser().doesConsent()) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }
    
    @Override
    public void resendEmailVerification(StudyIdentifier studyIdentifier, Email email) {
        checkNotNull(studyIdentifier, "StudyIdentifier object cannnot be null");
        checkNotNull(email, "Email object cannnot be null");
        
        Validate.entityThrowingException(emailValidator, email);
        try {
            accountDao.resendEmailVerificationToken(studyIdentifier, email);    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            logger.info("Resend email verification for unregistered email in study '"+studyIdentifier.getIdentifier()+"'");
        }
    }

    @Override
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

    @Override
    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        checkNotNull(passwordReset, "Password reset object required");

        Validate.entityThrowingException(passwordResetValidator, passwordReset);
        
        accountDao.resetPassword(passwordReset);
    }

    private UserSession getSessionFromAccount(final Study study, final Account account) {

        final UserSession session = getSession(account);
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment());
        session.setStudyIdentifier(study.getStudyIdentifier());

        final User user = new User(account);
        user.setStudyKey(study.getIdentifier());

        final String healthCode = getHealthCode(study, account);
        user.setHealthCode(healthCode);

        user.setSharingScope(optionsService.getSharingScope(healthCode));
        user.setSignedMostRecentConsent(consentService.hasUserSignedMostRecentConsent(study, user));
        user.setConsent(consentService.hasUserConsentedToResearch(study, user));

        // And now for some exceptions...
        // All administrators and all researchers are assumed to consent when using any API.
        // This is needed so they can sign in without facing a 412 exception.
        if (user.isInRole(ADMIN) || user.isInRole(RESEARCHER) || user.isInRole(DEVELOPER)) {
            user.setConsent(true);
        }
        // And then we set *any* account that has the admin email to be consented as well
        String adminUser = BridgeConfigFactory.getConfig().getProperty("admin.email");
        if (adminUser != null && adminUser.equals(account.getEmail())) {
            user.setConsent(true);
        }

        session.setUser(user);
        return session;
    }

    private UserSession getSession(final Account account) {
        final UserSession session = cacheProvider.getUserSessionByUserId(account.getId());
        if (session != null) {
            return session;
        }
        final UserSession newSession = new UserSession();
        newSession.setSessionToken(BridgeUtils.generateGuid());
        // Internal session token to identify sessions internally (e.g. in metrics)
        newSession.setInternalSessionToken(BridgeUtils.generateGuid());
        return newSession;
    }

    /**
     * Any user who authenticates has a health ID/code generated and assigned. It happens at authentication 
     * because some users are automatically marked as consented, which means we have these users accessing 
     * all the APIs that expect users to have health codes, which unknown consequences if they don't. We 
     * do not have to do it at sign up or when the user actually consents (interestingly enough). 
     * @param study
     * @param account
     * @return
     */
    private String getHealthCode(Study study, Account account) {
        HealthId healthId = healthCodeService.getMapping(account.getHealthId());
        if (healthId == null) {
            healthId = healthCodeService.createMapping(study);
            account.setHealthId(healthId.getId());
            accountDao.updateAccount(study, account);
            logger.debug("Health ID/code pair created for " + account.getId() + " in study " + study.getName());
        }
        return healthId.getCode();
    }
}
