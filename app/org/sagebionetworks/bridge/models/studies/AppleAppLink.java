package org.sagebionetworks.bridge.models.studies;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AppleAppLink {
    private final String appId;
    private final List<String> paths;
    
    @JsonCreator
    public AppleAppLink(@JsonProperty("appID") String appId, @JsonProperty("paths") List<String> paths) {
        this.appId = appId;
        this.paths = paths;
    }
    // This is not our normal naming convention, but this is the property as Apple wants to see it
    // in the final JSON. Likely to be confusing if someone uses the REST API directly.
    @JsonProperty("appID")
    public String getAppId() {
        return appId;
    }
    public List<String> getPaths() {
        return paths;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(appId, paths);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AppleAppLink other = (AppleAppLink) obj;
        return Objects.equals(appId, other.appId) && Objects.equals(paths, other.paths);
    }
    
}
