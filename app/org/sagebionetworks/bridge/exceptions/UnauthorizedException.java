package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
@NoStackTraceException
public class UnauthorizedException extends BridgeServiceException {
    public UnauthorizedException() {
        this("Caller does not have permission to access this service.");
    }

    public UnauthorizedException(String message) {
        super(message, HttpStatus.SC_FORBIDDEN);
    }
}
