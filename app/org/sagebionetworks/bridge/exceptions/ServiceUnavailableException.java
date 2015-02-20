package org.sagebionetworks.bridge.exceptions;

@SuppressWarnings("serial")
public class ServiceUnavailableException extends BridgeServiceException {

    public ServiceUnavailableException(Exception e) {
        super(e, 503);
    }
    
}
