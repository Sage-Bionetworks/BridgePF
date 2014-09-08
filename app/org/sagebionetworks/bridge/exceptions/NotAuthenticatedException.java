package org.sagebionetworks.bridge.exceptions;

public class NotAuthenticatedException extends BridgeServiceException {
    private static final long serialVersionUID = 5095692878442402684L;

    public NotAuthenticatedException() {
        super("Not signed in.", 401);
    }

}
