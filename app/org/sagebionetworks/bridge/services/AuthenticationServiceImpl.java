package org.sagebionetworks.bridge.services;

import models.UserSession;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.client.SynapseClient;
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
	public UserSession signIn(String usernameOrEmail, String password) throws Exception {
		Session session = getSynapseClient(null).login(usernameOrEmail, password);
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
			logger.error(e.getMessage(), e);
		}
	}

	@Override
	public void requestResetPassword(String email) throws Exception {
		getSynapseClient(null).sendPasswordResetEmail(email);
	}
	
	@Override
	public void resetPassword(String sessionToken, String password) throws Exception {
		getSynapseClient(null).changePassword(sessionToken, password);
	}
	
	@Override
	public void consentToResearch(String sessionToken) throws Exception {
		getSynapseClient(sessionToken).signTermsOfUse(sessionToken, DomainType.BRIDGE, true);
	}

}
