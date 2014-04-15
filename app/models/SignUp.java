package models;

import com.fasterxml.jackson.databind.JsonNode;

public class SignUp {

	private static final String EMAIL_FIELD = "email";
	private static final String USERNAME_FIELD = "username";
	
	private final String username;
	private final String email;
	
	public SignUp(String username, String email) {
		this.username = username;
		this.email = email;
	}
	
	public static SignUp fromJson(JsonNode node) {
		String username = null;
		String email = null;
		if (node != null && node.get(USERNAME_FIELD) != null) {
			username = node.get(USERNAME_FIELD).asText();
		}
		if (node != null && node.get(EMAIL_FIELD) != null) {
			email = node.get(EMAIL_FIELD).asText();
		}
		return new SignUp(username, email);
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}

}
