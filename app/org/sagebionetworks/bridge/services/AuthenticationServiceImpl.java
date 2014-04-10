package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;

import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.repo.model.TermsOfUseException;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.UserSessionData;
import org.sagebionetworks.repo.model.auth.Session;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

public class AuthenticationServiceImpl implements AuthenticationService, BeanFactoryAware {
	
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
	public String signIn(String username, String password) throws Exception {
		Session session = getSynapseClient(null).login(username, password);
		if (!session.getAcceptsTermsOfUse()) {
			throw new TermsOfUseException();
		}
		return session.getSessionToken();
	}

	@Override
	public void signOut(String sessionToken) throws Exception {
		getSynapseClient(sessionToken).logout();
	}

	@Override
	public void resetPassword(String email) throws Exception {
		getSynapseClient(null).sendPasswordResetEmail(email);
	}

	@Override
	public UserProfile getUserProfile(String sessionToken) throws Exception {
		UserSessionData data = getSynapseClient(sessionToken).getUserSessionData();
		return data.getProfile();
	}

}
