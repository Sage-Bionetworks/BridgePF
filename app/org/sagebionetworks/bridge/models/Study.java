package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;

public class Study {

    private List<String> hostnames;
    private String name;
    private String key; 
    private List<Tracker> trackers;
    
    public List<String> getHostnames() {
        return hostnames;
    }
    public void setHostnames(List<String> hostnames) {
        this.hostnames = hostnames;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public List<Tracker> getTrackers() {
        return trackers;
    }
    public void setTrackers(List<Tracker> trackers) {
        this.trackers = trackers;
    }
    public Tracker getTrackerById(Long id) {
        for (Tracker tracker : trackers) {
            if (tracker.getId() == id) {
                return tracker;
            }
        }
        throw new BridgeNotFoundException(String.format("Tracker %s not available for study '%s'", id.toString(), key));
    }
}
