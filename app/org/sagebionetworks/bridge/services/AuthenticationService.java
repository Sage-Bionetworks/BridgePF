package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface AuthenticationService {

    /**
     * This method returns the cached session for the user. A ScheduleContext object is not provided to the method, 
     * and the user's consent status is not re-calculated based on participation in one more more subpopulations. 
     * This only happens when calling session-constructing service methods (signIn and verifyEmail, both of which 
     * return newly constructed sessions).
     * @param sessionToken
     * @return session
     *      the cached user session calculated on sign in or during verify email workflow
     */
    public UserSession getSession(String sessionToken);

    public UserSession signIn(Study study, ScheduleContext context, SignIn signIn) throws ConsentRequiredException, EntityNotFoundException;

    public void signOut(UserSession session);

    public void signUp(Study study, SignUp signUp, boolean isAnonSignUp);

    public UserSession verifyEmail(Study study, ScheduleContext context, EmailVerification verification) throws ConsentRequiredException;
    
    public void resendEmailVerification(StudyIdentifier studyIdentifier, Email email);

    public void requestResetPassword(Study study, Email email);

    public void resetPassword(PasswordReset passwordReset);
    
}
