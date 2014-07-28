package org.sagebionetworks.bridge.services;

import java.util.UUID;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.UserSessionInfo;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import play.mvc.Http;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;

public class AuthenticationServiceImpl implements AuthenticationService {

    private Client stormpathClient;
    private CacheProvider cache;
    private BridgeConfig config;
    private BridgeEncryptor healthCodeEncryptor;
    private HealthCodeService healthCodeService;
    private EmailValidator emailValidator = EmailValidator.getInstance();
    private UserProfileService userProfileService;
    private SendMailService sendMailService;

    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    public void setCacheProvider(CacheProvider cache) {
        this.cache = cache;
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

    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    
    public void setSendMailService(SendMailService sendMailService) {
        this.sendMailService = sendMailService;
    }

    @Override
    public UserSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return cache.getUserSession(sessionToken);
    }

    @Override
    public UserSession signIn(Study study, SignIn signIn) throws ConsentRequiredException, BridgeNotFoundException,
            BridgeServiceException {

        if (signIn == null) {
            throw new BridgeServiceException("SignIn object is required", Http.Status.NOT_FOUND);
        } else if (StringUtils.isBlank(signIn.getUsername())) {
            throw new BridgeServiceException("Username/email must not be null", Http.Status.NOT_FOUND);
        } else if (StringUtils.isBlank(signIn.getPassword())) {
            throw new BridgeServiceException("Password must not be null", Http.Status.NOT_FOUND);
        } else if (study == null) {
            throw new BridgeServiceException("Study is required", HttpStatus.SC_BAD_REQUEST);
        }

        AuthenticationRequest<?, ?> request = null;
        UserSession session = null;
        try {
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            request = new UsernamePasswordRequest(signIn.getUsername(), signIn.getPassword());
            Account account = application.authenticateAccount(request).getAccount();

            session = createSessionFromAccount(study, account);
            cache.setUserSession(session.getSessionToken(), session);

            if (!session.doesConsent()) {
                throw new ConsentRequiredException(new UserSessionInfo(session));
            }

        } catch (ResourceException re) {
            throw new BridgeNotFoundException(re.getDeveloperMessage());
        } finally {
            if (request != null) {
                request.clear();
            }
        }
        return session;
    }

    @Override
    public void signOut(String sessionToken) {
        if (sessionToken != null) {
            cache.remove(sessionToken);
        }
    }

    @Override
    public void signUp(SignUp signUp, Study study) throws BridgeServiceException {
        if (study == null) {
            throw new BridgeServiceException("Study object is required", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(study.getStormpathDirectoryHref())) {
            throw new BridgeServiceException("Study's StormPath directory HREF is required", HttpStatus.SC_BAD_REQUEST);
        } else if (signUp == null) {
            throw new BridgeServiceException("SignUp object is required", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(signUp.getEmail())) {
            throw new BridgeServiceException("Email is required", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(signUp.getPassword())) {
            throw new BridgeServiceException("Password is required", HttpStatus.SC_BAD_REQUEST);
        } else if (!emailValidator.isValid(signUp.getEmail())) {
            throw new BridgeServiceException("Email address does not appear to be valid", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            Directory directory = stormpathClient.getResource(study.getStormpathDirectoryHref(), Directory.class);
            
            Account account = stormpathClient.instantiate(Account.class);
            account.setGivenName("<EMPTY>");
            account.setSurname("<EMPTY>");
            account.setEmail(signUp.getEmail());
            account.setUsername(signUp.getUsername());
            account.setPassword(signUp.getPassword());
            directory.createAccount(account);
        } catch (ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public UserSession verifyEmail(Study study, EmailVerification verification) throws BridgeServiceException,
            ConsentRequiredException {
        if (verification == null) {
            throw new BridgeServiceException("Verification object is required", HttpStatus.SC_BAD_REQUEST);
        } else if (verification.getSptoken() == null) {
            throw new BridgeServiceException("Email verification token is required", HttpStatus.SC_BAD_REQUEST);
        }
        UserSession session = null;
        try {
            Account account = stormpathClient.getCurrentTenant().verifyAccountEmail(verification.getSptoken());

            session = createSessionFromAccount(study, account);
            cache.setUserSession(session.getSessionToken(), session);
            if (!session.doesConsent()) {
                throw new ConsentRequiredException(new UserSessionInfo(session));
            }
            return session;
        } catch (ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public void requestResetPassword(Email email) throws BridgeServiceException {
        if (email == null) {
            throw new BridgeServiceException("Email object is required", HttpStatus.SC_BAD_REQUEST);
        }
        if (StringUtils.isBlank(email.getEmail())) {
            throw new BridgeServiceException("Email is required", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            application.sendPasswordResetEmail(email.getEmail());
        } catch (ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        if (passwordReset == null) {
            throw new BridgeServiceException("Password reset object is required", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(passwordReset.getSptoken())) {
            throw new BridgeServiceException("Password reset token is required", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(passwordReset.getPassword())) {
            throw new BridgeServiceException("Password is required", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            Account account = application.verifyPasswordResetToken(passwordReset.getSptoken());
            account.setPassword(passwordReset.getPassword());
            account.save();
        } catch (ResourceException e) {
            throw new BridgeServiceException(e.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public void consentToResearch(String sessionToken, ResearchConsent consent, Study study) throws BridgeServiceException {
        try {
            UserSession session = cache.getUserSession(sessionToken);
            if (session == null) {
                throw new BridgeServiceException("No session", 500);
            }
            Account account = stormpathClient.getResource(session.getUser().getStormpathHref(), Account.class);
            String key = session.getStudyKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
            CustomData data = account.getCustomData();
            data.put(key, "true");

            key = session.getStudyKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;

            HealthId healthId = healthCodeService.create();
            String encryptedId = healthCodeEncryptor.encrypt(healthId.getId());

            data.put(key, encryptedId);
            data.put(BridgeConstants.CUSTOM_DATA_VERSION, 1);
            data.save();
            
            // send consent agreement
            sendMailService.sendConsentAgreement(session.getUser().getEmail(), consent, study);

            session.setHealthDataCode(healthId.getCode());
            session.setConsent(true);
            cache.setUserSession(sessionToken, session);
        } catch (Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private UserSession createSessionFromAccount(Study study, Account account) {

        UserSession session;
        session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment().getEnvName());
        session.setSessionToken(UUID.randomUUID().toString());
        session.setStudyKey(study.getKey());
        session.setUser(userProfileService.createUserFromAccount(account));

        CustomData data = account.getCustomData();
        String consentKey = study.getKey()+BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
        session.setConsent( "true".equals(data.get(consentKey)) );
        
        // New users will not yet have consented and generated a health ID, so skip this if it doesn't exist.
        if (session.isConsent()) {
            final String hdcKey = study.getKey()+BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            final String encryptedId = (String)data.get(hdcKey);
            String healthId = healthCodeEncryptor.decrypt(encryptedId);
            String healthCode = healthCodeService.getHealthCode(healthId);
            session.setHealthDataCode(healthCode);
        }
        return session;
    }
}
