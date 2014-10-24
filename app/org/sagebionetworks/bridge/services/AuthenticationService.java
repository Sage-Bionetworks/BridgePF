package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;

public interface AuthenticationService {

    public UserSession getSession(String sessionToken);

    public UserSession signIn(Study study, SignIn signIn) throws ConsentRequiredException, EntityNotFoundException;

    public void signOut(String sessionToken);

    public void signUp(SignUp signUp, Study study, boolean sendEmail);

    public UserSession verifyEmail(Study study, EmailVerification verification) throws ConsentRequiredException;

    public void requestResetPassword(Email email);

    public void resetPassword(PasswordReset passwordReset);
    
    public User getUser(Study study, String email);
    
}
