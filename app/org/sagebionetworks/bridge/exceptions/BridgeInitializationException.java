package org.sagebionetworks.bridge.exceptions;

/**
 * This exception is thrown when Bridge fails to initialize properly. It exists primarily to facilitate testing (to
 * validate BridgeInitializationException vs any other RuntimeException that could occur for a variety of other
 * reasons).
 */
@SuppressWarnings("serial")
public class BridgeInitializationException extends RuntimeException {
    /** Basic constructor with error message. */
    public BridgeInitializationException(String message) {
        super(message);
    }
}
