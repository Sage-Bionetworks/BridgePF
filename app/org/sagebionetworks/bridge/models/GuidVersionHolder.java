package org.sagebionetworks.bridge.models;

public class GuidVersionHolder {

    private final String guid;
    private final Long version;
    
    public GuidVersionHolder(String guid, Long version) {
        this.guid = guid;
        this.version = version;
    }

    public String getGuid() {
        return guid;
    }

    public Long getVersion() {
        return version;
    }

}
