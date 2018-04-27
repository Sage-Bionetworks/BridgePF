package org.sagebionetworks.bridge.models.accounts;

public final class GeneratedPassword {
    private final String externalId;
    private final String userId;
    private final String password;

    public GeneratedPassword(String externalId, String userId, String password) {
        this.externalId = externalId;
        this.userId = userId;
        this.password = password;
    }
    public String getExternalId() {
        return externalId;
    }
    public String getUserId() {
        return userId;
    }
    public String getPassword() {
        return password;
    }
}
