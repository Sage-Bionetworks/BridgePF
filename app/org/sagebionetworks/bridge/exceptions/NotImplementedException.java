package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

/**
 * This endpoint has not been implemented for this study. It has been disabled, or 
 * not completely configured. The caller cannot fix the call to succeed, but it is 
 * not an unexpected server error, it is intentional.
 */
@SuppressWarnings("serial")
public class NotImplementedException extends BridgeServiceException {

    public NotImplementedException(String message) {
        super(message, HttpStatus.SC_NOT_IMPLEMENTED);
    }
    
    public NotImplementedException(Throwable throwable) {
        super(throwable, HttpStatus.SC_NOT_IMPLEMENTED);
    }
    
}
