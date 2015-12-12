package org.sagebionetworks.bridge.models.accounts;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConsentStatus {

    public static ConsentStatus forSubpopulation(List<ConsentStatus> statuses, SubpopulationGuid subpopGuid) {
        for (int i=0; i < statuses.size(); i++) {
            if (statuses.get(i).getSubpopulationGuid().equals(subpopGuid.getGuid())) {
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
    
    public static boolean hasOnlyOneSignedConsent(List<ConsentStatus> statuses) {
        int count = 0;
        for (ConsentStatus status : statuses) {
            if (status.isConsented()) {
                count ++;
            }
        }
        return !statuses.isEmpty() && count == 1;
    }
    
    private final String name;
    private final String subpopulationGuid;
    private final boolean required;
    private final boolean consented;
    private final boolean mostRecentConsent;
    
    @JsonCreator
    public ConsentStatus(@JsonProperty("name") String name, @JsonProperty("subpopulationGuid") String subpopulationGuid,
            @JsonProperty("required") boolean isRequired, @JsonProperty("consented") boolean isConsented, 
            @JsonProperty("mostRecentConsent") boolean isMostRecentConsent) {
        this.name = checkNotNull(name);
        this.subpopulationGuid = checkNotNull(subpopulationGuid);
        this.required = isRequired;
        this.consented = isConsented;
        this.mostRecentConsent = isMostRecentConsent;
    }
    
    public String getName() {
        return name;
    }

    public String getSubpopulationGuid() {
        return subpopulationGuid;
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
        return Objects.hash(name, subpopulationGuid, required, consented, mostRecentConsent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ConsentStatus other = (ConsentStatus) obj;
        return Objects.equals(name, other.name) && Objects.equals(subpopulationGuid, other.subpopulationGuid)
                && Objects.equals(required, other.required) && Objects.equals(consented, other.consented)
                && Objects.equals(mostRecentConsent, other.mostRecentConsent);
    }

    @Override
    public String toString() {
        return "ConsentStatus [name="+name+", subpopulationGuid="+subpopulationGuid+", isRequired="+required+", isConsented="+ consented+", isMostRecentConsent="+mostRecentConsent+"]";
    }
    
}
