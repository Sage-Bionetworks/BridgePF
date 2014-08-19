package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

import org.sagebionetworks.bridge.models.UserSession;

public class ConsentRequiredException extends BridgeServiceException {
    private static final long serialVersionUID = 3057825897435345541L;
    
    private final UserSession userSession;
    
    public ConsentRequiredException(UserSession userSession) {
        super("Consent is required before signing in", HttpStatus.SC_PRECONDITION_FAILED);
        this.userSession = userSession;
    }

    public UserSession getUserSession() {
        return userSession;
    }
    
}
