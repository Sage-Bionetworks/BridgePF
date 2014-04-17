package org.sagebionetworks.bridge.services;

import models.UserSession;

public interface AuthenticationService {
	
	public UserSession signIn(String usernameOrEmail, String password) throws Exception;
	
	public UserSession getSession(String sessionToken);
	
	public void signOut(String sessionToken) throws Exception;
	
	public void requestResetPassword(String email) throws Exception;
	
	public void resetPassword(String sessionToken, String password) throws Exception;
	
	public void consentToResearch(String sessionToken) throws Exception;
	
}