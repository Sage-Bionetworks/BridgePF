package models;

import com.fasterxml.jackson.databind.JsonNode;

public class SignUp {

	private static final String EMAIL_FIELD = "email";
	private static final String USERNAME_FIELD = "username";
	private static final String PASSWORD_FIELD = "password";
	
	private final String username;
	private final String email;
	private final String password;
	
	public SignUp(String username, String email, String password) {
		this.username = username;
		this.email = email;
		this.password = password;
	}
	
	public static SignUp fromJson(JsonNode node) {
		String username = null;
		String email = null;
		String password = null;
		if (node != null && node.get(USERNAME_FIELD) != null) {
			username = node.get(USERNAME_FIELD).asText();
		}
		if (node != null && node.get(EMAIL_FIELD) != null) {
			email = node.get(EMAIL_FIELD).asText();
		}
        if (node != null && node.get(PASSWORD_FIELD) != null) {
            password = node.get(PASSWORD_FIELD).asText();
        }
		return new SignUp(username, email, password);
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}
	
	public String getPassword() {
	    return password;
	}

}
