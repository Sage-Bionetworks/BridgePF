package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Validator;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.resource.ResourceException;

public class AuthenticationServiceImpl implements AuthenticationService {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private Client stormpathClient;
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private AccountEncryptionService accountEncryptionService;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private Validator signInValidator;
    private Validator signUpValidator;
    private Validator passwordResetValidator;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }

    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }

    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    
    public void setSignInValidator(Validator validator) {
        this.signInValidator = validator;
    }
    
    public void setSignUpValidator(Validator validator) {
        this.signUpValidator = validator;
    }
    
    public void setPasswordResetValidator(Validator validator) {
        this.passwordResetValidator = validator;
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
        
        final long start = System.nanoTime();
        AuthenticationRequest<?, ?> request = null;
        UserSession session = null;
        try {
            Application application = StormpathFactory.getStormpathApplication(stormpathClient);
            logger.debug("sign in create app " + (System.nanoTime() - start) );
            request = new UsernamePasswordRequest(signIn.getUsername(), signIn.getPassword());
            Account account = application.authenticateAccount(request).getAccount();
            logger.debug("sign in authenticate " + (System.nanoTime() - start));
            session = createSessionFromAccount(study, account);
            cacheProvider.setUserSession(session.getSessionToken(), session);
            
            if (!session.getUser().doesConsent()) {
                throw new ConsentRequiredException(session);
            }

        } catch (ResourceException re) {
            throw new EntityNotFoundException(User.class, re.getDeveloperMessage());
        } finally {
            if (request != null) {
                request.clear();
            }
        }
        final long end = System.nanoTime();
        logger.debug("sign in service " + (end - start));

        return session;
    }

    @Override
    public void signOut(String sessionToken) {
        if (sessionToken != null) {
            cacheProvider.remove(sessionToken);
        }
    }

    @Override
    public void signUp(SignUp signUp, Study study, boolean sendEmail) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signUp, "Sign up cannot be null");
        
        Validate.entityThrowingException(signUpValidator, signUp);
        
        try {
            Directory directory = stormpathClient.getResource(study.getStormpathHref(), Directory.class);
            // Create Stormpath account
            Account account = stormpathClient.instantiate(Account.class);
            account.setGivenName("<EMPTY>");
            account.setSurname("<EMPTY>");
            account.setEmail(signUp.getEmail());
            account.setUsername(signUp.getUsername());
            account.setPassword(signUp.getPassword());
            directory.createAccount(account, sendEmail);
            
            addAccountToGroups(directory, account, signUp.getRoles());
            
            // Assign a health code
            accountEncryptionService.createAndSaveHealthCode(study, account);

        } catch (ResourceException re) {
            throw new BadRequestException(re.getDeveloperMessage());
        }
    }

    @Override
    public UserSession verifyEmail(Study study, EmailVerification verification) throws ConsentRequiredException {
        checkNotNull(verification, "Verification object cannot be null");
        checkNotNull(verification.getSptoken(), "Email verification token is required");
        
        UserSession session = null;
        try {
            Account account = stormpathClient.getCurrentTenant().verifyAccountEmail(verification.getSptoken());
            
            session = createSessionFromAccount(study, account);
            cacheProvider.setUserSession(session.getSessionToken(), session);
            
            if (!session.getUser().doesConsent()) {
                throw new ConsentRequiredException(session);
            }
            return session;
        } catch (ResourceException re) {
            throw new BadRequestException(re.getDeveloperMessage());
        }
    }

    @Override
    public void requestResetPassword(Email email) throws BridgeServiceException {
        checkNotNull(email, "Email object cannot cannot be null");
        checkArgument(StringUtils.isNotBlank(email.getEmail()), "Email is required");
        
        try {
            Application application = StormpathFactory.getStormpathApplication(stormpathClient);
            application.sendPasswordResetEmail(email.getEmail());
        } catch (ResourceException re) {
            throw new BadRequestException(re.getDeveloperMessage());
        }
    }

    @Override
    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        checkNotNull(passwordReset, "Password reset object required");
        
        Validate.entityThrowingException(passwordResetValidator, passwordReset);
        try {
            Application application = StormpathFactory.getStormpathApplication(stormpathClient);
            Account account = application.verifyPasswordResetToken(passwordReset.getSptoken());
            account.setPassword(passwordReset.getPassword());
            account.save();
        } catch (ResourceException e) {
            throw new BadRequestException(e.getDeveloperMessage());
        }
    }

    @Override
    public User getUser(Study study, String email) {
        Application app = StormpathFactory.getStormpathApplication(stormpathClient);
        Map<String, Object> queryParams = new HashMap<String, Object>();
        queryParams.put("email", email);
        AccountList accounts = app.getAccounts(queryParams);

        if (accounts.iterator().hasNext()) {
            Account account = accounts.iterator().next();
            return createSessionFromAccount(study, account).getUser();
        }
        return null;
    }

    @Override
    public UserSession createSessionFromAccount(Study study, Account account) {

        final UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment().name().toLowerCase());
        session.setSessionToken(BridgeUtils.generateGuid());

        final User user = new User(account);
        user.setStudyKey(study.getIdentifier());

        final String healthCode = getHealthCode(study, account);
        user.setHealthCode(healthCode);

        user.setConsent(consentService.hasUserConsentedToResearch(user, study));
        user.setDataSharing(optionsService.getBooleanOption(healthCode, Option.DATA_SHARING));

        // And now for some exceptions...

        // All administrators and all researchers are assumed to consent when using any API.
        // This is needed so they can sign in without facing a 412 exception.
        if (user.isInRole(BridgeConstants.ADMIN_GROUP) || user.isInRole(study.getResearcherRole())) {
            user.setConsent(true);
        }
        // And then we set *anyone* configured as an admin to have signed the consent as well
        String adminUser = BridgeConfigFactory.getConfig().getProperty("admin.email");
        if (adminUser != null && adminUser.equals(account.getEmail())) {
            user.setConsent(true);
        }

        session.setUser(user);
        return session;
    }

    private void addAccountToGroups(Directory directory, Account account, List<String> roles) {
        if (roles != null) {
            GroupList groups = directory.getGroups();
            for (Group group : groups) {
                if (roles.contains(group.getName())) {
                    account.addGroup(group);
                }
            }
        }
    }

    private String getHealthCode(Study study, Account account) {
        HealthId healthId = accountEncryptionService.getHealthCode(study, account);
        if (healthId == null) {
            healthId = accountEncryptionService.createAndSaveHealthCode(study, account);
        }
        String healthCode = healthId.getCode();
        if (healthCode == null) {
            // TODO: Temporary patch for inconsistent data likely caused by
            // an obsolete bug in StormPathUserAdminService. Remove me after all the repositories
            // have been synced and run all the tests.
            healthId = accountEncryptionService.createAndSaveHealthCode(study, account);
            logger.error("Health code re-created for account " + account.getEmail() + " in study " + study.getName());
            healthCode = healthId.getCode();
        }
        checkNotNull(healthCode);
        return healthCode;
    }
}
