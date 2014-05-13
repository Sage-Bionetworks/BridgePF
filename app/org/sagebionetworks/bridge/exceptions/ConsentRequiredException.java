package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

/**
 * This is similar to the Synapse TermsOfUseException, except that it 
 * refers to consent, and will carry through the session token that is 
 * needed to make the consent call (this is not in the TermsOfUseException).
 *
 */
public class ConsentRequiredException extends BridgeServiceException {
	private static final long serialVersionUID = 3057825897435345541L;
	
	private final String sessionToken;
	
	public ConsentRequiredException(String sessionToken) {
		super("Consent is required before signing in", HttpStatus.SC_PRECONDITION_FAILED);
		this.sessionToken = sessionToken;
	}

	public String getSessionToken() {
		return sessionToken;
	}
	
}
