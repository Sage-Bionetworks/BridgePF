package models;

/**
 * We always need some information from the user's profile as well as a session token, 
 * when first signing in. We marshall that and return it in one request.
 *
 */
public class UserSession {

	private boolean authenticated;
	private String sessionToken;
	private String username;
	
	public UserSession() {
		this.sessionToken = "";
		this.username = "";
	}
	
	public String getSessionToken() {
		return sessionToken;
	}
	public void setSessionToken(String sessionToken) {
		this.sessionToken = sessionToken;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public boolean isAuthenticated() {
		return authenticated;
	}
	public void setAuthenticated(boolean authenticated) {
		this.authenticated = authenticated;
	}
}
