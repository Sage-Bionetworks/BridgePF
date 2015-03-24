package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

@NoStackTraceException
public class BadRequestException extends BridgeServiceException {
    private static final long serialVersionUID = -3629409697634918499L;

    public BadRequestException(Throwable throwable) {
        super(throwable, HttpStatus.SC_BAD_REQUEST);
    }
    
    public BadRequestException(String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
    }
    
}
