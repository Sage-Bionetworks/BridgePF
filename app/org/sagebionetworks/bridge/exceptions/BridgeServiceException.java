package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class BridgeServiceException extends RuntimeException {
    
    private final int statusCode;
    
    public BridgeServiceException(String message) {
        super(message);
        this.statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
    }
    
    public BridgeServiceException(Throwable throwable) {
        this(throwable, HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    public BridgeServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
    }

    public BridgeServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
    
    protected BridgeServiceException(Throwable throwable, int statusCode) {
        super(throwable);
        this.statusCode = statusCode;
    }
    
    public int getStatusCode() {
        return statusCode;
    }

}
