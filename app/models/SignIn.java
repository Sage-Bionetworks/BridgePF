package models;

import com.fasterxml.jackson.databind.JsonNode;

public final class SignIn {

	private final String username;
	private final String password;
	
	public SignIn(String username, String password) {
		this.username = username;
		this.password = password;
	}
	
	public static final SignIn fromJson(JsonNode node) {
		return new SignIn(node.get("username").asText(), node.get("password").asText());
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
}
