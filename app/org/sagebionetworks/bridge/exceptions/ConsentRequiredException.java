package org.sagebionetworks.bridge.exceptions;

import org.apache.commons.httpclient.HttpStatus;

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
