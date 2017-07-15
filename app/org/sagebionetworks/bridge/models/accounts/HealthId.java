package org.sagebionetworks.bridge.models.accounts;

/**
 * Holds the one-way mapping from health ID to health code.
 */
// Note: This is being deprecated, as the one-way mapping was found to be only not useful, but also detrimental to some
// of the things external partners are asking for.
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
