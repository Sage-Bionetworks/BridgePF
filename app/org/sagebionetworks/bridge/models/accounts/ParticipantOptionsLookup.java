package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;

/**
 * A wrapper around the JSON object that contains a participant's options. 
 */
public class ParticipantOptionsLookup {

    private final Map<String,String> options;
    
    public ParticipantOptionsLookup(Map<String,String> options) {
        checkNotNull(options);
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
        try {
            return Enum.valueOf(enumType, value);    
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "UserOptionsLookup [options=" + options + "]";
    }
}
