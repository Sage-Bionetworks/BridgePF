package org.sagebionetworks.bridge.models;

public final class HealthIdImpl implements HealthId {

    private final String id;
    private final String code;
    
    public HealthIdImpl(String id, String code) {
        this.id = id;
        this.code = code;
    }
    
    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getCode() {
        return code;
    }

}
