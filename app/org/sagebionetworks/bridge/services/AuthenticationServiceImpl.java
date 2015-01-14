package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.httpclient.auth.AuthScope.ANY;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.StudyLimitExceededException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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
import org.sagebionetworks.bridge.validators.SignUpValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
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

    private DistributedLockDao lockDao;
    private Client stormpathClient;
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private AccountEncryptionService accountEncryptionService;
    private ConsentService consentService;
    private ParticipantOptionsService optionsService;
    private Validator signInValidator;
    private Validator passwordResetValidator;

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

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
            session = getSessionFromAccount(study, account);
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
            cacheProvider.removeSession(sessionToken);
        }
    }

    @Override
    public void signUp(SignUp signUp, Study study, boolean sendEmail) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signUp, "Sign up cannot be null");
        checkNotNull(signUp.getEmail(), "Sign up email cannot be null");

        String lockId = null;
        try {
            lockId = lockDao.acquireLock(SignUp.class, signUp.getEmail());

            SignUpValidator validator = new SignUpValidator(this);
            Validate.entityThrowingException(validator, signUp);

            if (consentService.isStudyAtEnrollmentLimit(study)) {
                throw new StudyLimitExceededException(study);
            }

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
        } finally {
            lockDao.releaseLock(SignUp.class, signUp.getEmail(), lockId);
        }
    }

    @Override
    public UserSession verifyEmail(Study study, EmailVerification verification) throws ConsentRequiredException {
        checkNotNull(verification, "Verification object cannot be null");
        checkNotNull(verification.getSptoken(), "Email verification token is required");

        UserSession session = null;
        try {
            Account account = stormpathClient.getCurrentTenant().verifyAccountEmail(verification.getSptoken());

            session = getSessionFromAccount(study, account);
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
    public void resendEmailVerification(Study study, Email email) {
        checkNotNull(email, "Email object cannnot be null");
        checkNotNull(email.getEmail(), "Email is required");
        
        // This is painful, it's not in the Java SDK. I hope we can come back to this when it's in their SDK
        // and move it over.
        SimpleHttpConnectionManager manager = new SimpleHttpConnectionManager();
        int status = 202; // The Stormpath resend method returns 202 "Accepted" when successful
        byte[] responseBody = new byte[0];
        try {
            BridgeConfig config = BridgeConfigFactory.getConfig();
            
            String bodyJson = "{\"login\":\""+email.getEmail()+"\"}";
            String applicationId = StormpathFactory.getApplicationId();
            
            HttpClient client = new HttpClient(manager);
            
            PostMethod post = new PostMethod("https://api.stormpath.com/v1/applications/"+applicationId+"/verificationEmails");
            post.setRequestHeader("Accept", "application/json");
            post.setRequestHeader("Content-Type", "application/json");
            post.setRequestEntity(new StringRequestEntity(bodyJson, "application/json", "UTF-8"));

            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
                    config.getStormpathId().trim(), config.getStormpathSecret().trim());
            
            client.getState().setCredentials(ANY, creds);
            client.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, Lists.newArrayList(AuthPolicy.DIGEST));
            client.getParams().setAuthenticationPreemptive(true);
            
            status = client.executeMethod(post);
            responseBody = post.getResponseBody();

        } catch(Throwable throwable) {
            throw new BridgeServiceException(throwable);
        } finally {
            manager.shutdown();
        }
        // If it *wasn't* a 202, then there should be a JSON message included with the response...
        if (status != 202) {
            // One common response, that the email no longer exists, we have mapped to a 404, so do that 
            // here as well. Otherwise we treat it on the API side as a 500 error, a server problem.
            try {
                JsonNode node = BridgeObjectMapper.get().readTree(responseBody);
                String message = node.get("message").asText();
                if (message.contains("does not match a known resource")) {
                    status = 404;
                } else {
                    status = 500;
                }
                throw new BridgeServiceException(message, status);
            } catch(IOException e) {
                throw new BridgeServiceException(e);
            }
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
        Account account = getAccount(email);
        if (account != null) {
            return getSessionFromAccount(study, account).getUser();
        }
        return null;
    }

    @Override
    public Account getAccount(String email) {
        Application app = StormpathFactory.getStormpathApplication(stormpathClient);
        AccountList accounts = app.getAccounts(Accounts.where(Accounts.email().eqIgnoreCase(email)));
        if (accounts.iterator().hasNext()) {
            return accounts.iterator().next();
        }
        return null;
    }

    @Override
    public UserSession getSessionFromAccount(Study study, Account account) {
        final UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment().name().toLowerCase());
        session.setSessionToken(BridgeUtils.generateGuid());

        final User user = new User(account);
        user.setStudyKey(study.getIdentifier());

        final String healthCode = getHealthCode(study, account);
        user.setHealthCode(healthCode);

        user.setSignedMostRecentConsent(consentService.hasUserSignedMostRecentConsent(user, study));
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

    private void addAccountToGroups(Directory directory, Account account, Set<String> roles) {
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
            healthId = accountEncryptionService.createAndSaveHealthCode(study, account);
            logger.error("Health code re-created for account " + account.getEmail() + " in study " + study.getName());
            healthCode = healthId.getCode();
        }
        checkNotNull(healthCode);
        return healthCode;
    }
}
