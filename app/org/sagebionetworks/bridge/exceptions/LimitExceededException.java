package org.sagebionetworks.bridge.exceptions;

/**
 * Requests have exceeded an allowable limit, either in time or in number. The message should explain what 
 * is being gated (e.g. too many requests to sign in via email; too many users enrolled in the study, etc.).
 */
@NoStackTraceException
@SuppressWarnings("serial")
public class LimitExceededException extends BridgeServiceException {
    public LimitExceededException(String message) {
        super(message, 429);
    }
}
