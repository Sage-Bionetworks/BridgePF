package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

public class BridgeNotFoundException extends BridgeServiceException {
    private static final long serialVersionUID = 3560042590292172767L;
    
    public BridgeNotFoundException() {
        super(HttpStatus.SC_NOT_FOUND);
    }

    public BridgeNotFoundException(String message) {
        super(message, HttpStatus.SC_NOT_FOUND);
    }

    public BridgeNotFoundException(Throwable cause) {
        super(cause, HttpStatus.SC_NOT_FOUND);
    }

    public BridgeNotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SC_NOT_FOUND);
    }

    public BridgeNotFoundException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace, HttpStatus.SC_NOT_FOUND);
    }
}
