package org.sagebionetworks.bridge.models;

public class VersionHolder {

    private final Long version;
    
    public VersionHolder(Long version) {
        this.version = version;
    }

    public Long getVersion() {
        return version;
    }
    
}
