package org.sagebionetworks.bridge.models;

public class UserSession {

	private boolean authenticated;
    private boolean consent;
    private String environment;
    private String healthDataCode;
    private String sessionToken;
	private String stormpathHref;
	private String studyKey;
	private String username;

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
	public String getEnvironment() {
		return environment;
	}
	public void setEnvironment(String environment) {
		this.environment = environment;
	}
    public String getStormpathHref() {
        return stormpathHref;
    }
    public void setStormpathHref(String stormpathHref) {
        this.stormpathHref = stormpathHref;
    }
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }
    public boolean doesConsent() {
        return consent;
    }
    public void setConsent(boolean consent) {
        this.consent = consent;
    }
    public String getHealthDataCode() {
        return healthDataCode;
    }
    public void setHealthDataCode(String healthDataCode) {
        this.healthDataCode = healthDataCode;
    }
}
