package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=GuidCreatedOnVersionHolderImpl.class)
public interface GuidCreatedOnVersionHolder {
    String getGuid();
    long getCreatedOn();
    Long getVersion();
    boolean keysEqual(GuidCreatedOnVersionHolder keys);
}
