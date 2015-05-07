package org.sagebionetworks.bridge.models.accounts;

/**
 * Holds the one-way mapping from health ID to health code.
 */
public interface HealthId {

    /**
     * The unencrypted health ID which should be encrypted and then published to StormPath.
     */
    String getId();

    /**
     * The health code mapped to this ID.
     */
    String getCode();
}
