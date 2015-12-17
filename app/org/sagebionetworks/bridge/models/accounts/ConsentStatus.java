package org.sagebionetworks.bridge.models.accounts;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import static com.google.common.base.Preconditions.checkNotNull;

@JsonDeserialize(builder=ConsentStatus.Builder.class)
public final class ConsentStatus {

    public static Map<SubpopulationGuid,ConsentStatus> toMap(ConsentStatus... statuses) {
        return ConsentStatus.toMap(Lists.newArrayList(statuses));
    }
    
    public static Map<SubpopulationGuid,ConsentStatus> toMap(Collection<ConsentStatus> statuses) {
        ImmutableMap.Builder<SubpopulationGuid, ConsentStatus> builder = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>();
        if (statuses != null) {
            for (ConsentStatus status : statuses) {
                builder.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
            }
        }
        return builder.build();
    }
    
    public static ConsentStatus forSubpopulation(Collection<ConsentStatus> statuses, SubpopulationGuid subpopGuid) {
        for (ConsentStatus status : statuses) {
            if (status.getSubpopulationGuid().equals(subpopGuid.getGuid())) {
                return status;
            }
            
        }
        return null;
    }

    /**
     * Has the user consented to all the required consents?
     * @param statuses
     * @return
     */
    public static boolean isUserConsented(Map<SubpopulationGuid,ConsentStatus> statuses) {
        checkNotNull(statuses);
        return !statuses.isEmpty() && statuses.values().stream().allMatch(status -> {
            return !status.isRequired() || status.isConsented();
        });
    }

    /**
     * Are all the required consents up-to-date?
     * @param statuses
     * @return
     */
    public static boolean isConsentCurrent(Map<SubpopulationGuid,ConsentStatus> statuses) {
        checkNotNull(statuses);
        return !statuses.isEmpty() && statuses.values().stream().allMatch(status -> {
            return !status.isRequired() || status.getSignedMostRecentConsent();   
        });
    }
    
    public static boolean hasOnlyOneSignedConsent(Map<SubpopulationGuid,ConsentStatus> statuses) {
        checkNotNull(statuses);
        int count = 0;
        for (ConsentStatus status : statuses.values()) {
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
    private final boolean signedMostRecentConsent;
    
    @JsonCreator
    ConsentStatus(@JsonProperty("name") String name, @JsonProperty("subpopulationGuid") String subpopulationGuid,
            @JsonProperty("required") boolean isRequired, @JsonProperty("consented") boolean isConsented, 
            @JsonProperty("signedMostRecentConsent") boolean signedMostRecentConsent) {
        this.name = checkNotNull(name);
        this.subpopulationGuid = checkNotNull(subpopulationGuid);
        this.required = isRequired;
        this.consented = isConsented;
        this.signedMostRecentConsent = signedMostRecentConsent;
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
    
    public boolean getSignedMostRecentConsent() {
        return signedMostRecentConsent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, subpopulationGuid, required, consented, signedMostRecentConsent);
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
                && Objects.equals(signedMostRecentConsent, other.signedMostRecentConsent);
    }

    @Override
    public String toString() {
        return "ConsentStatus [name="+name+", subpopulationGuid="+subpopulationGuid+", isRequired="+required+", isConsented="+ consented+", hasSignedMostRecentConsent="+signedMostRecentConsent+"]";
    }
    
    public static class Builder {
        private String name;
        private SubpopulationGuid subpopulationGuid;
        private boolean required;
        private boolean consented;
        private boolean signedMostRecentConsent;
        
        public Builder withConsentStatus(ConsentStatus status) {
            this.name = status.getName();
            this.subpopulationGuid = SubpopulationGuid.create(status.getSubpopulationGuid());
            this.required = status.isRequired();
            this.consented = status.isConsented();
            this.signedMostRecentConsent = status.getSignedMostRecentConsent();
            return this;
        }
        public Builder withName(String name) {
            this.name = name;
            return this;
        }
        public Builder withGuid(SubpopulationGuid subpopGuid) {
            this.subpopulationGuid = subpopGuid;
            return this;
        }
        @JsonProperty("subpopulationGuid")
        private Builder withGuid(String subpopulationGuid) {
            this.subpopulationGuid = SubpopulationGuid.create(subpopulationGuid);
            return this;
        }
        public Builder withRequired(boolean required) {
            this.required = required;
            return this;
        }
        public Builder withConsented(boolean consented) {
            this.consented = consented;
            return this;
        }
        public Builder withSignedMostRecentConsent(boolean signedMostRecentConsent) {
            this.signedMostRecentConsent = signedMostRecentConsent;
            return this;
        }
        public ConsentStatus build() {
            return new ConsentStatus(name, subpopulationGuid.getGuid(), required, consented, signedMostRecentConsent);
        }
    }
    
}
