package org.sagebionetworks.bridge.models.appconfig;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AppleAppLink {
    private final String appId;
    private final List<String> paths;
    
    @JsonCreator
    public AppleAppLink(@JsonProperty("appID") String appId, @JsonProperty("paths") List<String> paths) {
        this.appId = appId;
        this.paths = paths;
    }
    @JsonProperty("appID")
    public String getAppId() {
        return appId;
    }
    public List<String> getPaths() {
        return paths;
    }
}
