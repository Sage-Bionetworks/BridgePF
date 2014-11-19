package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignature implements BridgeEntity {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";

    private String name;
    private String birthdate;

    public ConsentSignature(String name, String birthdate) {
        this.name = name;
        this.birthdate = birthdate;
    }
    
    public static final ConsentSignature fromJson(JsonNode node) {
        String name = JsonUtils.asText(node, NAME_FIELD);
        String birthdate = JsonUtils.asText(node, BIRTHDATE_FIELD);
        return new ConsentSignature(name, birthdate);
    }

    public String getName() {
        return name;
    }

    public String getBirthdate() {
        return birthdate;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((birthdate == null) ? 0 : birthdate.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConsentSignature other = (ConsentSignature) obj;
        if (birthdate == null) {
            if (other.birthdate != null)
                return false;
        } else if (!birthdate.equals(other.birthdate))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}
