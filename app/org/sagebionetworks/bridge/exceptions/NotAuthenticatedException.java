package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

@SuppressWarnings("serial")
@NoStackTraceException
public class NotAuthenticatedException extends BridgeServiceException {

    public NotAuthenticatedException() {
        super("Not signed in.", HttpStatus.SC_UNAUTHORIZED);
    }

}
