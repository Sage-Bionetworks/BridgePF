package org.sagebionetworks.bridge.stubs;

import java.lang.reflect.Method;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.transform.impl.UndeclaredThrowableStrategy;

import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseClientException;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.repo.model.DomainType;
import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.UserSessionData;
import org.sagebionetworks.repo.model.auth.NewIntegrationTestUser;
import org.sagebionetworks.repo.model.auth.Session;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class StubSynapseClient implements SynapseClient, SynapseAdminClient {
	
	UserSessionData currentUserData;
	Set<String> agreedTOUs = Sets.newHashSet();
	Map<String,UserSessionData> usersById = Maps.newHashMap();
    Map<String,String> usersByChangedPasswords = new HashMap<String,String>();
	
	Map<String,String> emailByUserId = Maps.newHashMap();

	public StubSynapseClient() {
	}

	private static StubSynapseClient singleStub = null;

	public static StubSynapseClient createInstance() {
		if (singleStub == null) {
			// Configure CGLIB Enhancer...
			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(StubSynapseClient.class);
			enhancer.setStrategy(new UndeclaredThrowableStrategy(UndeclaredThrowableException.class));
			enhancer.setInterfaces(new Class[] { SynapseClient.class, SynapseAdminClient.class });
			enhancer.setInterceptDuringConstruction(false);
			enhancer.setCallback(new MethodInterceptor() {
				@Override
				public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
					return proxy.invokeSuper(obj, args);
				}
			});

			// Generate the proxy class and create a proxy instance.
			Object proxy = enhancer.create();
			singleStub = (StubSynapseClient)proxy;
		}
		return singleStub;
	}

	public void setStubUsers(List<Map<String,String>> stubUsers) throws Exception {
	    int id = 1;
		for (Map<String,String> entry : stubUsers) {
		    String idString = Long.toString(id++);
			NewIntegrationTestUser newUser = new NewIntegrationTestUser();
			newUser.setUsername(entry.get("username"));
			newUser.setEmail(entry.get("email"));
			newUser.setPassword("password");
			createStubUser(newUser, idString);
			if ("true".equals(entry.get("tou"))) {
				agreedTOUs.add(idString);
			}
			if ("true".equals(entry.get("admin"))) {
				// nothing for now.
			}
		}
	}

    private void createStubUser(NewIntegrationTestUser user, String idString) throws SynapseException, JSONObjectAdapterException {
        UserProfile profile = new UserProfile();
        profile.setUserName(user.getUsername());
        profile.setOwnerId(idString);
        Session session = new Session();
        session.setSessionToken(idString);
        UserSessionData data = new UserSessionData();
        data.setIsSSO(false);
        data.setProfile(profile);
        data.setSession(session);
        usersById.put(user.getUsername(), data);
        usersById.put(idString, data);
        emailByUserId.put(idString, user.getEmail());
    }
    
	@Override
	public void appendUserAgent(String toAppend) {
	}

	@Override
	public void setSessionToken(String sessionToken) {
	    for (UserSessionData data : usersById.values()) {
	        if (data.getSession().getSessionToken().equals(sessionToken)) {
	            currentUserData = data;
	        }
	    }
	}

	@Override
	public String getCurrentSessionToken() {
	    return currentUserData.getSession().getSessionToken();
	}
	
	@Override
	public Session login(String userName, String password) throws SynapseException {
		currentUserData = null;
		
		UserSessionData data = usersById.get(userName);
		
		String changedPassword = usersByChangedPasswords.get(userName);
		if (changedPassword == null) {
		    changedPassword = "password"; // the defaults
		}
		if (data == null || !changedPassword.equals(password)) {
			throw new SynapseNotFoundException();
		}
		currentUserData = data;
		Session session = data.getSession();
		session.setAcceptsTermsOfUse(true);
		if (!agreedTOUs.contains(data.getProfile().getOwnerId())) {
			session.setAcceptsTermsOfUse(false);
			currentUserData = null;
		}
		//data.setSession(session);
		return session;
	}

	@Override
	public void logout() throws SynapseException {
		currentUserData = null;
	}

	@Override
	public UserSessionData getUserSessionData() throws SynapseException {
		if (currentUserData == null) {
			throw new SynapseClientException();
		}
		return currentUserData;
	}
	
	@Override
	public String getUserName() {
		if (currentUserData != null && currentUserData.getProfile() != null) {
			return currentUserData.getProfile().getUserName();
		}
		return null;
	}

	@Override
	public void changePassword(String sessionToken, String newPassword) throws SynapseException {
	    if (sessionToken != "asdf") {
	        throw new SynapseClientException("Invalid session token ("+sessionToken+")");
	    }
	    usersByChangedPasswords.put(currentUserData.getProfile().getUserName(), newPassword);
	}

	@Override
	public void signTermsOfUse(String sessionToken, DomainType domain, boolean acceptTerms) throws SynapseException {
		if (domain != DomainType.BRIDGE) {
			throw new IllegalArgumentException("Don't call this method with any other domain than Bridge");
		}
		if (acceptTerms) {
			// session tokens are set to the user's ID, so this will work.
			agreedTOUs.add(usersById.get(sessionToken).getProfile().getOwnerId());
		}		
	}

	@Override
	public Session passThroughOpenIDParameters(String queryString, Boolean createUserIfNecessary)
			throws SynapseException {
		throw new IllegalArgumentException("Don't use this API method in Bridge");
	}

	@Override
	public Session passThroughOpenIDParameters(String queryString, Boolean createUserIfNecessary,
			DomainType domainType) throws SynapseException {
		// We'd like to test three scenarios here:
		// 1. Brand new user, needs to sign TOU
		// 2. Returning user who hasn't signed TOU?
		// 3. Returning user who should just be logged in to default start page
		currentUserData = usersById.values().iterator().next();
		Session session = new Session();
		session.setSessionToken(currentUserData.getProfile().getOwnerId());
		return session;		
	}
	
	@Override
	public void sendPasswordResetEmail(String email) throws SynapseException {
		// noop
	}

	@Override
	public void setAuthEndpoint(String authEndpoint) {
		// noop
	}
}
