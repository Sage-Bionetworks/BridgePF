package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.validators.Validate;

public class OptionLookup {
    
    private final Map<String, String> map = new HashMap<String, String>();
    private final String defaultValue;

    public OptionLookup(String defaultValue) {
        checkArgument(isNotBlank(defaultValue), Validate.CANNOT_BE_BLANK, "defaultValue");
        this.defaultValue = defaultValue;
    }

    void put(String healthCode, String value) {
        checkNotNull(healthCode, Validate.CANNOT_BE_NULL, "healthCode");
        if (isNotBlank(value)) {
            map.put(healthCode, value);
        }
    }

    public String get(String healthCode) {
        String value = map.get(healthCode);
        return (value == null) ? defaultValue : value;
    }
    
    public SharingScope getSharingScope(String healthCode) {
        return SharingScope.valueOf(get(healthCode));
    }

}
