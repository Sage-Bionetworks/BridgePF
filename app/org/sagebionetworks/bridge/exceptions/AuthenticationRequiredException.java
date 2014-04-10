package org.sagebionetworks.bridge.exceptions;

public class AuthenticationRequiredException extends Exception {
	private static final long serialVersionUID = -5467582911114757369L;

	public AuthenticationRequiredException() {
		 super("You must sign in to continue.");
	}

}
