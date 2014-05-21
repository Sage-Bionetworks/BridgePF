package org.sagebionetworks.bridge.services;

import models.UserSession;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.repo.model.DomainType;
import org.sagebionetworks.repo.model.UserSessionData;
import org.sagebionetworks.repo.model.auth.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

public class AuthenticationServiceImpl implements AuthenticationService, BeanFactoryAware {
	
	final static Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
	
	private BeanFactory beanFactory;
	
	public void setBeanFactory(BeanFactory factory) {
		this.beanFactory = factory;
	}
	
	private SynapseClient getSynapseClient(String sessionToken) {
		SynapseClient client = beanFactory.getBean("synapseClient", SynapseClient.class);
		client.setSessionToken(sessionToken);
		client.appendUserAgent(BridgeConstants.USER_AGENT);
		return client;
	}
	
	@Override
    public UserSession signIn(String usernameOrEmail, String password) throws ConsentRequiredException,
            BridgeNotFoundException, BridgeServiceException {
	    if (StringUtils.isBlank(usernameOrEmail) || StringUtils.isBlank(password)) {
            throw new BridgeServiceException("Invalid credentials, supply username/email and password",
                    HttpStatus.SC_BAD_REQUEST);
	    }
	    Session session = null;
	    try {
	        session = getSynapseClient(null).login(usernameOrEmail, password);
	    } catch(SynapseNotFoundException e) { // NOTE: Do we need this now?
	        throw new BridgeNotFoundException(e);
	    } catch(Throwable e) {
	        throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
	    }
        if (!session.getAcceptsTermsOfUse()) {
            throw new ConsentRequiredException(session.getSessionToken());
        }
        return getSession(session.getSessionToken());
	}
	
	@Override
	public UserSession getSession(String sessionToken) {
		try {
			UserSessionData data = getSynapseClient(sessionToken).getUserSessionData();
			// Does the user ever *not* have a username?
			String username = data.getProfile().getUserName();
			if (username == null) {
				username = data.getProfile().getEmail();
			}
			UserSession userSession = new UserSession();
			userSession.setSessionToken(data.getSession().getSessionToken());
			userSession.setUsername(username);
			userSession.setAuthenticated(true);
	        userSession.setEnvironment(BridgeConfigFactory.getConfig().getEnvironment().getEnvName());
			return userSession;
		} catch(Throwable throwable) {
			return new UserSession();
		}
	}

	@Override
	public void signOut(String sessionToken) {
		// Synapse requires a session token, but it's not an error if it's missing
		// (e.g. user has deleted the cookie).
		try {
			getSynapseClient(sessionToken).logout();	
		} catch(Throwable e) {
			logger.warn(e.getMessage(), e);
		}
	}

	@Override
	public void requestResetPassword(String email) throws BridgeServiceException {
	    if (StringUtils.isBlank(email)) {
	        throw new BridgeServiceException("Email is required", HttpStatus.SC_BAD_REQUEST);
	    }
	    try {
	        getSynapseClient(null).sendPasswordResetEmail(email);    
	    } catch(Exception e) {
	        throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
	    }
	}
	
	@Override
	public void resetPassword(String sessionToken, String password) throws BridgeServiceException {
	    try {
	        getSynapseClient(null).changePassword(sessionToken, password);    
	    } catch(Exception e) {
	        throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
	    }
	}
	
	@Override
	public void consentToResearch(String sessionToken) throws BridgeServiceException {
        try {
            getSynapseClient(sessionToken).signTermsOfUse(sessionToken, DomainType.BRIDGE, true);
        } catch(Exception e) {
            throw new BridgeServiceException(e, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
	}

}
