package org.sagebionetworks.bridge.models.accounts;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.models.studies.Subpopulation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConsentStatus {

    public static ConsentStatus forSubpopulation(List<ConsentStatus> statuses, Subpopulation subpop) {
        for (int i=0; i < statuses.size(); i++) {
            if (statuses.get(i).getGuid().equals(subpop.getGuid())) {
                return statuses.get(i);
            }
        }
        return null;
    }

    public static boolean isUserConsented(List<ConsentStatus> statuses) {
        return !statuses.isEmpty() && statuses.stream().allMatch(status -> {
            return !status.isRequired() || status.isConsented();
        });
    }

    /**
     * Are all the required consents up-to-date?
     * @return
     */
    public static boolean isConsentCurrent(List<ConsentStatus> statuses) {
        return !statuses.isEmpty() && statuses.stream().allMatch(status -> {
            return !status.isRequired() || status.isMostRecentConsent();   
        });
    }
    
    private final String name;
    private final String guid;
    private final boolean required;
    private final boolean consented;
    private final boolean mostRecentConsent;
    
    @JsonCreator
    public ConsentStatus(@JsonProperty("name") String name, @JsonProperty("guid") String guid,
            @JsonProperty("required") boolean isRequired, @JsonProperty("consented") boolean isConsented, @JsonProperty("mostRecentConsent") boolean isMostRecentConsent) {
        this.name = checkNotNull(name);
        this.guid = checkNotNull(guid);
        this.required = isRequired;
        this.consented = isConsented;
        this.mostRecentConsent = isMostRecentConsent;
    }
    
    public String getName() {
        return name;
    }

    public String getGuid() {
        return guid;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isConsented() {
        return consented;
    }
    
    public boolean isMostRecentConsent() {
        return mostRecentConsent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, guid, required, consented, mostRecentConsent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ConsentStatus other = (ConsentStatus) obj;
        return Objects.equals(name, other.name) && Objects.equals(guid, other.guid)
                && Objects.equals(required, other.required) && Objects.equals(consented, other.consented) && Objects.equals(mostRecentConsent, other.mostRecentConsent);
    }

    @Override
    public String toString() {
        return "ConsentStatus [name="+name+", guid="+guid+", isRequired="+required+", isConsented="+ consented+", isMostRecentConsent="+mostRecentConsent+"]";
    }
    
}
