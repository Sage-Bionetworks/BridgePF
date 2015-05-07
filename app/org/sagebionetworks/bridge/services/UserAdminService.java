package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;

public interface UserAdminService {

    /**
     * Create a user, sign that user is, and optionally consent that user to research. The method is idempotent: no
     * error occurs if the user exists, or is already signed in, or has already consented.
     *
     * @param signUp
     *            sign up information for the target user
     * @param userStudy
     *            the study of the target user
     * @param signUserIn
     *            sign user into Bridge web application in as part of the creation process
     * @param consentUser
     *            should the user be consented to the research?
     * @return UserSession for the newly created user
     *
     * @throws BridgeServiceException
     */
    public UserSession createUser(SignUp signUp, Study userStudy, boolean signUserIn, boolean consentUser);

    /**
     * Delete the target user.
     *
     * @param user
     *            target user
     * @throws BridgeServiceException
     */
    public void deleteUser(Study study, String email);
    
    /**
     * Deletes all all users in the "test users" group in Stormpath.
     *
     * @throws BridgeServiceException
     */
    public void deleteAllUsers(String role) throws BridgeServiceException;

}
