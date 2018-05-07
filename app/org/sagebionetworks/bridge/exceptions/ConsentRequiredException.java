package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.models.accounts.UserSession;

@SuppressWarnings("serial")
@NoStackTraceException
public class ConsentRequiredException extends BridgeServiceException {
    
    private final UserSession userSession;
    
    public ConsentRequiredException(UserSession userSession) {
        // TODO: This is not correct. You sign in, and find out the next thing you need to do is consent.
        super("Consent is required before signing in.", HttpStatus.SC_PRECONDITION_FAILED);
        this.userSession = userSession;
    }

    public UserSession getUserSession() {
        return userSession;
    }
    
}
