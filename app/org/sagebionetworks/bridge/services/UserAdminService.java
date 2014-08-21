package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;

public interface UserAdminService {

    /**
     * Create a user, sign that user is, and optionally consent that user to
     * research. The method is idempotent: no error occurs if the user exists,
     * or is already signed in, or has already consented.
     * 
     * @param caller
     *            Admin user making service call
     * @param signUp
     *            sign up information for the target user
     * @param userStudy
     *            the study of the target user
     * @param signUserIn
     *            sign user into Bridge web application in as part of the
     *            creation process
     * @param consentUser
     *            should the user be consented to the research?
     * @return UserSession for the newly created user
     * 
     * @throws BridgeServiceException
     */
    public UserSession createUser(User caller, SignUp signUp, Study userStudy, boolean signUserIn, boolean consentUser)
            throws BridgeServiceException;

    // TODO: Do not expose this. Instead do it when deleting a user.
    /**
     * Remove all consent records from the target user. The user's session (if
     * the target user is signed in) will be updated to reflect this new state.
     * 
     * @param caller
     *            Admin user making service call
     * @param user
     *            target user
     * @param userStudy
     *            the study of the target user
     * @return UserSession for user with updated consent state
     * 
     * @throws BridgeServiceException
     */
    public void revokeAllConsentRecords(User caller, User user, Study userStudy) throws BridgeServiceException;

    /**
     * Delete the target user.
     * 
     * @param caller
     *            Admin user making service call
     * @param user
     *            target user
     * @throws BridgeServiceException
     */
    public void deleteUser(User caller, User user) throws BridgeServiceException;

}
