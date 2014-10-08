package org.sagebionetworks.bridge.services;

import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;

public class AuthenticationServiceImpl implements AuthenticationService {

    private final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private Client stormpathClient;
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private ConsentService consentService;
    private EmailValidator emailValidator = EmailValidator.getInstance();

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }

    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }

    public void setHealthCodeEncryptor(BridgeEncryptor encryptor) {
        this.healthCodeEncryptor = encryptor;
    }

    public void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Override
    public UserSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return cacheProvider.getUserSession(sessionToken);
    }

    @Override
    public UserSession signIn(Study study, SignIn signIn) throws ConsentRequiredException, EntityNotFoundException,
            BridgeServiceException {

        final long start = System.nanoTime();

        if (signIn == null) {
            throw new EntityNotFoundException(User.class, "SignIn object is required");
        } else if (StringUtils.isBlank(signIn.getUsername())) {
            throw new EntityNotFoundException(User.class, "Username/email must not be null");
        } else if (StringUtils.isBlank(signIn.getPassword())) {
            throw new EntityNotFoundException(User.class, "Password must not be null");
        } else if (study == null) {
            throw new BadRequestException("Study is required");
        }

        AuthenticationRequest<?, ?> request = null;
        UserSession session = null;
        try {
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            logger.info("sign in create app " + (System.nanoTime() - start) );
            request = new UsernamePasswordRequest(signIn.getUsername(), signIn.getPassword());
            Account account = application.authenticateAccount(request).getAccount();
            logger.info("sign in authenticate " + (System.nanoTime() - start));
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
        logger.info("sign in service " + (end - start));

        return session;
    }

    @Override
    public void signOut(String sessionToken) {
        if (sessionToken != null) {
            cacheProvider.remove(sessionToken);
        }
    }

    @Override
    public void signUp(SignUp signUp, Study study) throws BridgeServiceException {
        if (study == null) {
            throw new BadRequestException("Study object is required");
        } else if (StringUtils.isBlank(study.getStormpathDirectoryHref())) {
            throw new BadRequestException("Study's StormPath directory HREF is required");
        } else if (signUp == null) {
            throw new BadRequestException("SignUp object is required");
        } else if (StringUtils.isBlank(signUp.getEmail())) {
            throw new BadRequestException("Email is required");
        } else if (StringUtils.isBlank(signUp.getPassword())) {
            throw new BadRequestException("Password is required");
        } else if (!emailValidator.isValid(signUp.getEmail())) {
            throw new BadRequestException("Email address does not appear to be valid");
        }
        // It's possible to sign up for two different studies. We need to avoid this by checking if the 
        // application as a whole has an email address, before creating that address in a specific 
        // study. If it exists anywhere, it cannot be used again.
        Account account = findExistingEmailAddress(signUp);
        if (account != null) {
            throw new EntityAlreadyExistsException(new User(account));
        };
        
        try {
            Directory directory = stormpathClient.getResource(study.getStormpathDirectoryHref(), Directory.class);

            account = stormpathClient.instantiate(Account.class);
            account.setGivenName("<EMPTY>");
            account.setSurname("<EMPTY>");
            account.setEmail(signUp.getEmail());
            account.setUsername(signUp.getUsername());
            account.setPassword(signUp.getPassword());
            directory.createAccount(account);
        } catch (ResourceException re) {
            throw new BadRequestException(re.getDeveloperMessage());
        }
    }

    @Override
    public UserSession verifyEmail(Study study, EmailVerification verification) throws BridgeServiceException,
            ConsentRequiredException {
        if (verification == null) {
            throw new BadRequestException("Verification object is required");
        } else if (verification.getSptoken() == null) {
            throw new BadRequestException("Email verification token is required");
        }
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
        if (email == null) {
            throw new BadRequestException("Email object is required");
        }
        if (StringUtils.isBlank(email.getEmail())) {
            throw new BadRequestException("Email is required");
        }
        try {
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            application.sendPasswordResetEmail(email.getEmail());
        } catch (ResourceException re) {
            throw new BadRequestException(re.getDeveloperMessage());
        }
    }

    @Override
    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        if (passwordReset == null) {
            throw new BadRequestException("Password reset object is required");
        } else if (StringUtils.isBlank(passwordReset.getSptoken())) {
            throw new BadRequestException("Password reset token is required");
        } else if (StringUtils.isBlank(passwordReset.getPassword())) {
            throw new BadRequestException("Password is required");
        }
        try {
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            Account account = application.verifyPasswordResetToken(passwordReset.getSptoken());
            account.setPassword(passwordReset.getPassword());
            account.save();
        } catch (ResourceException e) {
            throw new BadRequestException(e.getDeveloperMessage());
        }
    }

    // We also want to verify that the username hasn't been taken in another study...
    private Account findExistingEmailAddress(SignUp signUp) {
        Map<String, Object> queryParams = Maps.newHashMap();
        queryParams.put("email", signUp.getEmail());
        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
        AccountList accounts = application.getAccounts(queryParams);
        
        return (accounts.iterator().hasNext()) ? accounts.iterator().next() : null;
    }

    private UserSession createSessionFromAccount(Study study, Account account) {

        final UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment().getEnvName());
        session.setSessionToken(BridgeUtils.generateGuid());
        final User user = new User(account);
        user.setStudyKey(study.getKey());

        final CustomData data = account.getCustomData();
        final String hdcKey = study.getKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        final String encryptedId = (String)data.get(hdcKey);
        if (encryptedId != null) {
            String healthId = healthCodeEncryptor.decrypt(encryptedId);
            String healthCode = healthCodeService.getHealthCode(healthId);
            user.setHealthDataCode(healthCode);
            boolean hasConsented = consentService.hasUserConsentedToResearch(user, study);
            user.setConsent(hasConsented);
        }

        String adminUser = BridgeConfigFactory.getConfig().getProperty("admin.email");
        if (adminUser != null && adminUser.equals(account.getEmail())) {
            user.setConsent(true);
        }
        
        session.setUser(user);
        return session;
    }
}
