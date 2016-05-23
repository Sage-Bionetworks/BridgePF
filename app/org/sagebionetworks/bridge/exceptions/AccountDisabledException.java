package org.sagebionetworks.bridge.exceptions;

import org.apache.http.HttpStatus;

/**
 * Exception for when account is disabled for administrative reasons. This throws 423 LOCKED (WebDAV, not pure HTTP).
 */
@SuppressWarnings("serial")
@NoStackTraceException
public class AccountDisabledException extends BridgeServiceException {
    public AccountDisabledException() {
        super("Account disabled, please contact user support", HttpStatus.SC_LOCKED);
    }
}
