package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
@NoStackTraceException
public class UnauthorizedException extends BridgeServiceException {

    public UnauthorizedException() {
        super("Caller does not have permission to access this service.", HttpStatus.SC_FORBIDDEN);
    }
}
