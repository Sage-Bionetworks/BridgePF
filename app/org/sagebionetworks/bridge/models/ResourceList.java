package org.sagebionetworks.bridge.models;

import java.util.List;

public final class ResourceList<T> {
    
    private final List<T> items;
    
    public ResourceList(List<T> items) {
        this.items = items;
    }
    public List<T> getItems() {
        return items;
    }
    public int getTotal() {
        return items.size();
    }
}