package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface UserAdminService {

    /**
     * Create a user, sign that user is, and optionally consent that user to
     * research. The method is idempotent: no error occurs if the user exists,
     * or is already signed in, or has already consented.
     * 
     * @param sessionToken
     *            session of the admin user
     * @param userStudy
     *            the study of the target user
     * @param signUp
     *            sign up information for the target user
     * @param signUserIn
     *            sign user into Bridge web application in as part of the creation process
     * @param consentUser
     *            should the user be consented to the research?
     * @return UserSession for the newly created user
     * 
     * @throws BridgeServiceException
     */
    public UserSession createUser(String sessionToken, Study userStudy, SignUp signUp, boolean signUserIn,
            boolean consentUser) throws BridgeServiceException;
    
    /**
     * Remove all consent records from the target user. The user's session (if
     * the target user is signed in) will be updated to reflect this new state.
     * 
     * @param sessionToken
     *            session of the admin user
     * @param userStudy
     *            the study of the target user
     * @param userEmail
     *            the target user's email address
     * @return UserSession for user with updated consent state
     * 
     * @throws BridgeServiceException
     */
    public void revokeAllConsentRecords(String sessionToken, Study userStudy, String userEmail) throws BridgeServiceException;
    
    /**
     * Delete the target user.
     * 
     * @param sessionToken
     *            session of the admin user
     * @param userStudy
     *            the study of the target user
     * @param userEmail
     *            the target user's email address
     * @throws BridgeServiceException
     */
    public void deleteUser(String sessionToken, Study userStudy, String userEmail) throws BridgeServiceException;
    
}
