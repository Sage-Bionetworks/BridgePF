package org.sagebionetworks.bridge.models;

/**
 * Holds the one-way mapping from health ID to health code.
 */
public interface HealthId {

    /**
     * The health ID which is encrypted and then published to StormPath.
     */
    String getId();

    /**
     * The health code mapped to this ID.
     */
    String getCode();
}
