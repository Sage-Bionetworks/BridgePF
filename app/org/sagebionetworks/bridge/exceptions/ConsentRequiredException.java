package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.client.exceptions.SynapseServerException;

/**
 * This is similar to the Synapse TermsOfUseException, except that it 
 * refers to consent, and will carry through the session token that is 
 * needed to make the consent call (this is not in the TermsOfUseException).
 *
 */
public class ConsentRequiredException extends SynapseServerException {
	private static final long serialVersionUID = 3057825897435345541L;
	
	private final String sessionToken;
	
	// 412 == "Precondition Failed"
	public ConsentRequiredException(String sessionToken) {
		super(412, "Consent is required before signing in");
		this.sessionToken = sessionToken;
	}

	public String getSessionToken() {
		return sessionToken;
	}
	
}
