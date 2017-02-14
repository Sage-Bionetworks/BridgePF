package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VersionHolder {

    private final Long version;
    
    public VersionHolder(@JsonProperty("version") Long version) {
        this.version = version;
    }

    public Long getVersion() {
        return version;
    }
    
}
