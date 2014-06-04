package org.sagebionetworks.bridge.models;

import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;

public class Study {

    private String name;
    private String key; 
    private List<String> hostnames = Collections.emptyList();
    private List<Tracker> trackers = Collections.emptyList();
    
    public Study(String name, String key, List<String> hostnames, List<Tracker> trackers) {
        this.name = name;
        this.key = key; 
        if (hostnames != null) {
            this.hostnames = Collections.unmodifiableList(hostnames);
        }
        if (trackers != null) {
            this.trackers = Collections.unmodifiableList(trackers);
        }
    }
    
    public Study(Study study) {
        this(study.getName(), study.getKey(), study.getHostnames(), study.getTrackers());
    }
    
    public List<String> getHostnames() {
        return hostnames;
    }
    public String getName() {
        return name;
    }
    public String getKey() {
        return key;
    }
    public List<Tracker> getTrackers() {
        return trackers;
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
