package org.sagebionetworks.bridge.models;

/**
 * Holder for user id
 */
public class SynapseUserIdHolder {
    private String userId;

    public SynapseUserIdHolder(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return this.userId;
    }
}
