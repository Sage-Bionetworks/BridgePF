package models;

import com.fasterxml.jackson.databind.JsonNode;

public class SignUp {

	private final String username;
	private final String email;
	
	public SignUp(String username, String email) {
		this.username = username;
		this.email = email;
	}
	
	public static SignUp fromJson(JsonNode node) {
		return new SignUp(node.get("username").asText(), node.get("email").asText());
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}

}
