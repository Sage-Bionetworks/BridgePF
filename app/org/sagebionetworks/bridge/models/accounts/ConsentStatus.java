package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConsentStatus {

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
