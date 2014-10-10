package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("Backfill")
public class Backfill implements BridgeEntity {

    public Backfill(String name) {
        this.name = name;
        count = 0;
        completed = false;
    }

    public String getName() {
        return name;
    }
    public int getCount() {
        return count;
    }
    public boolean isCompleted() {
        return completed;
    }
    public void setCount(int count) {
        this.count = count;
    }
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    private final String name;
    private volatile int count;
    private volatile boolean completed;
}
