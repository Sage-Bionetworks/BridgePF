package org.sagebionetworks.bridge.models.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Normally we would only return the version for an object that was identified
 * by a user-supplied string (an identifier). But at least survey responses allow
 * that value to be optional, and in that case the server fills it in with a value
 * that must be returned to the user in order to identify that survey response in 
 * the future (we generate a GUID if the identifier is not supplied).
 *
 */
public class IdentifierHolder {

    private final String identifier;

    @JsonCreator
    public IdentifierHolder(@JsonProperty("identifier") String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
    
}
