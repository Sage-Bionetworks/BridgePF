package org.sagebionetworks.bridge.services;

import java.util.UUID;

import org.apache.commons.httpclient.HttpStatus;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.EncryptorUtil;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.AccountStore;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;

public class AuthenticationServiceImpl implements AuthenticationService {
	
	final static Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
	
	private Client stormpathClient;
	private CacheProvider cache;
	private BridgeConfig config;
	private EmailValidator emailValidator = EmailValidator.getInstance();
	
    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }
	
    public void setCacheProvider(CacheProvider cache) {
        this.cache = cache;
    }
    
    public void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    
	@Override
    public UserSession signIn(Study study, String usernameOrEmail, String password) throws ConsentRequiredException,
            BridgeNotFoundException, BridgeServiceException {
	    if (StringUtils.isBlank(usernameOrEmail) || StringUtils.isBlank(password)) {
            throw new BridgeServiceException("Invalid credentials, supply username/email and password",
                    HttpStatus.SC_BAD_REQUEST);
	    }
	    if (study == null) {
            throw new BridgeServiceException("Study is required", HttpStatus.SC_BAD_REQUEST);
	    }
	    AuthenticationRequest<?, ?> request = null;
	    UserSession session = null;
	    try {
	        
	        Application application = StormpathFactory.createStormpathApplication(stormpathClient); 
	        request = new UsernamePasswordRequest(usernameOrEmail, password);
	        Account account = application.authenticateAccount(request).getAccount();
	        
	        session = createSessionFromAccount(study, account);
	        cache.set(session.getSessionToken(), session);

	        if (!session.doesConsent()) {
	            throw new ConsentRequiredException(session.getSessionToken());
	        }
	        
	    } catch (ResourceException re) {
	        throw new BridgeNotFoundException(re.getDeveloperMessage());
	    } finally {
	        request.clear();
	    }
	    return session;
	}

	@Override
	public UserSession getSession(String sessionToken) throws BridgeServiceException {
        if (sessionToken == null) {
            return new UserSession(); // why do we do this?
        }
	    UserSession session = (UserSession)cache.get(sessionToken);
	    if (session == null || !session.doesConsent()) {
	        return new UserSession();
	    }/* else if (!session.doesConsent()) {
            throw new ConsentRequiredException(session.getSessionToken());
	    }*/
		return session;
	}

	@Override
	public void signOut(String sessionToken) {
	    if (sessionToken != null) {
	        cache.remove(sessionToken);    
	    }
	}

    @Override
    public void signUp(String username, String email, String password) throws BridgeServiceException {
        if (StringUtils.isBlank(email)) {
            throw new BridgeServiceException("Email is required", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(password)) {
            throw new BridgeServiceException("Password is required", HttpStatus.SC_BAD_REQUEST);
        } else if (!emailValidator.isValid(email)) {
            throw new BridgeServiceException("Email address does not appear to be valid", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            // Is it necessary to do two requests like this? Is it two requests?
            Application application = StormpathFactory.createStormpathApplication(stormpathClient);
            AccountStore store = application.getDefaultAccountStore();
            Directory directory = stormpathClient.getResource(store.getHref(), Directory.class);
            
            Account account = stormpathClient.instantiate(Account.class);
            account.setGivenName("<EMPTY>");
            account.setSurname("<EMPTY>");
            account.setEmail(email);
            account.setUsername(username);
            account.setPassword(password);
            directory.createAccount(account);
        } catch(ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }
    
    @Override
    public void verifyEmail(String verificationToken) throws BridgeServiceException {
        if (verificationToken == null) {
            throw new BridgeServiceException("Email verification token is required", HttpStatus.SC_BAD_REQUEST);
        }
        try {
            stormpathClient.getCurrentTenant().verifyAccountEmail(verificationToken);    
        } catch(ResourceException re) {
            throw new BridgeServiceException(re.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

	@Override
	public void requestResetPassword(String email) throws BridgeServiceException {
	    if (StringUtils.isBlank(email)) {
	        throw new BridgeServiceException("Email is required", HttpStatus.SC_BAD_REQUEST);
	    }
	    try {
	        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
	        application.sendPasswordResetEmail(email);
	    } catch(ResourceException re) {
	        throw new BridgeServiceException(re.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
	    }
	}
	
	@Override
	public void resetPassword(String password, String passwordResetToken) throws BridgeServiceException {
	    if (StringUtils.isBlank(passwordResetToken)) {
	        throw new BridgeServiceException("Password reset token is required", HttpStatus.SC_BAD_REQUEST);
	    }
	    if (StringUtils.isBlank(password)) {
	        throw new BridgeServiceException("Password is required", HttpStatus.SC_BAD_REQUEST);
	    }
	    try {
	        Application application = StormpathFactory.createStormpathApplication(stormpathClient);
	        Account account = application.verifyPasswordResetToken(passwordResetToken);
	        account.setPassword(password);
	        account.save();
	    } catch(ResourceException e) {
	        throw new BridgeServiceException(e.getDeveloperMessage(), HttpStatus.SC_BAD_REQUEST);
	    }
	}
	
	@Override
	public void consentToResearch(String sessionToken) throws BridgeServiceException {
        try {
            UserSession session = (UserSession)cache.get(sessionToken);
            if (session == null) {
                throw new BridgeServiceException("No session", 500);
            }
            Account account = stormpathClient.getResource(session.getStormpathHref(), Account.class);
            String key = session.getStudyKey() + BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
            CustomData data = account.getCustomData();
            data.put(key, "true");
            
            key = session.getStudyKey() + BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
            
            String healthDataCode = UUID.randomUUID().toString();
            PBEStringEncryptor encryptor = EncryptorUtil.getEncryptor(config.getHealthCodePassword(), config.getHealthCodeSalt());
            healthDataCode = encryptor.encrypt(healthDataCode);
            
            data.put(key, healthDataCode);
            data.save();

            session.setHealthDataCode(healthDataCode);
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
	}
    
    private UserSession createSessionFromAccount(Study study, Account account) {
        UserSession session;
        session = new UserSession();
        session.setAuthenticated(true);
        session.setStormpathHref(account.getHref());
        session.setEnvironment(config.getEnvironment().getEnvName());
        session.setSessionToken(UUID.randomUUID().toString());
        session.setStudyKey(study.getKey());
        session.setUsername(account.getUsername());
        
        CustomData data = account.getCustomData();
        String consentKey = study.getKey()+BridgeConstants.CUSTOM_DATA_CONSENT_SUFFIX;
        String hdcKey = study.getKey()+BridgeConstants.CUSTOM_DATA_HEALTH_CODE_SUFFIX;
        session.setConsent( "true".equals(data.get(consentKey)) );
        session.setHealthDataCode( (String)data.get(hdcKey) );
        
        return session;
    }
}
