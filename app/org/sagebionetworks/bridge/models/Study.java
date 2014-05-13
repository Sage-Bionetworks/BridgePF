package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;

public class Study {

    private List<String> hostnames;
    private Long id;
    private List<Tracker> trackers;
    
    public List<String> getHostnames() {
        return hostnames;
    }
    public void setHostnames(List<String> hostnames) {
        this.hostnames = hostnames;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
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
        throw new BridgeNotFoundException(String.format("Tracker %s not available for study '%s'", id.toString(), id));
    }
}
