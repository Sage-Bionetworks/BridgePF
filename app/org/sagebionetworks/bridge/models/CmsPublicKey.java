package org.sagebionetworks.bridge.models;

/**
 * A wrapper to return the CMS public key in the PEM format to developers 
 * through the API.
 */
public final class CmsPublicKey {

    private final String publicKey;
    
    public CmsPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
    
    public String getPublicKey() {
        return publicKey;
    }
    
}
