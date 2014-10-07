package org.sagebionetworks.bridge.models;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.springframework.core.io.Resource;

import com.google.common.collect.ImmutableList;

@BridgeTypeName("Study")
public class Study implements BridgeEntity {

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
    private final List<String> hostnames;
    private final List<Tracker> trackers;
    /**
     * The name of the role assigned to researchers for this study, who have 
     * permissions to engage in a wider range of activities vis-a-vis the API
     * than study participants.
     */
    private final String researcherRole;
    
    public Study(String name, String key, int minAge, String stormpathDirectoryHref, List<String> hostnames,
            List<Tracker> trackers, Resource consentAgreement, String researcherRole) {
        this.name = name;
        this.key = key; 
        this.minAge = minAge;
        this.stormpathDirectoryHref = stormpathDirectoryHref;
        this.consentAgreement = consentAgreement;
        this.hostnames = ImmutableList.copyOf(hostnames); // Collections.unmodifiableList(hostnames);
        this.trackers = ImmutableList.copyOf(trackers); // Collections.unmodifiableList(trackers);
        this.researcherRole = researcherRole;
    }
    
    public Study(Study study) {
        this(study.getName(), study.getKey(), study.getMinAge(), study.getStormpathDirectoryHref(), study.getHostnames(), 
            study.getTrackers(), study.getConsentAgreement(), study.getResearcherRole());
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
    public String getResearcherRole() {
        return researcherRole;
    }
    public Tracker getTrackerById(Long id) {
        for (Tracker tracker : trackers) {
            if (tracker.getId() == id) {
                return tracker;
            }
        }
        String message = String.format("Tracker %s not available for study '%s'", id.toString(), key);
        throw new EntityNotFoundException(Tracker.class, message);
    }
    @Override
    public String toString() {
        return "Study [name=" + name + ", key=" + key + ", minAge=" + minAge + ", stormpathDirectoryHref="
                + stormpathDirectoryHref + ", consentAgreement=" + consentAgreement + ", hostnames=" + hostnames
                + ", trackers=" + trackers + ", researcherRole=" + researcherRole + "]";
    }
}
