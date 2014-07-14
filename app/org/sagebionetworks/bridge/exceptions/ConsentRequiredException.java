package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;
import org.sagebionetworks.bridge.models.UserSessionInfo;

public class ConsentRequiredException extends BridgeServiceException {
    private static final long serialVersionUID = 3057825897435345541L;
    
    private final UserSessionInfo userSession;
    
    public ConsentRequiredException(UserSessionInfo userSession) {
        super("Consent is required before signing in", HttpStatus.SC_PRECONDITION_FAILED);
        this.userSession = userSession;
    }

    public UserSessionInfo getUserSession() {
        return userSession;
    }
    
}
