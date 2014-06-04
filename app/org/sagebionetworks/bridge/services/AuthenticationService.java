package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface AuthenticationService {
	
    public UserSession signIn(Study study, String usernameOrEmail, String password) throws ConsentRequiredException,
            BridgeNotFoundException, BridgeServiceException;
	
	public UserSession getSession(String sessionToken);
	
	public void signOut(String sessionToken);
	
	public void requestResetPassword(String email) throws BridgeServiceException;
	
	public void resetPassword(String password, String passwordResetToken) throws BridgeServiceException;
	
	public void consentToResearch(String sessionToken) throws BridgeServiceException;
	
}