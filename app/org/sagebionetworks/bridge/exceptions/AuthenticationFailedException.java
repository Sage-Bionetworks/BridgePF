package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
@NoStackTraceException
public class AuthenticationFailedException extends BridgeServiceException {
    public AuthenticationFailedException() {
        super("Authentication failed.", HttpStatus.SC_UNAUTHORIZED);
    }
}
