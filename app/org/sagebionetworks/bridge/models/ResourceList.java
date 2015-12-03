package org.sagebionetworks.bridge.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ResourceList<T> {
    
    private final List<T> items;

    @JsonCreator
    public ResourceList(@JsonProperty("items") List<T> items) {
        this.items = items;
    }
    public List<T> getItems() {
        return items;
    }
    public int getTotal() {
        return items.size();
    }
}