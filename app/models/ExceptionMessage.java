package models;

public class ExceptionMessage extends JsonPayload<String> {

	/**
	 * When the user has not consented to research, they are not logged in, 
	 * but they are issued a session token to make the agreement. So that
	 * session is passed back as part of the exception JSON. Otherwise, this
	 * is not used.
	 */
	private String sessionToken;
	
	public ExceptionMessage(Throwable throwable) {
		super(throwable.getMessage());
		this.type = throwable.getClass().getName();
	}

	public ExceptionMessage(Throwable throwable, String message) {
		super(message);
		this.type = throwable.getClass().getName();
	}
	
	public ExceptionMessage(Throwable throwable, String message, String sessionToken) {
		super(message);
		this.type = throwable.getClass().getName();
		this.sessionToken = sessionToken;
	}
	
	public String getSessionToken() {
		return sessionToken;
	}

}
