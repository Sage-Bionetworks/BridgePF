package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=GuidCreatedOnVersionHolderImpl.class)
public interface GuidCreatedOnVersionHolder {
    public String getGuid();
    public long getCreatedOn();
    public Long getVersion();
    public boolean keysEqual(GuidCreatedOnVersionHolder keys);
}
