package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

@SuppressWarnings("serial")
public class NotImplementedException extends BridgeServiceException {

    public NotImplementedException(String message) {
        super(message, HttpStatus.SC_NOT_IMPLEMENTED);
    }
    
    public NotImplementedException(Throwable throwable) {
        super(throwable, HttpStatus.SC_NOT_IMPLEMENTED);
    }
    
}
