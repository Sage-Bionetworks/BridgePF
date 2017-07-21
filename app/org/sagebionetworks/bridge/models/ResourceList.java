package org.sagebionetworks.bridge.models;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

/**
 * Basic array of items, not paged (these are all there are given the requested parameters), 
 * with any parameters that were sent to the server to produce the list.
 */
public class ResourceList<T> {
    
    protected static final String ITEMS = "items";
    
    private final List<T> items;
    private Map<String,Object> requestParams = new HashMap<>();

    @JsonCreator
    public ResourceList(@JsonProperty(ITEMS) List<T> items) {
        this.items = items;
    }
    public List<T> getItems() {
        return items;
    }
    public Map<String, Object> getRequestParams() {
        return ImmutableMap.copyOf(requestParams);
    }
    public ResourceList<T> withRequestParam(String key, Object value) {
        if (isNotBlank(key) && value != null) {
            // For DateTime, forcing toString() here rather than using Jackson's 
            // serialization mechanism, ensures the string is in the supplied timezone
            requestParams.put(key, value.toString());    
        }
        return this;
    }
}