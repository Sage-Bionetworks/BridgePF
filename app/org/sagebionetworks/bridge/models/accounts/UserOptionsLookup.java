package org.sagebionetworks.bridge.models.accounts;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;

/**
 * A wrapper around the JSON object that contains a participant's options. 
 */
public class UserOptionsLookup {

    private final Map<String,String> options;
    
    public UserOptionsLookup(Map<String,String> options) {
        this.options = options;
    }
    
    public String getString(ParticipantOption option) {
        if (options == null) {
            return option.getDefaultValue();
        }
        String value = options.get(option.name());
        if (value == null) {
            return option.getDefaultValue();
        }
        return value;
    }

    public Set<String> getStringSet(ParticipantOption option) {
        String value = getString(option);
        return BridgeUtils.commaListToOrderedSet(value);
    }
    
    public LinkedHashSet<String> getOrderedStringSet(ParticipantOption option) {
        String value = getString(option);
        // We know this implementation returns a LinkedHashSet
        return (LinkedHashSet<String>)BridgeUtils.commaListToOrderedSet(value);
    }

    public boolean getBoolean(ParticipantOption option) {
        String value = getString(option);
        return Boolean.valueOf(value);
    }
    
    public <T extends Enum<T>> T getEnum(ParticipantOption option, Class<T> enumType) {
        String value = getString(option);
        return Enum.valueOf(enumType, value);
    }

    @Override
    public String toString() {
        return "UserOptionsLookup [options=" + options + "]";
    }
}
