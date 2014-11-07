package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.validators.Validate;

public class OptionLookup {
    
    private final Map<String, String> map = new HashMap<String, String>();
    private final String defaultValue;

    public OptionLookup(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    void put(String healthDataCode, String value) {
        checkNotNull(healthDataCode, Validate.CANNOT_BE_NULL, "healthDataCode");
        map.put(healthDataCode, value);
    }

    public String get(String healthDataCode) {
        if (map.containsKey(healthDataCode)) {
            return map.get(healthDataCode);
        }
        return defaultValue;
    }

}
