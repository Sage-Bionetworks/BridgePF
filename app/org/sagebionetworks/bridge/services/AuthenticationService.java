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

    public UserSession getSession(String sessionToken);

    public UserSession signIn(Study study, ScheduleContext context, SignIn signIn) throws ConsentRequiredException, EntityNotFoundException;

    public void signOut(UserSession session);

    public void signUp(Study study, SignUp signUp, boolean isAnonSignUp);

    public UserSession verifyEmail(Study study, ScheduleContext context, EmailVerification verification) throws ConsentRequiredException;
    
    public void resendEmailVerification(StudyIdentifier studyIdentifier, Email email);

    public void requestResetPassword(Study study, Email email);

    public void resetPassword(PasswordReset passwordReset);
    
}
