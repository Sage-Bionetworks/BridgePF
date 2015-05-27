package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.models.accounts.UserSession;

@SuppressWarnings("serial")
@NoStackTraceException
public class ConsentRequiredException extends BridgeServiceException {
    
    private final UserSession userSession;
    
    public ConsentRequiredException(UserSession userSession) {
        super("Consent is required before signing in.", HttpStatus.SC_PRECONDITION_FAILED);
        this.userSession = userSession;
    }

    public UserSession getUserSession() {
        return userSession;
    }
    
}
