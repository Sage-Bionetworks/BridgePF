package org.sagebionetworks.bridge.services;

import org.sagebionetworks.repo.model.UserProfile;
import org.sagebionetworks.repo.model.auth.Session;

public interface AuthenticationService {
	
	public Session signIn(String username, String password) throws Exception;
	
	public void signOut(String sessionToken) throws Exception;
	
	public void resetPassword(String email) throws Exception;
	
	public UserProfile getUserProfile(String sessionToken) throws Exception;
	
}