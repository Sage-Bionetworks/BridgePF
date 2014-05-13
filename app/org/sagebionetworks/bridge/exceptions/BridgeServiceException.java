package org.sagebionetworks.bridge.exceptions;

public class BridgeServiceException extends RuntimeException {
    private static final long serialVersionUID = -4425097573703184608L;
    
    private int statusCode;
    
    public BridgeServiceException(int statusCode) {
        this.statusCode = statusCode;
    }

    public BridgeServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public BridgeServiceException(Throwable cause, int statusCode) {
        super(cause);
        this.statusCode = statusCode;
    }

    public BridgeServiceException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public BridgeServiceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, int statusCode) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() {
        return statusCode;
    }

}
