package org.sagebionetworks.bridge.models;

import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeNotFoundException;
import org.springframework.core.io.Resource;

public class Study {

    private final String name;
    private final String key;
    private final int minAge;
    /**
     * There is a separate StormPath directory for each study (all associated to
     * the production application). This allows us to tailor the email for each
     * study, linking back to the correct host name for that study.
     */
    private final String stormpathDirectoryHref;
    private final Resource consentAgreement;
    private List<String> hostnames = Collections.emptyList();
    private List<Tracker> trackers = Collections.emptyList();
    
    public Study(String name, String key, int minAge, String stormpathDirectoryHref, List<String> hostnames,
            List<Tracker> trackers, Resource consentAgreement) {
        this.name = name;
        this.key = key; 
        this.minAge = minAge;
        this.stormpathDirectoryHref = stormpathDirectoryHref;
        this.consentAgreement = consentAgreement;
        if (hostnames != null) {
            this.hostnames = Collections.unmodifiableList(hostnames);
        }
        if (trackers != null) {
            this.trackers = Collections.unmodifiableList(trackers);
        }
    }
    
    public Study(Study study) {
        this(study.getName(), study.getKey(), study.getMinAge(), study.getStormpathDirectoryHref(), study.getHostnames(), 
            study.getTrackers(), study.getConsentAgreement());
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
    public int getMinAge() {
        return minAge;
    }
    public String getStormpathDirectoryHref() {
        return stormpathDirectoryHref;
    }
    public List<Tracker> getTrackers() {
        return trackers;
    }
    public Resource getConsentAgreement() {
        return consentAgreement;
    }
    public Tracker getTrackerById(Long id) {
        for (Tracker tracker : trackers) {
            if (tracker.getId() == id) {
                return tracker;
            }
        }
        throw new BridgeNotFoundException(String.format("Tracker %s not available for study '%s'", id.toString(), key));
    }
    public String getType() {
        return this.getClass().getSimpleName();
    }
}
