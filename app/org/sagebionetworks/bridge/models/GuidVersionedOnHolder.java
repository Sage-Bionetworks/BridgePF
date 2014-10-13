package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class GuidVersionedOnHolder {

    private final String guid;
    private final long versionedOn;
    
    public GuidVersionedOnHolder(String guid, long versionedOn) {
        this.guid = guid;
        this.versionedOn = versionedOn;
    }

    public String getGuid() {
        return guid;
    }

    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getVersionedOn() {
        return versionedOn;
    }
    
}
