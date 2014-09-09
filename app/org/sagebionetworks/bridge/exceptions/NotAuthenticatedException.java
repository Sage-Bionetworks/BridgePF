package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

public class NotAuthenticatedException extends BridgeServiceException {
    private static final long serialVersionUID = 5095692878442402684L;

    public NotAuthenticatedException() {
        super("Not signed in.", HttpStatus.SC_UNAUTHORIZED);
    }

}
