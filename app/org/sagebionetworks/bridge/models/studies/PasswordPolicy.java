package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The policies that govern password creation. New studies will be created with the default password 
 * policy, which requires 8 characters, one numeric, one symbol and one upper- and lower-case 
 * character. However, this policy can be adjusted for legacy studies which were created with much 
 * more lax constraints.
 */
public final class PasswordPolicy {
    
    public static final int FIXED_MAX_LENGTH = 999;
    public static final int VALUE_OFF = 0;
    public static final int VALUE_ON = 1;
    
    /**
     * The password policy that is initially created for a study.
     */
    public static final PasswordPolicy DEFAULT_PASSWORD_POLICY = new PasswordPolicy(8, true, true, true, true);
    
    private final int minLength;
    private final boolean numericRequired;
    private final boolean symbolRequired;
    private final boolean lowerCaseRequired;
    private final boolean upperCaseRequired;
    
    @JsonCreator
    public PasswordPolicy(@JsonProperty("minLength") int minLength,
                    @JsonProperty("numericRequired") boolean numericRequired,
                    @JsonProperty("symbolRequired") boolean symbolRequired,
                    @JsonProperty("lowerCaseRequired") boolean lowerCaseRequired,
                    @JsonProperty("upperCaseRequired") boolean upperCaseRequired) {
        this.minLength = minLength;
        this.numericRequired = numericRequired;
        this.symbolRequired = symbolRequired;
        this.lowerCaseRequired = lowerCaseRequired;
        this.upperCaseRequired = upperCaseRequired;
    }
    
    public int getMinLength() {
        return minLength;
    }
    public boolean isNumericRequired() {
        return numericRequired;
    }
    public boolean isSymbolRequired() {
        return symbolRequired;
    }
    public boolean isLowerCaseRequired() {
        return lowerCaseRequired;
    }
    public boolean isUpperCaseRequired() {
        return upperCaseRequired;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(minLength);
        result = prime * result + Objects.hashCode(numericRequired);
        result = prime * result + Objects.hashCode(symbolRequired);
        result = prime * result + Objects.hashCode(lowerCaseRequired);
        result = prime * result + Objects.hashCode(upperCaseRequired);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        PasswordPolicy other = (PasswordPolicy) obj;
        return (Objects.equals(minLength, other.minLength) && 
                Objects.equals(numericRequired, other.numericRequired) &&
                Objects.equals(symbolRequired, other.symbolRequired) &&
                Objects.equals(lowerCaseRequired, other.lowerCaseRequired) &&
                Objects.equals(upperCaseRequired, other.upperCaseRequired));
    }

    @Override
    public String toString() {
        return String.format("PasswordPolicy [minLength=%s, numericRequired=%s, symbolRequired=%s, lowerCaseRequired=%s, upperCaseRequired=%s]",
            minLength, numericRequired, symbolRequired, lowerCaseRequired, upperCaseRequired);
    }
}
