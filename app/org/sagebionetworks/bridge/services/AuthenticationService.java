package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;

public interface AuthenticationService {

    public UserSession getSession(String sessionToken);

    public UserSession signIn(Study study, SignIn signIn) throws ConsentRequiredException, BridgeNotFoundException,
            BridgeServiceException;

    public void signOut(String sessionToken);

    public void signUp(SignUp signUp, Study study) throws BridgeServiceException;

    public UserSession verifyEmail(Study study, EmailVerification verification) throws ConsentRequiredException,
            BridgeServiceException;

    public void requestResetPassword(Email email) throws BridgeServiceException;

    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException;
}
