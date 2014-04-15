package org.sagebionetworks.bridge.services;

import models.UserSession;

public interface AuthenticationService {
	
	public UserSession signIn(String usernameOrEmail, String password) throws Exception;
	
	public UserSession getSession(String sessionToken);
	
	public void signOut(String sessionToken) throws Exception;
	
	public void resetPassword(String email) throws Exception;
	
}