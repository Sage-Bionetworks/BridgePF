package org.sagebionetworks.bridge.models;

public class IdVersionHolder {
    private final String id;
    private final Long version;
    
    public IdVersionHolder(String id, Long version) {
        this.id = id;
        this.version = version;
    }

    public String getId() {
        return id;
    }
    public Long getVersion() {
        return version;
    }
    public String getType() {
        return this.getClass().getSimpleName();
    }
}
