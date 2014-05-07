package org.sagebionetworks.bridge.exceptions;

public class BridgeServiceException extends RuntimeException {
    private static final long serialVersionUID = -4425097573703184608L;

    public BridgeServiceException() {
    }

    public BridgeServiceException(String message) {
        super(message);
    }

    public BridgeServiceException(Throwable cause) {
        super(cause);
    }

    public BridgeServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public BridgeServiceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
