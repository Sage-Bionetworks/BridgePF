package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;

import models.UserSession;

public interface AuthenticationService {
	
    public UserSession signIn(String usernameOrEmail, String password) throws ConsentRequiredException,
            BridgeNotFoundException, BridgeServiceException;
	
	public UserSession getSession(String sessionToken);
	
	public void signOut(String sessionToken);
	
	public void requestResetPassword(String email) throws BridgeServiceException;
	
	public void resetPassword(String sessionToken, String password) throws BridgeServiceException;
	
	public void consentToResearch(String sessionToken) throws BridgeServiceException;
	
}