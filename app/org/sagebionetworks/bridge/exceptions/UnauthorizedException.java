package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

public class UnauthorizedException extends BridgeServiceException {
    private static final long serialVersionUID = 6356584260615251518L;

    public UnauthorizedException() {
        super("Caller does not have permission to access this service.", HttpStatus.SC_FORBIDDEN);
    }
}
