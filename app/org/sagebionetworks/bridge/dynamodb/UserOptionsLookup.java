package org.sagebionetworks.bridge.dynamodb;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;

public class UserOptionsLookup {

    // This is a map of ParticipantOption type to its serialized value.
    private final Map<String,String> options;
    
    public UserOptionsLookup(Map<String,String> options) {
        this.options = options;
    }
    
    public String getString(ParticipantOption option) {
        if (options == null) {
            return null;
        }
        String value = options.get(option.name());
        if (value == null) {
            return option.getDefaultValue();
        }
        return value;
    }
    
    public SharingScope getSharingScope() {
        String value = getString(ParticipantOption.SHARING_SCOPE);
        if (value == null) {
            return null;
        }
        return SharingScope.valueOf(value);
    }
    
    public Set<String> getDataGroups() {
        String value = getString(ParticipantOption.DATA_GROUPS);
        if (value == null) {
            return null;
        }
        return BridgeUtils.commaListToOrderedSet(value);
    }

    public LinkedHashSet<String> getLanguages() {
        String value = getString(ParticipantOption.LANGUAGES);
        if (value == null) {
            return null;
        }
        // We know this implementation returns a LinkedHashSet
        return (LinkedHashSet<String>)BridgeUtils.commaListToOrderedSet(value);
    }
    
    public Boolean getBoolean(ParticipantOption option) {
        String value = getString(option);
        if (value == null) {
            return false;
        }
        return Boolean.getBoolean(value);
    }
    
}
