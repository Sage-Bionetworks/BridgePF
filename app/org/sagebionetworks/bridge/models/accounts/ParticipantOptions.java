package org.sagebionetworks.bridge.models.accounts;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Object to deserialize JSON update of participant options. Some or all of the 
 * values can be missing in the JSON and we will only update values that are non-null.
 */
public final class ParticipantOptions {

    private final @Nullable SharingScope sharingScope;
    private final @Nullable Boolean notifyByEmail;
    private final @Nullable String externalId;
    private final @Nullable Set<String> dataGroups;
    private final @Nullable LinkedHashSet<String> languages;

    @JsonCreator
    public ParticipantOptions(@JsonProperty("sharingScope") SharingScope sharingScope, 
            @JsonProperty("notifyByEmail") Boolean notifyByEmail, 
            @JsonProperty("externalId") String externalId, 
            @JsonProperty("dataGroups") Set<String> dataGroups, 
            @JsonProperty("languages") LinkedHashSet<String> languages) {
        this.sharingScope = sharingScope;
        this.notifyByEmail = notifyByEmail;
        this.externalId = externalId;
        this.dataGroups = dataGroups;
        this.languages = languages;
    }
    public SharingScope getSharingScope() {
        return sharingScope;
    }
    public Boolean getNotifyByEmail() {
        return notifyByEmail;
    }
    public String getExternalId() {
        return externalId;
    }
    public Set<String> getDataGroups() {
        return dataGroups;
    }
    public LinkedHashSet<String> getLanguages() {
        return languages;
    }
    /**
     * It would be rare, but if no updates are sent in the JSON for this object, this method
     * will return true.
     * @return
     */
    public boolean hasNoUpdates() {
        return (sharingScope == null && notifyByEmail == null && externalId == null && dataGroups == null
                && languages == null);
    }
}
