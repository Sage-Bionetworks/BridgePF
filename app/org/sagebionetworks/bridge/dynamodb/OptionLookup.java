package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.validators.Validate;

public class OptionLookup {
    
    private final Map<String, String> map = new HashMap<String, String>();
    private final String defaultValue;

    public OptionLookup(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    void put(String healthCode, String value) {
        checkNotNull(healthCode, Validate.CANNOT_BE_NULL, "healthCode");
        map.put(healthCode, value);
    }

    public String get(String healthCode) {
        if (map.containsKey(healthCode)) {
            return map.get(healthCode);
        }
        return defaultValue;
    }
    
    public ParticipantOption.ScopeOfSharing getScopeOfSharing(String healthCode) {
        if (map.containsKey(healthCode)) {
            return ParticipantOption.ScopeOfSharing.valueOf(map.get(healthCode));
        }
        return ParticipantOption.ScopeOfSharing.valueOf(defaultValue);
    }

}
