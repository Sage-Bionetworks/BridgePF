package org.sagebionetworks.bridge.exceptions;

@SuppressWarnings("serial")
public class ServiceUnavailableException extends BridgeServiceException {

    public ServiceUnavailableException(String message) {
        super(message, 503);
    }
    
    public ServiceUnavailableException(Exception e) {
        super(e, 503);
    }
    
}
