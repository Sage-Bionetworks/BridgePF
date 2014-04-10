package org.sagebionetworks.bridge.services;

import org.sagebionetworks.repo.model.UserProfile;

public interface AuthenticationService {
	
	public String signIn(String username, String password) throws Exception;
	
	public void signOut(String sessionToken) throws Exception;
	
	public void resetPassword(String email) throws Exception;
	
	public UserProfile getUserProfile(String sessionToken) throws Exception;
	
}